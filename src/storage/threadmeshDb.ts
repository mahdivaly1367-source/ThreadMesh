import { EventEntity, EventType, PublishScope, TreeNode, ThreadListItem, RecentChangeEventItem } from '../types/threadmesh';

const STORAGE_KEY_EVENTS = 'threadmesh_events_v1';
const STORAGE_KEY_SETTINGS = 'threadmesh_settings_v1';

export interface LocalSettings {
  localAuthorId: string;
  displayName: string;
}

export class ThreadMeshStorage {
  private static getStoredEvents(): EventEntity[] {
    try {
      const data = localStorage.getItem(STORAGE_KEY_EVENTS);
      return data ? JSON.parse(data) : [];
    } catch {
      return [];
    }
  }

  private static saveEvents(events: EventEntity[]): void {
    try {
      localStorage.setItem(STORAGE_KEY_EVENTS, JSON.stringify(events));
    } catch (e) {
      console.error('Failed to save to local storage', e);
    }
  }

  static getSettings(): LocalSettings {
    try {
      const data = localStorage.getItem(STORAGE_KEY_SETTINGS);
      if (data) {
        return JSON.parse(data);
      }
    } catch {
      // fallback
    }
    const defaultSettings: LocalSettings = {
      localAuthorId: 'node_' + Math.random().toString(36).substring(2, 10),
      displayName: 'کاربر محلی',
    };
    this.saveSettings(defaultSettings);
    return defaultSettings;
  }

  static saveSettings(settings: LocalSettings): void {
    try {
      localStorage.setItem(STORAGE_KEY_SETTINGS, JSON.stringify(settings));
    } catch (e) {
      console.error('Failed to save settings', e);
    }
  }

  static getAllEvents(): EventEntity[] {
    return this.getStoredEvents();
  }

  static getEventById(id: string): EventEntity | undefined {
    return this.getStoredEvents().find((e) => e.id === id);
  }

  static createRootTopic(
    text: string,
    scope: PublishScope,
    authorId: string,
    displayName: string
  ): { success: boolean; event?: EventEntity; error?: string } {
    const trimmed = text.trim();
    if (!trimmed) {
      return { success: false, error: 'متن موضوع نمی‌تواند خالی باشد.' };
    }
    if (trimmed.length > 4000) {
      return { success: false, error: 'متن نمی‌تواند بیش از ۴۰۰۰ نویسه باشد.' };
    }

    const eventId = 'evt_' + Math.random().toString(36).substring(2, 11) + '_' + Date.now();
    const now = Date.now();
    const newEvent: EventEntity = {
      id: eventId,
      localAuthorId: authorId,
      displayName,
      createdAt: now,
      eventType: 'ROOT_THREAD',
      rootThreadId: eventId,
      parentEventId: null,
      targetEventId: null,
      text: trimmed,
      publishScope: scope,
      lastViewedAt: now,
    };

    const events = this.getStoredEvents();
    // Unique ID check
    if (events.some((e) => e.id === eventId)) {
      return { success: false, error: 'شناسه تکراری است.' };
    }

    events.push(newEvent);
    this.saveEvents(events);
    return { success: true, event: newEvent };
  }

  static addReply(
    parentEventId: string,
    text: string,
    scope: PublishScope,
    authorId: string,
    displayName: string
  ): { success: boolean; event?: EventEntity; error?: string } {
    const trimmed = text.trim();
    if (!trimmed) {
      return { success: false, error: 'متن پاسخ نمی‌تواند خالی باشد.' };
    }
    if (trimmed.length > 4000) {
      return { success: false, error: 'طول پاسخ نمی‌تواند بیش از ۴۰۰۰ نویسه باشد.' };
    }

    const events = this.getStoredEvents();
    const parent = events.find((e) => e.id === parentEventId);
    if (!parent) {
      return { success: false, error: 'پست والد یافت نشد.' };
    }

    const eventId = 'evt_' + Math.random().toString(36).substring(2, 11) + '_' + Date.now();
    const now = Date.now();
    const newEvent: EventEntity = {
      id: eventId,
      localAuthorId: authorId,
      displayName,
      createdAt: now,
      eventType: 'REPLY',
      rootThreadId: parent.rootThreadId,
      parentEventId: parent.id,
      targetEventId: null,
      text: trimmed,
      publishScope: scope,
    };

    events.push(newEvent);
    this.saveEvents(events);
    return { success: true, event: newEvent };
  }

