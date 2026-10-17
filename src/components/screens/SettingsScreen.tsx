import React, { useState } from 'react';
import { User, KeyRound, Shield, Check, Info } from 'lucide-react';
import { LocalSettings } from '../../storage/threadmeshDb';

interface SettingsScreenProps {
  settings: LocalSettings;
  onUpdateSettings: (newSettings: LocalSettings) => void;
}

export const SettingsScreen: React.FC<SettingsScreenProps> = ({ settings, onUpdateSettings }) => {
  const [displayName, setDisplayName] = useState(settings.displayName);
  const [saved, setSaved] = useState(false);

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = displayName.trim();
    if (trimmed) {
      onUpdateSettings({
        ...settings,
        displayName: trimmed,
      });
      setSaved(true);
      setTimeout(() => setSaved(false), 2500);
    }
  };

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-100 overflow-hidden text-right">
      <header className="bg-white border-b border-slate-200 px-4 py-3 shrink-0 shadow-xs">
        <h1 className="text-base font-bold text-slate-900">تنظیمات هویت محلی</h1>
        <p className="text-xs text-slate-500">پیکربندی نام نمایشی و کلید گره این دستگاه</p>
      </header>

      <main className="flex-1 overflow-y-auto p-4 space-y-4">
        {/* Name Form Card */}
        <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs">
          <form onSubmit={handleSave} className="space-y-4">
            <div className="flex items-center gap-2 text-slate-900 font-bold text-sm">
              <User className="w-4 h-4 text-indigo-600" />
              <span>نام نمایشی شما:</span>
            </div>

            <div>
              <input
                type="text"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                placeholder="مثلاً: پژوهشگر محلی، سارا، و..."
                className="w-full text-right p-3 text-sm border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-slate-900 focus:border-transparent transition"
              />
              <p className="text-xs text-slate-400 mt-1.5">
                این نام در بالای پیام‌ها و تغییرات دیدگاه ثبت‌شده از این دستگاه نمایش داده می‌شود.
              </p>
            </div>

            <button
              type="submit"
              className="w-full py-2.5 bg-slate-900 hover:bg-slate-800 text-white rounded-xl text-xs font-bold transition flex items-center justify-center gap-1.5 shadow-sm"
            >
              {saved ? (
                <>
                  <Check className="w-4 h-4 text-emerald-400" />
                  <span>ذخیره شد!</span>
                </>
              ) : (
                <span>ذخیره نام نمایشی محلی</span>
              )}
            </button>
          </form>
        </div>

        {/* Local Node UUID */}
        <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-2">
          <div className="flex items-center gap-2 text-slate-900 font-bold text-sm">
            <KeyRound className="w-4 h-4 text-slate-600" />
            <span>شناسهٔ رمزنگاری گره محلی (Author UUID):</span>
          </div>
          <div className="bg-slate-50 border border-slate-200 rounded-lg p-3 font-mono text-xs text-slate-700 break-all select-all">
            {settings.localAuthorId}
          </div>
          <p className="text-[11px] text-slate-400">
            شناسهٔ بالا به هر رویداد جدید به عنوان نویسنده محلی الصاق می‌شود تا تعلق شاخه‌ها در گراف گفتگو مشخص باشد.
          </p>
        </div>

        {/* Disclaimer / Non-claim */}
        <div className="bg-slate-50 border border-slate-200 rounded-xl p-4 flex items-start gap-2.5 text-xs text-slate-600">
          <Info className="w-5 h-5 text-slate-500 shrink-0 mt-0.5" />
          <div className="leading-relaxed">
            <span className="font-bold text-slate-800 block mb-1">عدم ادعای هویت آنلاین یا احراز هویت مرکزی:</span>
            در این فاز آفلاین، هیچ سرور مرکزی، ثبت‌نام آنلاین، رمز عبور یا هویت تأییدشده دولتی/شرکتی وجود ندارد. هویت‌ها صرفاً کلیدهای محلی روی همین دستگاه هستند.
          </div>
        </div>
      </main>
    </div>
  );
};
