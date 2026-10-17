import React from 'react';
import { MessageSquare, Clock, Plus, Sparkles, AlertCircle } from 'lucide-react';
import { ThreadListItem } from '../../types/threadmesh';

interface ThreadListScreenProps {
  threads: ThreadListItem[];
  onSelectThread: (threadId: string) => UnitOrVoid;
  onCreateThread: () => void;
  onSeedDemo: () => void;
}

type UnitOrVoid = void;

function formatPersianRelativeTime(timestamp: number): string {
  const diff = Date.now() - timestamp;
  if (diff < 60_000) return 'هم‌اکنون';
  if (diff < 3600_000) return `${Math.floor(diff / 60_000)} دقیقه پیش`;
  if (diff < 86400_000) return `${Math.floor(diff / 3600_000)} ساعت پیش`;
  const d = new Date(timestamp);
  return `${d.toLocaleDateString('fa-IR')} ${d.toLocaleTimeString('fa-IR', { hour: '2-digit', minute: '2-digit' })}`;
}

export const ThreadListScreen: React.FC<ThreadListScreenProps> = ({
  threads,
  onSelectThread,
  onCreateThread,
  onSeedDemo,
}) => {
  return (
    <div className="flex-1 flex flex-col h-full bg-slate-100 overflow-hidden relative">
      {/* Top Bar */}
      <header className="bg-white border-b border-slate-200 px-4 py-3 shrink-0 flex items-center justify-between shadow-xs">
        <div>
          <h1 className="text-base font-bold text-slate-900">گفتگوهای محلی</h1>
          <p className="text-xs text-slate-500">ThreadMesh • شبکه متن‌محور بدون اینترنت</p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={onSeedDemo}
            title="ایجاد سناریوی نمونه شامل ۳ سطح پاسخ و تغییر دیدگاه برای راستی‌آزمایی"
            className="flex items-center gap-1.5 px-2.5 py-1.5 bg-amber-50 text-amber-800 border border-amber-200 rounded-lg text-xs font-medium hover:bg-amber-100 transition"
          >
            <Sparkles className="w-3.5 h-3.5 text-amber-600" />
            <span>بارگذاری نمونه آزمایشی</span>
          </button>
        </div>
      </header>

      {/* Main List */}
      <main className="flex-1 overflow-y-auto p-4 space-y-3">
        {threads.length === 0 ? (
          <div className="h-full flex flex-col items-center justify-center text-center p-6 text-slate-500">
            <div className="w-16 h-16 rounded-2xl bg-white border border-slate-200 flex items-center justify-center text-slate-400 mb-4 shadow-sm">
              <MessageSquare className="w-8 h-8" />
            </div>
            <h3 className="text-base font-bold text-slate-800 mb-1">هنوز هیچ موضوعی ثبت نشده است</h3>
            <p className="text-xs text-slate-500 max-w-xs leading-relaxed mb-6">
              اولین موضوع متنی خود را در پایگاه محلی ذخیره کنید یا روی «بارگذاری نمونه آزمایشی» کلیک کنید.
            </p>
            <div className="flex flex-col sm:flex-row gap-2.5">
              <button
                onClick={onCreateThread}
                className="px-4 py-2.5 bg-slate-900 text-white rounded-xl text-xs font-bold hover:bg-slate-800 transition shadow-sm flex items-center justify-center gap-2"
              >
                <Plus className="w-4 h-4" />
                <span>شروع موضوع اصلی</span>
              </button>
              <button
                onClick={onSeedDemo}
                className="px-4 py-2.5 bg-white text-slate-700 border border-slate-300 rounded-xl text-xs font-medium hover:bg-slate-50 transition shadow-xs flex items-center justify-center gap-2"
              >
                <Sparkles className="w-4 h-4 text-amber-500" />
                <span>بارگذاری ۳ سطح پاسخ + تغییر دیدگاه</span>
              </button>
            </div>
          </div>
        ) : (
          threads.map((item) => (
            <div
              key={item.rootEvent.id}
              onClick={() => onSelectThread(item.rootEvent.id)}
              className="bg-white rounded-xl border border-slate-200/80 p-4 shadow-xs hover:border-slate-300 hover:shadow-sm transition cursor-pointer text-right group"
            >
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs font-semibold text-indigo-600 bg-indigo-50 px-2 py-0.5 rounded-md">
                  {item.rootEvent.displayName}
                </span>
                <div className="flex items-center gap-2">
                  {item.hasUnseenChanges && (
                    <span className="text-[11px] font-bold text-amber-700 bg-amber-50 border border-amber-200 px-2 py-0.5 rounded-full flex items-center gap-1">
                      <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-pulse"></span>
                      تغییر جدید
                    </span>
                  )}
                  <span className="text-[11px] text-slate-400 font-mono">
                    {item.rootEvent.publishScope}
                  </span>
                </div>
              </div>

              <h2 className="text-sm font-bold text-slate-900 leading-relaxed mb-2 line-clamp-2">
                {item.rootEvent.text}
              </h2>

              {item.replyCount > 0 && (
                <div className="bg-slate-50 rounded-lg p-2.5 border border-slate-100 mb-3 text-xs text-slate-600 line-clamp-1">
                  <span className="text-slate-400 ml-1">آخرین فعالیت:</span>
                  {item.lastPreviewText}
                </div>
              )}

              <div className="flex items-center justify-between text-xs text-slate-500 pt-2 border-t border-slate-100">
                <div className="flex items-center gap-1.5 font-medium">
                  <MessageSquare className="w-3.5 h-3.5 text-slate-400" />
                  <span>{item.replyCount} پاسخ در درخت</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <Clock className="w-3.5 h-3.5 text-slate-400" />
                  <span>{formatPersianRelativeTime(item.lastActivityTime)}</span>
                </div>
              </div>
            </div>
          ))
        )}
      </main>

      {/* Floating Action Button (Compose Material 3 Style) */}
      <button
        onClick={onCreateThread}
        title="ایجاد موضوع جدید"
        className="absolute bottom-5 left-5 w-14 h-14 rounded-2xl bg-slate-900 text-white shadow-xl hover:bg-slate-800 transition flex items-center justify-center focus:outline-none focus:ring-4 focus:ring-slate-300"
      >
        <Plus className="w-7 h-7" />
      </button>
    </div>
  );
};