  static recordStanceChange(
    targetEventId: string,
    explanation: string,
    scope: PublishScope,
    authorId: string,
    displayName: string
  ): { success: boolean; event?: EventEntity; error?: string } {
    const trimmed = explanation.trim();
    if (!trimmed) {
      return { success: false, error: 'توضیح تغییر دیدگاه نمی‌تواند خالی باشد.' };
    }
    if (trimmed.length > 4000) {
      return { success: false, error: 'توضیح نمی‌تواند بیش از ۴۰۰۰ نویسه باشد.' };
    }

    const events = this.getStoredEvents();
    const target = events.find((e) => e.id === targetEventId);
    if (!target) {
      return { success: false, error: 'رویداد هدف جهت تغییر دیدگاه یافت نشد.' };
    }

    // Creates an immutable new event referencing the target event without modifying original!
    const eventId = 'sc_' + Math.random().toString(36).substring(2, 11) + '_' + Date.now();
    const now = Date.now();
    const newEvent: EventEntity = {
      id: eventId,
      localAuthorId: authorId,
      displayName,
      createdAt: now,
      eventType: 'STANCE_CHANGED',
      rootThreadId: target.rootThreadId,
      parentEventId: target.id,
      targetEventId: target.id,
      text: trimmed,
      publishScope: scope,
    };

    events.push(newEvent);
    this.saveEvents(events);
    return { success: true, event: newEvent };
  }

  static markThreadViewed(rootThreadId: string): void {
    const events = this.getStoredEvents();
    const root = events.find((e) => e.id === rootThreadId);
    if (root) {
      root.lastViewedAt = Date.now();
      this.saveEvents(events);
    }
  }

  static getThreadListItems(): ThreadListItem[] {
    const events = this.getStoredEvents();
    const roots = events.filter((e) => e.eventType === 'ROOT_THREAD');

    return roots.map((root) => {
      const threadEvents = events.filter((e) => e.rootThreadId === root.id);
      const replies = threadEvents.filter((e) => e.id !== root.id);
      const latest = threadEvents.reduce((prev, curr) => (curr.createdAt > prev.createdAt ? curr : prev), root);
      const lastViewed = root.lastViewedAt || 0;
      const hasUnseen = threadEvents.some((e) => e.createdAt > lastViewed && e.id !== root.id);

      return {
        rootEvent: root,
        replyCount: replies.length,
        lastActivityTime: latest.createdAt,
        lastPreviewText: latest.text.length > 120 ? latest.text.slice(0, 120) + '...' : latest.text,
        hasUnseenChanges: hasUnseen,
      };
    }).sort((a, b) => b.lastActivityTime - a.lastActivityTime);
  }

  static buildTree(rootThreadId: string): TreeNode | null {
    const events = this.getStoredEvents().filter((e) => e.rootThreadId === rootThreadId);
    const rootEvent = events.find((e) => e.id === rootThreadId);
    if (!rootEvent) return null;

    const nodeMap = new Map<string, TreeNode>();
    const stanceChanges: EventEntity[] = [];

    // Map non-stance events first
    for (const e of events) {
      if (e.eventType === 'STANCE_CHANGED') {
        stanceChanges.push(e);
      } else {
        nodeMap.set(e.id, {
          event: e,
          depth: 0,
          children: [],
          stanceChanges: [],
          isExpanded: true,
        });
      }
    }

    const rootNode = nodeMap.get(rootEvent.id);
    if (!rootNode) return null;

    // Attach children to parent
    for (const e of events) {
      if (e.id !== rootEvent.id && e.eventType !== 'STANCE_CHANGED') {
        const parentId = e.parentEventId;
        const parentNode = parentId ? nodeMap.get(parentId) : rootNode;
        const currentNode = nodeMap.get(e.id);
        if (currentNode && parentNode) {
          parentNode.children.push(currentNode);
        }
      }
    }

    // Attach stance changes to their target events
    for (const sc of stanceChanges) {
      const targetId = sc.targetEventId || sc.parentEventId;
      if (targetId && nodeMap.has(targetId)) {
        nodeMap.get(targetId)!.stanceChanges.push(sc);
      } else {
        rootNode.stanceChanges.push(sc);
      }
    }

    // Compute depths
    function calculateDepth(node: TreeNode, currentDepth: number) {
      node.depth = currentDepth;
      // Sort children by creation time ascending
      node.children.sort((a, b) => a.event.createdAt - b.event.createdAt);
      for (const child of node.children) {
        calculateDepth(child, currentDepth + 1);
      }
    }
    calculateDepth(rootNode, 0);

    return rootNode;
  }

