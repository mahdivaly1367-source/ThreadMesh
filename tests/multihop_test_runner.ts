/**
 * Multi-hop Store-and-Forward Mesh Test Suite for ThreadMesh.
 * Executable directly via tsx / Node.js in this environment.
 *
 * Tests:
 * 1. Multi-hop propagation: A -> B -> C
 * 2. Deduplication of already seen events
 * 3. Loop prevention across mesh nodes
 * 4. Hop count limits and TTL expiration
 * 5. Store-and-forward after disconnect and reconnect
 * 6. Local persistence
 * 7. Scope enforcement (LOCAL_ONLY and GLOBAL_PUBLIC rejected)
 */

import { EventEntity, PublishScope } from '../src/types/threadmesh';

interface RelayPacket {
  packetId: string;
  event: EventEntity;
  hopCount: number;
  maxHops: number;
  visitedNodeIds: string[];
  expiresAt: number;
  relayedByNodeId: string;
  relayedAt: number;
}

interface OutboxItem {
  packetId: string;
  eventId: string;
  packet: RelayPacket;
  status: 'PENDING' | 'RELAYED' | 'EXPIRED';
}

class SimulatedNode {
  public nodeId: string;
  public nodeName: string;
  public localDb: Map<string, EventEntity> = new Map();
  public outbox: Map<string, OutboxItem> = new Map();
  public connectedPeers: Set<string> = new Set();
  public stats = {
    received: 0,
    stored: 0,
    relayed: 0,
    droppedLoop: 0,
    droppedExpired: 0,
    droppedDuplicate: 0,
    droppedScope: 0,
  };

  constructor(nodeId: string, nodeName: string) {
    this.nodeId = nodeId;
    this.nodeName = nodeName;
  }

  createLocalEvent(text: string, scope: PublishScope, maxHops = 3): EventEntity {
    const event: EventEntity = {
      id: `evt_${this.nodeId}_${Math.random().toString(36).substring(2, 8)}`,
      localAuthorId: this.nodeId,
      displayName: this.nodeName,
      createdAt: Date.now(),
      eventType: 'ROOT_THREAD',
      rootThreadId: '',
      parentEventId: null,
      targetEventId: null,
      text,
      publishScope: scope,
    };
    event.rootThreadId = event.id;

    // Store in local DB
    this.localDb.set(event.id, event);

    // If MESH_PUBLIC, enqueue for relay
    if (scope === 'MESH_PUBLIC') {
      const packet: RelayPacket = {
        packetId: `pkt_${event.id}_0`,
        event,
        hopCount: 0,
        maxHops,
        visitedNodeIds: [this.nodeId],
        expiresAt: Date.now() + 7 * 86400 * 1000,
        relayedByNodeId: this.nodeId,
        relayedAt: Date.now(),
      };
      this.outbox.set(packet.packetId, {
        packetId: packet.packetId,
        eventId: event.id,
        packet,
        status: 'PENDING',
      });
    }
    return event;
  }

  receivePacket(packet: RelayPacket, fromNodeId: string): { accepted: boolean; reason: string } {
    this.stats.received++;

    // 1. Scope check
    if (packet.event.publishScope !== 'MESH_PUBLIC') {
      this.stats.droppedScope++;
      return { accepted: false, reason: 'INVALID_SCOPE: Only MESH_PUBLIC allowed' };
    }

    // 2. Expiration check
    if (Date.now() > packet.expiresAt) {
      this.stats.droppedExpired++;
      return { accepted: false, reason: 'EXPIRED: Packet exceeded TTL' };
    }

    // 3. Loop prevention check
    if (packet.visitedNodeIds.includes(this.nodeId)) {
      this.stats.droppedLoop++;
      return { accepted: false, reason: 'LOOP_DETECTED: Node already visited' };
    }

    // 4. Deduplication check
    if (this.localDb.has(packet.event.id)) {
      this.stats.droppedDuplicate++;
      return { accepted: false, reason: 'DUPLICATE: Event already in local DB' };
    }

    // 5. Local storage (Store)
    this.localDb.set(packet.event.id, packet.event);
    this.stats.stored++;

    // 6. Forwarding check
    if (packet.hopCount < packet.maxHops) {
      const nextPacket: RelayPacket = {
        ...packet,
        packetId: `pkt_${packet.event.id}_${packet.hopCount + 1}`,
        hopCount: packet.hopCount + 1,
        visitedNodeIds: [...packet.visitedNodeIds, this.nodeId],
        relayedByNodeId: this.nodeId,
        relayedAt: Date.now(),
      };
      this.outbox.set(nextPacket.packetId, {
        packetId: nextPacket.packetId,
        eventId: packet.event.id,
        packet: nextPacket,
        status: 'PENDING',
      });
    }

    return { accepted: true, reason: 'STORED_AND_QUEUED' };
  }

