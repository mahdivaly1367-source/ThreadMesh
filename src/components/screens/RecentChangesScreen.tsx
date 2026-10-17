import React from 'react';
import { History, Clock, Repeat, CornerDownLeft, Info, CheckCircle2 } from 'lucide-react';
import { RecentChangeEventItem } from '../../types/threadmesh';

interface RecentChangesScreenProps {
  items: RecentChangeEventItem[];
  onSelectThread: (threadId: string) => void;
}

function formatPersianRelativeTime(timestamp: number): string {
  const diff = Date.now() - timestamp;
  if (diff < 60_000) return 'هم‌اکنون';
  if (diff < 3600_000) return `${Math.floor(diff / 60_000)} دقیقه پیش`;
  if (diff < 86400_000) return `${Math.floor(diff / 3600_000)} ساعت پیش`;
  const d = new Date(timestamp);
  return `${d.toLocaleDateString('fa-IR')} ${d.toLocaleTimeString('fa-IR', { hour: '2-digit', minute: '2-digit' })}`;
}

export const RecentChangesScreen: React.FC<RecentChangesScreenProps> = ({ items, onSelectThread }) => {
  return (
    <div className="flex-1 flex flex-col h-full bg-slate-100 overflow-hidden text-right">
      {/* Header */}
      <header className="bg-white border-b border-slate-200 px-4 py-3 shrink-0 shadow-xs">
        <h1 className="text-base font-bold text-slate-900">تغییرات جدید محلی</h1>
        <p className="text-xs text-slate-500">
          رویدادهای ثبت‌شده پس از آخرین بازدید شما از هر گفتگو (بر اساس زمان محلی)
        </p>
      </header>

      {/* Notice */}
      <div className="bg-amber-50 border-b border-amber-200 px-4 py-2.5 flex items-start gap-2 text-xs text-amber-900">
        <Info className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
        <p className="leading-relaxed">
          <strong>معیار زمانی محلی:</strong> این فهرست بر مبنای زمان آخرین مشاهدهٔ ثبت‌شده در پایگاه دادهٔ محلی همین دستگاه تنظیم شده است و ادعایی دربارهٔ هماهنگی زمانی سراسری ندارد.
        </p>
      </div>

      {/* Main Content */}
      <main className="flex-1 overflow-y-auto p-4 space-y-3">
        {items.length === 0 ? (
          <div className="h-full flex flex-col items-center justify-center text-center p-6 text-slate-500">
            <div className="w-16 h-16 rounded-2xl bg-white border border-slate-200 flex items-center justify-center text-emerald-500 mb-4 shadow-sm">
              <CheckCircle2 className="w-8 h-8" />
            </div>
            <h3 className="text-base font-bold text-slate-800 mb-1">تمامی گفتگوها به‌روز هستند</h3>
            <p className="text-xs text-slate-500 max-w-xs leading-relaxed">
              رویداد یا پاسخ جدیدی پس از آخرین بازدید شما در پایگاه محلی افزوده نشده است.
            </p>
          </div>
        ) : (
          items.map(({ event, rootEventTitle, parentPreviewText }) => {
            const isStance = event.eventType === 'STANCE_CHANGED';
            return (
              <div
                key={event.id}
                onClick={() => onSelectThread(event.rootThreadId)}
                className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs hover:border-slate-300 transition cursor-pointer group"
              >
                <div className="flex items-center justify-between gap-2 mb-2">
                  <div className="flex items-center gap-1.5">
                    {isStance ? (
                      <span className="flex items-center gap-1 text-[11px] font-bold text-sky-700 bg-sky-50 px-2 py-0.5 rounded-md">
                        <Repeat className="w-3 h-3 text-sky-600" />
                        تغییر دیدگاه از {event.displayName}
                      </span>
                    ) : (
                      <span className="flex items-center gap-1 text-[11px] font-bold text-indigo-700 bg-indigo-50 px-2 py-0.5 rounded-md">
                        <CornerDownLeft className="w-3 h-3 text-indigo-600" />
                        پاسخ جدید از {event.displayName}
                      </span>
                    )}
                  </div>
                  <span className="text-[11px] text-slate-400 flex items-center gap-1">
                    <Clock className="w-3 h-3" />
                    {formatPersianRelativeTime(event.createdAt)}
                  </span>
                </div>

                <div className="text-xs text-slate-500 mb-1.5 font-medium">
                  موضوع بحث: <span className="text-slate-800">{rootEventTitle}</span>
                </div>

                {parentPreviewText && (
                  <div className="text-[11px] text-slate-400 bg-slate-50 p-2 rounded-lg border border-slate-100 mb-2 line-clamp-1">
                    در پاسخ به: {parentPreviewText}
                  </div>
                )}

                <p className="text-xs text-slate-800 leading-relaxed line-clamp-3">
                  {event.text}
                </p>

                <div className="mt-3 pt-2 border-t border-slate-100 flex items-center justify-between text-[11px] text-slate-400">
                  <span>شناسه رویداد: {event.id.slice(0, 10)}...</span>
                  <span className="text-indigo-600 font-semibold group-hover:underline">مشاهده در درخت گفتگو ←</span>
                </div>
              </div>
            );
          })
        )}
      </main>
    </div>
  );
};