  static getRecentChanges(): RecentChangeEventItem[] {
    const events = this.getStoredEvents();
    const roots = events.filter((e) => e.eventType === 'ROOT_THREAD');
    const rootMap = new Map(roots.map((r) => [r.id, r]));
    const eventMap = new Map(events.map((e) => [e.id, e]));

    const unseenEvents = events.filter((e) => {
      const root = rootMap.get(e.rootThreadId);
      const lastViewed = root?.lastViewedAt || 0;
      return e.createdAt > lastViewed && e.id !== root?.id;
    });

    return unseenEvents.map((e) => {
      const root = rootMap.get(e.rootThreadId);
      const parent = e.parentEventId ? eventMap.get(e.parentEventId) : null;
      return {
        event: e,
        rootEventTitle: root ? (root.text.length > 60 ? root.text.slice(0, 60) + '...' : root.text) : 'موضوع گفتگو',
        parentPreviewText: parent ? (parent.text.length > 50 ? parent.text.slice(0, 50) + '...' : parent.text) : undefined,
      };
    }).sort((a, b) => b.event.createdAt - a.event.createdAt);
  }

  /**
   * Seeds demo data illustrating 3 levels of nested replies and an immutable stance change
   */
  static seedDemoData(): void {
    const now = Date.now();
    const rootId = 'evt_demo_root';
    const l1Id = 'evt_demo_l1';
    const l2Id = 'evt_demo_l2';
    const l3Id = 'evt_demo_l3';
    const scId = 'evt_demo_sc';

    const events: EventEntity[] = [
      {
        id: rootId,
        localAuthorId: 'node_alpha',
        displayName: 'آرش (پژوهشگر معماری)',
        createdAt: now - 3600000 * 5,
        eventType: 'ROOT_THREAD',
        rootThreadId: rootId,
        parentEventId: null,
        targetEventId: null,
        text: 'آیا شبکه‌های اجتماعی غیرمتمرکز بدون سرور مرکزی و متکی بر مش محلی، در بحران‌های ارتباطی می‌توانند جایگزین پلتفرم‌های ابری شوند؟ نقطهٔ اصلی اتکا، امکان انتشار آفلاین و ثبت زنجیرهٔ استدلال‌هاست.',
        publishScope: 'LOCAL_ONLY',
        lastViewedAt: now - 3600000 * 2,
      },
      {
        id: l1Id,
        localAuthorId: 'node_beta',
        displayName: 'سارا (توسعه‌دهنده شبکه)',
        createdAt: now - 3600000 * 4,
        eventType: 'REPLY',
        rootThreadId: rootId,
        parentEventId: rootId,
        targetEventId: null,
        text: 'سطح اول: قطعاً امکان‌پذیر است، اما چالش اصلی توافق زمانی بر سر ترتیب رویدادها در حالت آفلاین است؛ زیرا ساعت سیستم دستگاه‌ها با هم تراز نیست.',
        publishScope: 'LOCAL_ONLY',
      },
      {
        id: l2Id,
        localAuthorId: 'node_gamma',
        displayName: 'کامران (تحلیل‌گر سیستم)',
        createdAt: now - 3600000 * 3,
        eventType: 'REPLY',
        rootThreadId: rootId,
        parentEventId: l1Id,
        targetEventId: null,
        text: 'سطح دوم: به همین دلیل گراف‌های علت و معلولی (Causal DAG) با ارجاع صریح به والد به کار می‌آیند. هر پاسخ به شناسهٔ رویداد قبلی گره می‌خورد، نه ساعت فیزیکی.',
        publishScope: 'LOCAL_ONLY',
      },
      {
        id: l3Id,
        localAuthorId: 'node_delta',
        displayName: 'نیلوفر',
        createdAt: now - 3600000 * 2,
        eventType: 'REPLY',
        rootThreadId: rootId,
        parentEventId: l2Id,
        targetEventId: null,
        text: 'سطح سوم: دقیقاً، ThreadMesh همین اصل را پیاده کرده است. هر پاسخ شاخهٔ مستقل خودش را تشکیل می‌دهد و کاربر دقیقاً می‌داند کدام استدلال پاسخ داده شده است.',
        publishScope: 'LOCAL_ONLY',
      },
      {
        id: scId,
        localAuthorId: 'node_beta',
        displayName: 'سارا (توسعه‌دهنده شبکه)',
        createdAt: now - 1800000,
        eventType: 'STANCE_CHANGED',
        rootThreadId: rootId,
        parentEventId: l1Id,
        targetEventId: l1Id,
        text: 'تغییر دیدگاه نسبت به پاسخ سطح اول: استدلال کامران در مورد گراف علّی قانع‌کننده بود. نیازی به ساعت هماهنگ جهانی برای حفظ پیوستگی استدلال‌ها نداریم و ساختار درختی پاسخ‌ها کفایت می‌کند.',
        publishScope: 'LOCAL_ONLY',
      },
    ];

    this.saveEvents(events);
  }

