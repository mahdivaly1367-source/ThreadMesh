import React, { useState } from 'react';
import { X, Repeat, Info, Send } from 'lucide-react';
import { PublishScope, PUBLISH_SCOPE_LABELS } from '../../types/threadmesh';

interface StanceChangeModalProps {
  isOpen: boolean;
  onClose: () => void;
  targetEventId: string;
  targetPreview: string;
  onSubmit: (targetEventId: string, explanation: string, scope: PublishScope) => { success: boolean; error?: string };
}

export const StanceChangeModal: React.FC<StanceChangeModalProps> = ({
  isOpen,
  onClose,
  targetEventId,
  targetPreview,
  onSubmit,
}) => {
  const [explanation, setExplanation] = useState('');
  const [scope, setScope] = useState<PublishScope>('LOCAL_ONLY');
  const [error, setError] = useState<string | null>(null);
  const maxChars = 4000;

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = explanation.trim();
    if (!trimmed) {
      setError('توضیح تغییر دیدگاه نمی‌تواند خالی باشد.');
      return;
    }
    if (trimmed.length > maxChars) {
      setError(`طول متن بیش از ۴۰۰۰ نویسه است (${trimmed.length}).`);
      return;
    }

    const res = onSubmit(targetEventId, trimmed, scope);
    if (!res.success) {
      setError(res.error || 'خطایی رخ داد.');
    } else {
      setExplanation('');
      setError(null);
      onClose();
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl w-full max-w-xl max-h-[90vh] flex flex-col shadow-2xl border border-slate-200 overflow-hidden text-right">
        {/* Header */}
        <div className="px-5 py-4 border-b border-slate-200 flex items-center justify-between bg-sky-50/70">
          <div className="flex items-center gap-2 text-sky-900">
            <Repeat className="w-5 h-5 text-sky-600" />
            <h2 className="text-base font-bold">ثبت تغییر دیدگاه (Stance Change)</h2>
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-200 transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-5 space-y-4">
          {/* Explanation Info */}
          <div className="bg-sky-50 border border-sky-200 rounded-xl p-3 flex items-start gap-2 text-xs text-sky-900">
            <Info className="w-4 h-4 text-sky-600 shrink-0 mt-0.5" />
            <div className="leading-relaxed">
              <span className="font-bold block mb-0.5">شفافیت روند تفکر و تحول اندیشه:</span>
              رویداد تغییر دیدگاه پست قبلی را پاک یا بی‌صدا بازنویسی نمی‌کند؛ بلکه به عنوان یک رویداد مستقل با ارجاع به استدلال اولیه ثبت می‌شود تا تاریخچه گفتگو دست‌نخورده باقی بماند.
            </div>
          </div>

          {/* Target Event Preview */}
          <div className="bg-slate-50 border border-slate-200 rounded-xl p-3 text-xs">
            <span className="font-bold text-slate-500 block mb-1">ارجاع به دیدگاه اولیه:</span>
            <p className="line-clamp-3 leading-relaxed text-slate-800">{targetPreview}</p>
          </div>

          <div>
            <label className="block text-xs font-bold text-slate-800 mb-1.5">
              توضیح دیدگاه جدید و علت تحول آن:
            </label>
            <textarea
              value={explanation}
              onChange={(e) => {
                if (e.target.value.length <= maxChars) {
                  setExplanation(e.target.value);
                  setError(null);
                }
              }}
              rows={5}
              placeholder="دیدگاه تجدیدنظرشدهٔ خود را با دلیل بنویسید..."
              className="w-full text-right p-3.5 text-sm border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-sky-600 focus:border-transparent transition resize-none"
            />
            <div className="flex items-center justify-between mt-1 text-xs">
              <span className={error ? 'text-rose-600 font-medium' : 'text-slate-400'}>
                {error || 'این رویداد به ساختار درخت متصل خواهد شد.'}
              </span>
              <span className={`font-mono ${explanation.length > 3900 ? 'text-rose-600 font-bold' : 'text-slate-500'}`}>
                {explanation.length} / {maxChars}
              </span>
            </div>
          </div>

          {/* Scope Selector */}
          <div>
            <label className="block text-xs font-bold text-slate-800 mb-2">
              دامنهٔ انتشار رویداد:
            </label>
            <div className="space-y-2">
              {(['LOCAL_ONLY', 'MESH_PUBLIC', 'GLOBAL_PUBLIC'] as PublishScope[]).map((s) => {
                const info = PUBLISH_SCOPE_LABELS[s];
                const isSelected = scope === s;
                return (
                  <label
                    key={s}
                    className={`block p-3 rounded-xl border text-right cursor-pointer transition ${
                      isSelected
                        ? 'border-sky-600 bg-sky-50/70 shadow-xs'
                        : 'border-slate-200 bg-white hover:border-slate-300'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2.5">
                        <input
                          type="radio"
                          name="scope"
                          value={s}
                          checked={isSelected}
                          onChange={() => setScope(s)}
                          className="accent-sky-600 w-4 h-4"
                        />
                        <span className="text-xs font-bold text-slate-900">{info.title}</span>
                      </div>
                      {s === 'MESH_PUBLIC' && (
                        <span className="text-[10px] font-bold text-emerald-700 bg-emerald-100 border border-emerald-200 px-2 py-0.5 rounded-md">
                          قابل انتقال در مش Nearby
                        </span>
                      )}
                      {s === 'GLOBAL_PUBLIC' && (
                        <span className="text-[10px] font-bold text-amber-700 bg-amber-100 border border-amber-200 px-2 py-0.5 rounded-md">
                          انتقال جهانی هنوز فعال نیست
                        </span>
                      )}
                    </div>
                  </label>
                );
              })}
            </div>
          </div>

          <div className="pt-2 flex items-center justify-end gap-2 border-t border-slate-100">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-xl text-xs font-medium text-slate-600 hover:bg-slate-100 transition"
            >
              انصراف
            </button>
            <button
              type="submit"
              className="px-5 py-2.5 rounded-xl text-xs font-bold bg-sky-600 text-white hover:bg-sky-700 transition flex items-center gap-1.5 shadow-sm"
            >
              <Send className="w-3.5 h-3.5" />
              <span>ثبت رویداد تغییر دیدگاه</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