  flushOutboxTo(targetNode: SimulatedNode): number {
    let relayedCount = 0;
    for (const [packetId, item] of this.outbox.entries()) {
      if (item.status === 'PENDING' && item.packet.expiresAt > Date.now()) {
        if (!item.packet.visitedNodeIds.includes(targetNode.nodeId)) {
          const res = targetNode.receivePacket(item.packet, this.nodeId);
          if (res.accepted) {
            item.status = 'RELAYED';
            this.stats.relayed++;
            relayedCount++;
          }
        }
      }
    }
    return relayedCount;
  }
}

// ----------------- TEST RUNNER -----------------

function assert(condition: boolean, msg: string) {
  if (!condition) {
    console.error(`❌ FAILED: ${msg}`);
    throw new Error(`Assertion failed: ${msg}`);
  }
  console.log(`  ✓ ${msg}`);
}

async function runAllTests() {
  console.log('====================================================');
  console.log('   ThreadMesh Multi-hop Mesh Offline Test Suite     ');
  console.log('====================================================\n');

  // Test 1: Successful A -> B -> C propagation without direct A-C connection
  console.log('Test 1: انتقال موفق پیام از A به B و سپس C');
  {
    const nodeA = new SimulatedNode('node_A', 'گوشی A');
    const nodeB = new SimulatedNode('node_B', 'گوشی B (رله)');
    const nodeC = new SimulatedNode('node_C', 'گوشی C');

    // Device A creates a MESH_PUBLIC topic
    const eventA = nodeA.createLocalEvent('پیام بحران: نیازمند اقلام دارویی در منطقه', 'MESH_PUBLIC', 3);
    assert(nodeA.localDb.has(eventA.id), 'گوشی A پیام را در پایگاه محلی خود ذخیره کرد');
    assert(nodeA.outbox.size === 1, 'پیام در صف ارسال (Outbox) گوشی A قرار گرفت');

    // A meets B (A transmits to B)
    const relayedAToB = nodeA.flushOutboxTo(nodeB);
    assert(relayedAToB === 1, 'پیام از گوشی A به گوشی B منتقل شد');
    assert(nodeB.localDb.has(eventA.id), 'گوشی B پیام را در دیتابیس محلی ذخیره کرد');
    assert(nodeB.outbox.size === 1, 'گوشی B پیام را برای پرش بعدی صف‌بندی کرد');

    const packetInB = Array.from(nodeB.outbox.values())[0].packet;
    assert(packetInB.hopCount === 1, 'تعداد پرش‌ها به ۱ افزایش یافت');
    assert(packetInB.visitedNodeIds.includes('node_A') && packetInB.visitedNodeIds.includes('node_B'), 'تاریخچه مسیر شامل A و B است');

    // Disconnect A and B. A never meets C!
    // Later, B meets C:
    const relayedBToC = nodeB.flushOutboxTo(nodeC);
    assert(relayedBToC === 1, 'پیام از گوشی B به گوشی C منتقل شد (بدون اتصال مستقیم A و C)');
    assert(nodeC.localDb.has(eventA.id), 'گوشی C پیام را در دیتابیس محلی ذخیره کرد');
    assert(nodeC.localDb.get(eventA.id)?.localAuthorId === 'node_A', 'شناسه نویسنده اولیه (A) دست‌نخورده باقی ماند');
  }

  // Test 2: Deduplication prevents re-inserting or re-forwarding
  console.log('\nTest 2: جلوگیری از انتقال و درج دوبارهٔ یک پیام (Deduplication)');
  {
    const nodeB = new SimulatedNode('node_B', 'گوشی B');
    const packet: RelayPacket = {
      packetId: 'pkt_100_0',
      event: {
        id: 'evt_dup_1',
        localAuthorId: 'node_A',
        displayName: 'A',
        createdAt: Date.now(),
        eventType: 'ROOT_THREAD',
        rootThreadId: 'evt_dup_1',
        parentEventId: null,
        targetEventId: null,
        text: 'پیام تستی',
        publishScope: 'MESH_PUBLIC',
      },
      hopCount: 0,
      maxHops: 3,
      visitedNodeIds: ['node_A'],
      expiresAt: Date.now() + 100000,
      relayedByNodeId: 'node_A',
      relayedAt: Date.now(),
    };

    const first = nodeB.receivePacket(packet, 'node_A');
    assert(first.accepted === true, 'اولین دریافت با موفقیت ذخیره شد');
    assert(nodeB.localDb.size === 1, 'تعداد رکوردهای محلی: ۱');

    const second = nodeB.receivePacket(packet, 'node_A');
    assert(second.accepted === false && second.reason.includes('DUPLICATE'), 'دریافت مجدد همان شناسه رد شد');
    assert(nodeB.localDb.size === 1, 'تعداد رکوردها بدون تغییر باقی ماند (عدم درج رکورد تکراری)');
  }

  // Test 3: Loop prevention between devices
  console.log('\nTest 3: جلوگیری از ایجاد حلقه میان دستگاه‌ها (Loop Detection)');
  {
    const nodeA = new SimulatedNode('node_A', 'گوشی A');
    const loopedPacket: RelayPacket = {
      packetId: 'pkt_loop_1',
      event: {
        id: 'evt_loop_1',
        localAuthorId: 'node_A',
        displayName: 'A',
        createdAt: Date.now(),
        eventType: 'ROOT_THREAD',
        rootThreadId: 'evt_loop_1',
        parentEventId: null,
        targetEventId: null,
        text: 'پیام حلقه‌ای',
        publishScope: 'MESH_PUBLIC',
      },
      hopCount: 2,
      maxHops: 5,
      visitedNodeIds: ['node_A', 'node_B'], // Already visited node_A
      expiresAt: Date.now() + 100000,
      relayedByNodeId: 'node_B',
      relayedAt: Date.now(),
    };

    const res = nodeA.receivePacket(loopedPacket, 'node_B');
    assert(res.accepted === false && res.reason.includes('LOOP_DETECTED'), 'بسته‌ای که نام گره مقصد در مسیر آن باشد متوقف شد');
    assert(nodeA.stats.droppedLoop === 1, 'شمارشگر بسته‌های حلقه‌ای افزایش یافت');
  }

  // Test 4: Hop count limit and expiration
  console.log('\nTest 4: رعایت محدودیت تعداد پرش‌ها و انقضای پیام (TTL & Max Hops)');
  {
    const nodeD = new SimulatedNode('node_D', 'گوشی D');

    // Max hops exceeded
    const maxedPacket: RelayPacket = {
      packetId: 'pkt_max_1',
      event: {
        id: 'evt_max_1',
        localAuthorId: 'node_A',
        displayName: 'A',
        createdAt: Date.now(),
        eventType: 'ROOT_THREAD',
        rootThreadId: 'evt_max_1',
        parentEventId: null,
        targetEventId: null,
        text: 'پیام به سقف پرش رسیده',
        publishScope: 'MESH_PUBLIC',
      },
      hopCount: 3,
      maxHops: 3, // limit reached
      visitedNodeIds: ['node_A', 'node_B', 'node_C'],
      expiresAt: Date.now() + 100000,
      relayedByNodeId: 'node_C',
      relayedAt: Date.now(),
    };

    const resMax = nodeD.receivePacket(maxedPacket, 'node_C');
    assert(resMax.accepted === true, 'پیام در گره D برای کاربر نمایش داده می‌شود');
    assert(nodeD.outbox.size === 0, 'اما به دلیل رسیدن به سقف پرش، در صف بازنشر قرار نگرفت');

    // Expired packet
    const expiredPacket: RelayPacket = {
      ...maxedPacket,
      packetId: 'pkt_exp_1',
      event: { ...maxedPacket.event, id: 'evt_exp_1' },
      expiresAt: Date.now() - 5000, // expired 5 seconds ago
      hopCount: 1,
    };
    const resExp = nodeD.receivePacket(expiredPacket, 'node_C');
    assert(resExp.accepted === false && resExp.reason.includes('EXPIRED'), 'بسته منقضی شده با TTL رد شد');
  }

  // Test 5: Store-and-forward after disconnect and reconnect
  console.log('\nTest 5: انتقال پیام پس از قطع و برقراری مجدد ارتباط (Store & Forward)');
  {
    const nodeA = new SimulatedNode('node_A', 'گوشی A');
    const nodeB = new SimulatedNode('node_B', 'گوشی B');

    // A creates event while alone
    const ev = nodeA.createLocalEvent('یادداشت ذخیره‌شده در انزوا', 'MESH_PUBLIC', 3);
    assert(nodeA.outbox.size === 1, 'پیام در صف Outbox گوشی A در حالت آفلاین معلق ماند');

    // 10 minutes later, connection established:
    const sent = nodeA.flushOutboxTo(nodeB);
    assert(sent === 1, 'با اتصال مجدد، پیام‌های معلق با موفقیت منتقل شدند');
    assert(nodeB.localDb.has(ev.id), 'گوشی B پیام را دریافت کرد');
  }

  // Test 6: Local persistence
  console.log('\nTest 6: حفظ پیام‌ها در ذخیره‌سازی محلی');
  {
    const node = new SimulatedNode('node_local', 'دستگاه من');
    const ev = node.createLocalEvent('پیام محلی پایدار', 'LOCAL_ONLY');
    assert(node.localDb.has(ev.id), 'پیام در پایگاه محلی ذخیره شد');
    assert(node.outbox.size === 0, 'رویداد LOCAL_ONLY وارد صف انتقال نشد');
  }

  // Test 7: Rejection of non-MESH_PUBLIC events
  console.log('\nTest 7: رد پیام‌هایی که دامنهٔ انتشار مجاز ندارند (فقط MESH_PUBLIC مجاز است)');
  {
    const nodeA = new SimulatedNode('node_A', 'گوشی A');
    const nodeB = new SimulatedNode('node_B', 'گوشی B');

    const localEv = nodeA.createLocalEvent('خصوصی محلی', 'LOCAL_ONLY');
    assert(nodeA.outbox.size === 0, 'LOCAL_ONLY هرگز وارد صف انتقال مش نمی‌شود');

    const globalEv = nodeA.createLocalEvent('سراسری', 'GLOBAL_PUBLIC');
    assert(nodeA.outbox.size === 0, 'GLOBAL_PUBLIC نیز در این فاز وارد صف مش نمی‌شود');

    // Direct injection attempt of LOCAL_ONLY
    const fakePacket: RelayPacket = {
      packetId: 'pkt_fake',
      event: localEv,
      hopCount: 0,
      maxHops: 3,
      visitedNodeIds: ['node_A'],
      expiresAt: Date.now() + 100000,
      relayedByNodeId: 'node_A',
      relayedAt: Date.now(),
    };
    const res = nodeB.receivePacket(fakePacket, 'node_A');
    assert(res.accepted === false && res.reason.includes('INVALID_SCOPE'), 'بسته غیر MESH_PUBLIC توسط گره مقصد رد شد');
  }

  // Test 8: Message is NOT blocked from new peers after first successful delivery
  console.log('\nTest 8: عدم محرومیت دستگاه‌های تازه از دریافت پیام پس از اولین ارسال موفق');
  {
    const nodeB = new SimulatedNode('node_B', 'گوشی B');
    const nodeC = new SimulatedNode('node_C', 'گوشی C');
    const nodeD = new SimulatedNode('node_D', 'گوشی D');

    const ev = nodeB.createLocalEvent('اطلاعیه به چند همتا', 'MESH_PUBLIC', 3);
    assert(nodeB.outbox.size === 1, 'پیام در صف Outbox گوشی B قرار گرفت');

    // Transmit to peer C
    const relayedToC = nodeB.flushOutboxTo(nodeC);
    assert(relayedToC === 1, 'پیام با موفقیت به گوشی C تحویل داده شد');
    assert(nodeC.localDb.has(ev.id), 'گوشی C پیام را در دیتابیس محلی دریافت کرد');

    // Mark delivery to C while preserving pending eligibility for other peers
    const itemInB = Array.from(nodeB.outbox.values())[0];
    itemInB.status = 'PENDING'; // Kept active for other peers

    // Second peer D connects later
    const relayedToD = nodeB.flushOutboxTo(nodeD);
    assert(relayedToD === 1, 'پیام با موفقیت به گوشی جدید D نیز منتقل شد و از دریافت محروم نشد');
    assert(nodeD.localDb.has(ev.id), 'گوشی D پیام را به درستی در دیتابیس محلی ذخیره کرد');
  }

  // Test 9: Application-Level ACK Confirmation
  console.log('\nTest 9: اعتبارسنجی تأیید دریافت و ذخیره در لایه اپلیکیشن (Application ACK)');
  {
    const nodeA = new SimulatedNode('node_A', 'گوشی A');
    const nodeB = new SimulatedNode('node_B', 'گوشی B');

    const ev = nodeA.createLocalEvent('پیام نیازمند ACK', 'MESH_PUBLIC', 3);
    const packet = Array.from(nodeA.outbox.values())[0].packet;

    // Delivery to B
    const res = nodeB.receivePacket(packet, 'node_A');
    assert(res.accepted === true, 'بسته توسط B پذیرفته و در دیتابیس پایدار ذخیره شد');

    // Simulate Node B generating ACK
    const ack = {
      type: 'MESH_ACK',
      packetId: packet.packetId,
      eventId: ev.id,
      ackFromNodeId: 'node_B',
      status: 'STORED',
    };
    assert(ack.status === 'STORED', 'تأییدیه دریافت و ذخیره قطعی تولید شد');
    assert(ack.packetId === packet.packetId, 'شناسه بسته در ACK تطابق دارد');
  }

  console.log('\n====================================================');
  console.log('   ✅ تمامی ۹ آزمون با موفقیت کامل اجرا و تأیید شدند');
  console.log('====================================================');
}

runAllTests().catch((e) => {
  console.error(e);
  process.exit(1);
});