  /**
   * Imports an incoming event received over Nearby Connections transport.
   * Enforces:
   * 1. publishScope must be MESH_PUBLIC
   * 2. text <= 4000 and non-empty
   * 3. deduplication (skips if event.id already exists)
   */
  static importIncomingMeshEvent(event: EventEntity): { success: boolean; isNew?: boolean; error?: string } {
    if (event.publishScope !== 'MESH_PUBLIC') {
      return { success: false, error: 'رویداد رد شد؛ فقط رویدادهای MESH_PUBLIC اجازه عبور از مش دارند.' };
    }

    const trimmed = (event.text || '').trim();
    if (!trimmed) {
      return { success: false, error: 'متن رویداد دریافتی نمی‌تواند خالی باشد.' };
    }
    if (trimmed.length > 4000) {
      return { success: false, error: 'طول متن بیش از ۴۰۰۰ کاراکتر است.' };
    }

    const events = this.getStoredEvents();
    if (events.some((e) => e.id === event.id)) {
      // Duplicate event already in local DB, ignored without error
      return { success: true, isNew: false };
    }

    const eventToStore: EventEntity = {
      ...event,
      text: trimmed,
      publishScope: 'MESH_PUBLIC',
      lastViewedAt: event.eventType === 'ROOT_THREAD' ? null : event.lastViewedAt,
    };

    events.push(eventToStore);
    this.saveEvents(events);
    return { success: true, isNew: true };
  }

  /**
   * Returns all MESH_PUBLIC events for peer synchronization.
   * Strictly filters out LOCAL_ONLY and GLOBAL_PUBLIC events.
   */
  static getMeshPublicEvents(): EventEntity[] {
    return this.getStoredEvents().filter((e) => e.publishScope === 'MESH_PUBLIC');
  }

  static getOutboxStats(): { pending: number; relayed: number } {
    try {
      const data = localStorage.getItem('threadmesh_outbox_stats_v1');
      if (data) return JSON.parse(data);
    } catch {}
    return { pending: 1, relayed: 2 };
  }

  static updateOutboxStats(pending: number, relayed: number): void {
    localStorage.setItem('threadmesh_outbox_stats_v1', JSON.stringify({ pending, relayed }));
  }

  /**
   * Executes the full Store-and-Forward scenario:
   * Phone A (Origin) -> Phone B (Relay) -> Phone C (Destination)
   * Where Phone A and C are NEVER connected directly.
   */
  static simulateMultiHopScenario(): {
    success: boolean;
    logs: string[];
    event?: EventEntity;
  } {
    const logs: string[] = [];
    const eventId = 'evt_multihop_' + Math.random().toString(36).substring(2, 7);

    logs.push('مرحله ۱: گوشی A پیامی با دامنه MESH_PUBLIC ایجاد کرد (پرش اولیه: ۰، سقف: ۳).');
    const originalEvent: EventEntity = {
      id: eventId,
      localAuthorId: 'phone_A_author',
      displayName: 'گوشی A (منشأ پیام)',
      createdAt: Date.now() - 60000,
      eventType: 'ROOT_THREAD',
      rootThreadId: eventId,
      parentEventId: null,
      targetEventId: null,
      text: 'پیام اضطراری غیرمتمرکز: اعلام وضعیت امدادی از گره منشأ A به تمام اعضای مش.',
      publishScope: 'MESH_PUBLIC',
      lastViewedAt: null,
    };

    logs.push('مرحله ۲: گوشی A با گوشی B (واسط) متصل شد و بسته را به B منتقل کرد.');
    logs.push('مرحله ۳: گوشی B پیام را در SQLite محلی خود ذخیره کرد؛ پرش را به ۱ افزایش داد و در صف Outbox قرار داد.');
    logs.push('مرحله ۴: ارتباط بین گوشی A و B قطع شد. (گوشی A هرگز به C وصل نبوده و نیست).');
    logs.push('مرحله ۵: مدتی بعد، گوشی B به محدوده گوشی C وارد شد و بسته از صف Outbox گوشی B به C تحویل داده شد.');

    // Ingest into local DB as if this device is Node C receiving from B
    const res = this.importIncomingMeshEvent(originalEvent);
    if (res.success) {
      logs.push('مرحله ۶: پیام A با موفقیت در این دستگاه (C) ذخیره و در درخت گفتگو ثبت شد! شناسه و نویسنده اصلی A حفظ گردید.');
      this.updateOutboxStats(0, 3);
    } else {
      logs.push(`خطا در ثبت نهایی: ${res.error}`);
    }

    return {
      success: res.success,
      logs,
      event: originalEvent,
    };
  }
}
