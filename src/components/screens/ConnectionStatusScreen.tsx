import React, { useState, useEffect } from 'react';
import {
  Radio,
  WifiOff,
  Cpu,
  Network,
  CheckCircle2,
  AlertTriangle,
  KeyRound,
  RefreshCw,
  Send,
  Shield,
  Layers,
  Sparkles,
  Info,
} from 'lucide-react';
import { DiscoveredPeer, MeshServiceState, PeerConnectionStatus, EventEntity } from '../../types/threadmesh';
import { ThreadMeshStorage } from '../../storage/threadmeshDb';

interface ConnectionStatusScreenProps {
  onRefreshData?: () => void;
}

export const ConnectionStatusScreen: React.FC<ConnectionStatusScreenProps> = ({ onRefreshData }) => {
  const [isMeshEnabled, setIsMeshEnabled] = useState(false);
  const [serviceState, setServiceState] = useState<MeshServiceState>('STOPPED');
  const [peers, setPeers] = useState<DiscoveredPeer[]>([]);
  const [syncNotice, setSyncNotice] = useState<string | null>(null);

  // Toggle Mesh Service
  const handleToggleMesh = () => {
    if (isMeshEnabled) {
      setIsMeshEnabled(false);
      setServiceState('STOPPED');
      setPeers([]);
      setSyncNotice('مش نزدیک خاموش شد.');
    } else {
      setIsMeshEnabled(true);
      setServiceState('STARTING');
      setSyncNotice('در حال راه‌اندازی Nearby Connections با استراتژی P2P_CLUSTER...');

      setTimeout(() => {
        setServiceState('RUNNING');
        // Discover a nearby simulated peer
        setPeers([
          {
            endpointId: 'node_galaxy_s23',
            endpointName: 'دستگاه همراه مجاور (Galaxy S23 - مهدی)',
            status: 'DISCOVERED',
          },
        ]);
        setSyncNotice('مش فعال شد: ۱ همتای فعال در مجاورت فیزیکی کشف شد.');
      }, 900);
    }
  };

  // Request connection
  const handleRequestConnection = (endpointId: string) => {
    setPeers((prev) =>
      prev.map((p) =>
        p.endpointId === endpointId
          ? {
              ...p,
              status: 'AUTHENTICATION_PENDING',
              authDigits: '7492',
              isIncomingRequest: false,
            }
          : p
      )
    );
    setSyncNotice('درخواست اتصال فرستاده شد. لطفاً کد تأیید ۷۴۹۲ را در دستگاه مقابل بررسی کنید.');
  };

  // Accept Connection
  const handleAcceptConnection = (endpointId: string) => {
    setPeers((prev) =>
      prev.map((p) => (p.endpointId === endpointId ? { ...p, status: 'CONNECTED' } : p))
    );
    setSyncNotice('اتصال همتا تأیید شد. اکنون انتقال رویدادهای MESH_PUBLIC ممکن است.');
  };

  // Reject Connection
  const handleRejectConnection = (endpointId: string) => {
    setPeers((prev) =>
      prev.map((p) => (p.endpointId === endpointId ? { ...p, status: 'REJECTED' } : p))
    );
    setSyncNotice('درخواست اتصال رد شد.');
  };

  // Synchronize MESH_PUBLIC events
  const handleSyncPublicEvents = () => {
    const publicEvents = ThreadMeshStorage.getMeshPublicEvents();
    const count = publicEvents.length;
    setSyncNotice(
      count > 0
        ? `تعداد ${count} رویداد با دامنه MESH_PUBLIC با موفقیت به همتا فرستاده شد (رویدادهای LOCAL_ONLY فیلتر شدند).`
        : 'هیچ رویدادی با برچسب MESH_PUBLIC در دیتابیس محلی شما برای ارسال وجود ندارد. (رویدادهای LOCAL_ONLY محرمانه مانده‌اند).'
    );
  };

  // Simulate receiving an incoming MESH_PUBLIC reply from peer
  const handleSimulateIncomingEvent = () => {
    const events = ThreadMeshStorage.getAllEvents();
    const targetRoot = events.find((e) => e.eventType === 'ROOT_THREAD');
    const rootId = targetRoot ? targetRoot.id : 'evt_mesh_demo_root';
    const newIncomingId = 'mesh_rx_' + Math.random().toString(36).substring(2, 8);

    const incomingEvent: EventEntity = {
      id: newIncomingId,
      localAuthorId: 'peer_node_galaxy',
      displayName: 'مهدی (همتای نزدیک)',
      createdAt: Date.now(),
      eventType: 'REPLY',
      rootThreadId: rootId,
      parentEventId: rootId,
      targetEventId: null,
      text: 'دریافت پاسخ زنده از طریق Nearby Connections (P2P Cluster): استدلال شما در شاخه محلی دریافت و در حافظه SQLite من ادغام شد.',
      publishScope: 'MESH_PUBLIC',
    };

    const res = ThreadMeshStorage.importIncomingMeshEvent(incomingEvent);
    if (res.success) {
      setSyncNotice('رویداد MESH_PUBLIC جدید از دستگاه مهدی دریافت و در پایگاه محلی ثبت شد!');
      onRefreshData?.();
    } else {
      setSyncNotice(res.error || 'خطا در دریافت رویداد');
    }
  };

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-100 overflow-hidden text-right">
      <header className="bg-white border-b border-slate-200 px-4 py-3 shrink-0 shadow-xs flex items-center justify-between">
        <div>
          <h1 className="text-base font-bold text-slate-900">مش نزدیک (Nearby Connections)</h1>
          <p className="text-xs text-slate-500">ارتباط آفلاین مستقیم همتا به همتا با استراتژی P2P_CLUSTER</p>
        </div>
      </header>

      <main className="flex-1 overflow-y-auto p-4 space-y-4">
        {/* Master Toggle Card */}
        <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div
                className={`w-12 h-12 rounded-2xl flex items-center justify-center transition ${
                  isMeshEnabled ? 'bg-emerald-50 text-emerald-600' : 'bg-slate-100 text-slate-400'
                }`}
              >
                <Radio className={`w-6 h-6 ${isMeshEnabled ? 'animate-pulse' : ''}`} />
              </div>
              <div>
                <h2 className="text-sm font-bold text-slate-900">روشن/خاموش کردن مش نزدیک</h2>
                <p className="text-xs text-slate-500 mt-0.5">
                  {isMeshEnabled
                    ? 'تبلیغ و جستجوی همتاها (Advertising & Discovering) فعال است'
                    : 'خاموش (هیچ رادیویی فعال نیست و هیچ ارتباط مخفی وجود ندارد)'}
                </p>
              </div>
            </div>

            <button
              onClick={handleToggleMesh}
              className={`px-4 py-2 rounded-xl text-xs font-bold transition shadow-xs ${
                isMeshEnabled
                  ? 'bg-rose-600 hover:bg-rose-700 text-white'
                  : 'bg-slate-900 hover:bg-slate-800 text-white'
              }`}
            >
              {isMeshEnabled ? 'خاموش کردن' : 'روشن کردن مش'}
            </button>
          </div>

          {syncNotice && (
            <div className="mt-4 p-3 bg-slate-50 border border-slate-200 rounded-lg text-xs text-slate-700 leading-relaxed">
              {syncNotice}
            </div>
          )}
        </div>

        {/* Strategy P2P_CLUSTER Explanation */}
        <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs">
          <div className="flex items-center gap-2 text-indigo-700 font-bold text-xs mb-1.5">
            <Layers className="w-4 h-4" />
            <span>مشخصات فنی استراتژی P2P_CLUSTER گوگل:</span>
          </div>
          <p className="text-xs text-slate-600 leading-relaxed">
            استراتژی <strong>P2P_CLUSTER</strong> امکان اتصال M-به-N همزمان میان چند دستگاه مجاور را بدون نیاز به مودم فراهم می‌کند. توجه داشته باشید این معماری صرفاً <strong>اتصال مستقیم همتا به همتا</strong> است و مسیریابی چندمرحله‌ای (Multi-hop mesh) به‌خودی‌خود انجام نمی‌دهد.
          </p>
        </div>

        {/* Nearby Peers List */}
        <div className="space-y-3">
          <div className="flex items-center justify-between text-xs font-bold text-slate-800">
            <span>همتاهای کشف‌شده در امواج مجاور:</span>
            <span className="text-slate-500 font-mono">{peers.length} گره</span>
          </div>

          {!isMeshEnabled ? (
            <div className="bg-white rounded-xl border border-dashed border-slate-300 p-6 text-center text-xs text-slate-400">
              مش نزدیک خاموش است. با لمس دکمهٔ بالا، تبلیغ و کشف دستگاه‌ها آغاز می‌شود.
            </div>
          ) : peers.length === 0 ? (
            <div className="bg-white rounded-xl border border-slate-200 p-6 text-center text-xs text-slate-500 flex flex-col items-center">
              <RefreshCw className="w-5 h-5 animate-spin text-slate-400 mb-2" />
              <span>در حال جستجوی دستگاه‌های دارای ThreadMesh در شعاع بلوتوث و Wi-Fi...</span>
            </div>
          ) : (
            peers.map((peer) => (
              <div
                key={peer.endpointId}
                className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs space-y-3"
              >
                <div className="flex items-center justify-between">
                  <div>
                    <h3 className="text-xs font-bold text-slate-900">{peer.endpointName}</h3>
                    <span className="text-[10px] text-slate-400 font-mono">شناسه: {peer.endpointId}</span>
                  </div>

                  {peer.status === 'DISCOVERED' && (
                    <button
                      onClick={() => handleRequestConnection(peer.endpointId)}
                      className="px-3 py-1.5 bg-slate-900 text-white rounded-lg text-xs font-bold hover:bg-slate-800 transition"
                    >
                      درخواست اتصال
                    </button>
                  )}

                  {peer.status === 'CONNECTED' && (
                    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 bg-emerald-50 text-emerald-700 border border-emerald-200 rounded-full text-[11px] font-bold">
                      <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
                      متصل (Ready)
                    </span>
                  )}

                  {peer.status === 'REJECTED' && (
                    <span className="text-xs font-medium text-rose-600 bg-rose-50 px-2 py-1 rounded">
                      اتصال رد شد
                    </span>
                  )}
                </div>

                {/* Handshake Authentication Code Box */}
                {peer.status === 'AUTHENTICATION_PENDING' && (
                  <div className="bg-sky-50 border border-sky-200 rounded-xl p-3 text-xs text-sky-900 space-y-2">
                    <div className="flex items-center gap-1.5 font-bold">
                      <KeyRound className="w-4 h-4 text-sky-600" />
                      <span>کد تأیید احراز هویت دوطرفه:</span>
                      <span className="font-mono text-base text-sky-800 bg-white px-2 py-0.5 rounded border border-sky-300 font-bold">
                        {peer.authDigits}
                      </span>
                    </div>
                    <p className="text-[11px] text-slate-600 leading-relaxed">
                      قبل از پذیرش، مطمئن شوید همین کد رقمی روی صفحهٔ دستگاه مقابل نیز نمایش داده شده است تا از اتصال اشتباه جلوگیری شود.
                    </p>
                    <div className="flex items-center justify-end gap-2 pt-1">
                      <button
                        onClick={() => handleRejectConnection(peer.endpointId)}
                        className="px-3 py-1.5 bg-white text-rose-600 border border-rose-200 hover:bg-rose-50 rounded-lg text-xs font-medium transition"
                      >
                        رد اتصال
                      </button>
                      <button
                        onClick={() => handleAcceptConnection(peer.endpointId)}
                        className="px-4 py-1.5 bg-emerald-600 text-white hover:bg-emerald-700 rounded-lg text-xs font-bold transition shadow-xs"
                      >
                        تأیید و پذیرش اتصال
                      </button>
                    </div>
                  </div>
                )}

                {/* Connected Peer Actions */}
                {peer.status === 'CONNECTED' && (
                  <div className="pt-2 border-t border-slate-100 flex flex-wrap gap-2">
                    <button
                      onClick={handleSyncPublicEvents}
                      className="flex items-center gap-1.5 px-3 py-1.5 bg-indigo-50 text-indigo-700 hover:bg-indigo-100 rounded-lg text-xs font-bold transition"
                    >
                      <Send className="w-3.5 h-3.5" />
                      <span>ارسال رویدادهای MESH_PUBLIC به همتا</span>
                    </button>

                    <button
                      onClick={handleSimulateIncomingEvent}
                      className="flex items-center gap-1.5 px-3 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-lg text-xs font-medium transition"
                      title="آزمایش دریافت یک پاسخ عمومی از همتا به دیتابیس محلی"
                    >
                      <Sparkles className="w-3.5 h-3.5 text-amber-500" />
                      <span>شبیه‌سازی دریافت پاسخ از همتا</span>
                    </button>
                  </div>
                )}
              </div>
            ))
          )}
        </div>

        {/* Phase 3: Multi-Hop Store-and-Forward Section */}
        <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">
                <Network className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-sm font-bold text-slate-900">انتقال چندمرحله‌ای (Store & Forward)</h3>
                <p className="text-xs text-slate-500">سناریوی اصلی: گوشی A → گوشی B → گوشی C</p>
              </div>
            </div>
            <span className="text-[10px] font-bold bg-indigo-100 text-indigo-800 px-2 py-0.5 rounded-full">
              فاز ۳ فعال
            </span>
          </div>

          {/* Outbox & Queue Status */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-center text-xs">
            <div className="bg-slate-50 p-2.5 rounded-xl border border-slate-200">
              <span className="text-slate-400 block text-[10px]">صف انتظار رله (Outbox)</span>
              <span className="text-sm font-bold text-slate-800">
                {ThreadMeshStorage.getOutboxStats().pending} پیام
              </span>
            </div>
            <div className="bg-slate-50 p-2.5 rounded-xl border border-slate-200">
              <span className="text-slate-400 block text-[10px]">پیام‌های رله‌شده</span>
              <span className="text-sm font-bold text-emerald-600">
                {ThreadMeshStorage.getOutboxStats().relayed} بسته
              </span>
            </div>
            <div className="bg-slate-50 p-2.5 rounded-xl border border-slate-200">
              <span className="text-slate-400 block text-[10px]">سقف پرش (Max Hops)</span>
              <span className="text-sm font-bold text-indigo-600">۵ پرش</span>
            </div>
            <div className="bg-slate-50 p-2.5 rounded-xl border border-slate-200">
              <span className="text-slate-400 block text-[10px]">زمان انقضا (TTL)</span>
              <span className="text-sm font-bold text-slate-700">۷ روز</span>
            </div>
          </div>

          {/* Architecture diagram */}
          <div className="bg-indigo-50/70 border border-indigo-200 rounded-xl p-3 text-xs text-indigo-950 space-y-1.5">
            <div className="font-bold flex items-center gap-1.5 text-indigo-800">
              <span>مسیر ایزولهٔ فیزیکی:</span>
              <span className="font-mono bg-white px-2 py-0.5 rounded border border-indigo-200 text-indigo-700">
                گوشی A ──[پرش ۱]──► گوشی B ──[پرش ۲]──► گوشی C
              </span>
            </div>
            <p className="text-[11px] text-slate-600 leading-relaxed">
              گوشی A و C هرگز ارتباط مستقیمی ندارند. گوشی B هنگام برقراری اتصال با A پیام را دریافت، در SQLite محلی ذخیره، و در صف Outbox قرار می‌دهد. با ورود B به محدودهٔ C، پیام بدون اینترنت به C تحویل داده می‌شود.
            </p>
          </div>

          {/* Run multi-hop test button */}
          <button
            onClick={() => {
              const res = ThreadMeshStorage.simulateMultiHopScenario();
              setSyncNotice(res.logs.join('\n'));
              onRefreshData?.();
            }}
            className="w-full py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-bold transition flex items-center justify-center gap-2 shadow-xs"
          >
            <Sparkles className="w-4 h-4 text-amber-300" />
            <span>آزمایش سناریوی انتقال چندمرحله‌ای (A → B → C)</span>
          </button>
        </div>

        {/* Security & Strict Scope Rules Card */}
        <div className="bg-slate-50 border border-slate-200 rounded-xl p-4 text-xs text-slate-600 space-y-2">
          <div className="flex items-center gap-2 font-bold text-slate-800">
            <Shield className="w-4 h-4 text-slate-600" />
            <span>قوانین امنیتی و مکانیزم‌های فاز ۳:</span>
          </div>
          <ul className="space-y-1.5 list-disc list-inside leading-relaxed text-slate-600">
            <li>
              <strong>فیلتر صریح دامنه:</strong> فقط رویدادهای <code className="bg-white px-1 py-0.5 rounded border border-slate-300 text-slate-800">MESH_PUBLIC</code> اجازهٔ عبور از مش دارند. رویدادهای <code className="bg-white px-1 py-0.5 rounded border border-slate-300 text-slate-800">LOCAL_ONLY</code> و <code className="bg-white px-1 py-0.5 rounded border border-slate-300 text-slate-800">GLOBAL_PUBLIC</code> هرگز وارد صف انتقال چندمرحله‌ای نمی‌شوند.
            </li>
            <li>
              <strong>جلوگیری از ایجاد حلقه (Loop Prevention):</strong> بسته حامل آرایهٔ <code className="bg-white px-1 py-0.5 rounded font-mono text-[11px]">visitedNodeIds</code> است. اگر گره‌ای نام خود را در مسیر ببیند، بسته بی‌صدا دور انداخته می‌شود.
            </li>
            <li>
              <strong>محدودیت تعداد پرش‌ها (Hop Count):</strong> بسته با رسیدن به سقف پرش مجاز متوقف شده و به گره‌های بعدی ارسال نمی‌گردد.
            </li>
            <li>
              <strong>انقضای زمانی (TTL):</strong> بسته‌های قدیمی‌تر از زمان انقضا، منقضی شده و از صف حذف می‌شوند.
            </li>
            <li>
              <strong>پایداری کامل:</strong> پیام‌ها و صف Outbox در پایگاه محلی Room ذخیره می‌شوند و پس از بستن کامل اپ یا راه‌اندازی مجدد گوشی محفوظ می‌مانند.
            </li>
          </ul>
        </div>
      </main>
    </div>
  );
};
