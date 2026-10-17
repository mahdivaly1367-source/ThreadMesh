import React, { useState } from 'react';
import { X, Send } from 'lucide-react';
import { PublishScope, PUBLISH_SCOPE_LABELS } from '../../types/threadmesh';

interface CreatePostModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (text: string, scope: PublishScope) => { success: boolean; error?: string };
  parentPreview?: string;
  isReply: boolean;
}

export const CreatePostModal: React.FC<CreatePostModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
  parentPreview,
  isReply,
}) => {
  const [text, setText] = useState('');
  const [scope, setScope] = useState<PublishScope>('LOCAL_ONLY');
  const [error, setError] = useState<string | null>(null);
  const maxChars = 4000;

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = text.trim();
    if (!trimmed) {
      setError('متن نمی‌تواند خالی باشد.');
      return;
    }
    if (trimmed.length > maxChars) {
      setError(`طول متن بیش از ۴۰۰۰ نویسه است (${trimmed.length}).`);
      return;
    }

    const res = onSubmit(trimmed, scope);
    if (!res.success) {
      setError(res.error || 'خطایی رخ داد.');
    } else {
      setText('');
      setError(null);
      onClose();
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl w-full max-w-xl max-h-[90vh] flex flex-col shadow-2xl border border-slate-200 overflow-hidden text-right">
        {/* Header */}
        <div className="px-5 py-4 border-b border-slate-200 flex items-center justify-between bg-slate-50">
          <h2 className="text-base font-bold text-slate-900">
            {isReply ? 'پاسخ به یادداشت در درخت' : 'آغاز موضوع جدید'}
          </h2>
          <button
            onClick={onClose}
            className="p-1 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-200 transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-5 space-y-4">
          {parentPreview && (
            <div className="bg-slate-50 border border-slate-200 rounded-xl p-3 text-xs text-slate-600">
              <span className="font-bold text-indigo-600 block mb-1">در پاسخ به:</span>
              <p className="line-clamp-3 leading-relaxed text-slate-700">{parentPreview}</p>
            </div>
          )}

          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1.5">
              {isReply ? 'متن پاسخ شما:' : 'متن استدلال یا موضوع:'}
            </label>
            <textarea
              value={text}
              onChange={(e) => {
                if (e.target.value.length <= maxChars) {
                  setText(e.target.value);
                  setError(null);
                }
              }}
              rows={6}
              placeholder={isReply ? 'استدلال یا پاسخ خود را به این شاخه اضافه کنید...' : 'موضوع جدید را با جزئیات بنویسید...'}
              className="w-full text-right p-3.5 text-sm border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-slate-900 focus:border-transparent transition resize-none"
            />
            <div className="flex items-center justify-between mt-1 text-xs">
              <span className={error ? 'text-rose-600 font-medium' : 'text-slate-400'}>
                {error || 'متن به صورت محلی و تغییرناپذیر ذخیره خواهد شد.'}
              </span>
              <span className={`font-mono ${text.length > 3900 ? 'text-rose-600 font-bold' : 'text-slate-500'}`}>
                {text.length} / {maxChars}
              </span>
            </div>
          </div>

          {/* Scope Selector */}
          <div>
            <label className="block text-xs font-bold text-slate-800 mb-2">
              دامنهٔ انتشار (Publish Scope):
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
                        ? 'border-indigo-600 bg-indigo-50/70 shadow-xs'
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
                          className="accent-slate-900 w-4 h-4"
                        />
                        <span className="text-xs font-bold text-slate-900">{info.title}</span>
                      </div>
                      {s === 'MESH_PUBLIC' && (
                        <span className="text-[10px] font-bold text-emerald-700 bg-emerald-100 border border-emerald-200 px-2 py-0.5 rounded-md">
                          فعال در مش Nearby
                        </span>
                      )}
                      {s === 'GLOBAL_PUBLIC' && (
                        <span className="text-[10px] font-bold text-amber-700 bg-amber-100 border border-amber-200 px-2 py-0.5 rounded-md">
                          انتقال جهانی هنوز فعال نیست
                        </span>
                      )}
                    </div>
                    <p className="text-[11px] text-slate-500 mt-1 mr-6 leading-relaxed">
                      {info.desc}
                    </p>
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
              className="px-5 py-2.5 rounded-xl text-xs font-bold bg-slate-900 text-white hover:bg-slate-800 transition flex items-center gap-1.5 shadow-sm"
            >
              <Send className="w-3.5 h-3.5" />
              <span>{isReply ? 'ثبت پاسخ در پایگاه محلی' : 'ایجاد موضوع'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
