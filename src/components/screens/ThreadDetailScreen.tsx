import React, { useState } from 'react';
import {
  ArrowRight,
  GitBranch,
  CornerDownLeft,
  ChevronDown,
  ChevronLeft,
  Repeat,
  ShieldAlert,
  Clock,
  Share2,
} from 'lucide-react';
import { TreeNode, EventEntity } from '../../types/threadmesh';

interface ThreadDetailScreenProps {
  tree: TreeNode;
  onBack: () => void;
  onReply: (targetEventId: string, parentPreview: string) => void;
  onStanceChange: (targetEventId: string, targetPreview: string) => void;
}

function formatPersianRelativeTime(timestamp: number): string {
  const diff = Date.now() - timestamp;
  if (diff < 60_000) return 'هم‌اکنون';
  if (diff < 3600_000) return `${Math.floor(diff / 60_000)} دقیقه پیش`;
  if (diff < 86400_000) return `${Math.floor(diff / 3600_000)} ساعت پیش`;
  const d = new Date(timestamp);
  return `${d.toLocaleDateString('fa-IR')} ${d.toLocaleTimeString('fa-IR', { hour: '2-digit', minute: '2-digit' })}`;
}

interface TreeNodeItemProps {
  node: TreeNode;
  onReply: (targetEventId: string, parentPreview: string) => void;
  onStanceChange: (targetEventId: string, targetPreview: string) => void;
}

const TreeNodeItem: React.FC<TreeNodeItemProps> = ({ node, onReply, onStanceChange }) => {
  const [collapsed, setCollapsed] = useState(false);
  const { event, depth, children, stanceChanges } = node;
  const isRoot = depth === 0;

  return (
    <div className={`relative ${!isRoot ? 'mr-4 sm:mr-8 border-r-2 border-indigo-200/60 pr-3 sm:pr-4 mt-3' : 'mt-1'}`}>
      {/* Node Card */}
      <div
        className={`rounded-xl border transition shadow-xs text-right ${
          isRoot
            ? 'bg-white border-indigo-300 ring-2 ring-indigo-50/80 p-4 sm:p-5'
            : 'bg-white/95 border-slate-200 p-3.5 sm:p-4 hover:border-slate-300'
        }`}
      >
        {/* Node Header */}
        <div className="flex items-center justify-between gap-2 mb-2">
          <div className="flex items-center gap-2">
            <div
              className={`w-7 h-7 rounded-lg flex items-center justify-center text-xs font-bold ${
                isRoot
                  ? 'bg-slate-900 text-white'
                  : depth === 1
                  ? 'bg-indigo-600 text-white'
                  : depth === 2
                  ? 'bg-sky-600 text-white'
                  : 'bg-slate-700 text-white'
              }`}
            >
              {event.displayName.slice(0, 1)}
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-xs font-bold text-slate-900">{event.displayName}</span>
                {isRoot && (
                  <span className="text-[10px] font-bold bg-slate-900 text-white px-2 py-0.5 rounded-md">
                    موضوع اصلی
                  </span>
                )}
                {!isRoot && (
                  <span className="text-[10px] font-medium bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded">
                    سطح {depth}
                  </span>
                )}
              </div>
              <span className="text-[10px] text-slate-400 font-mono">شناسه: {event.id.slice(0, 8)}...</span>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <span className="text-[11px] text-slate-400 flex items-center gap-1">
              <Clock className="w-3 h-3" />
              {formatPersianRelativeTime(event.createdAt)}
            </span>
            {children.length > 0 && (
              <button
                onClick={() => setCollapsed(!collapsed)}
                className="flex items-center gap-1 text-[11px] font-medium text-slate-600 bg-slate-100 hover:bg-slate-200 px-2 py-1 rounded-lg transition"
                title={collapsed ? 'باز کردن شاخه' : 'بستن شاخه'}
              >
                {collapsed ? <ChevronLeft className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
                <span>{children.length} شاخه</span>
              </button>
            )}
          </div>
        </div>

        {/* Node Body Text */}
        <p className="text-sm text-slate-800 leading-relaxed font-normal whitespace-pre-wrap mb-3">
          {event.text}
        </p>

        {/* Stance Changes (Evolution records attached to this node) */}
        {stanceChanges.length > 0 && (
          <div className="my-3 space-y-2 border-t border-sky-100 pt-2.5">
            <div className="flex items-center gap-1.5 text-xs font-bold text-sky-800">
              <Repeat className="w-3.5 h-3.5 text-sky-600" />
              <span>تحول و ثبت تغییر دیدگاه (Stance Change):</span>
            </div>
            {stanceChanges.map((sc) => (
              <div
                key={sc.id}
                className="bg-sky-50/90 border border-sky-200 rounded-lg p-3 text-right"
              >
                <div className="flex items-center justify-between text-[11px] text-sky-700 font-medium mb-1">
                  <span>تغییر دیدگاه ثبت‌شده توسط: {sc.displayName}</span>
                  <span className="text-slate-400 font-mono text-[10px]">{formatPersianRelativeTime(sc.createdAt)}</span>
                </div>
                <p className="text-xs text-slate-800 leading-relaxed">
                  {sc.text}
                </p>
                <div className="mt-1.5 text-[10px] text-sky-600/80">
                  * این دیدگاه به شکل رویداد مستقل ذخیره شده و استدلال قبلی بدون تغییر محفوظ است.
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Action Buttons */}
        <div className="flex items-center justify-between pt-2 border-t border-slate-100">
          <div className="text-[11px] text-slate-400">
            دامنه: <span className="font-mono text-slate-600">{event.publishScope}</span>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => onStanceChange(event.id, event.text)}
              className="flex items-center gap-1 px-2.5 py-1 text-xs font-medium text-sky-700 bg-sky-50 hover:bg-sky-100 rounded-lg transition"
            >
              <Repeat className="w-3.5 h-3.5 text-sky-600" />
              <span>تغییر دیدگاه</span>
            </button>
            <button
              onClick={() => onReply(event.id, event.text)}
              className="flex items-center gap-1 px-2.5 py-1 text-xs font-bold text-indigo-700 bg-indigo-50 hover:bg-indigo-100 rounded-lg transition"
            >
              <CornerDownLeft className="w-3.5 h-3.5 text-indigo-600" />
              <span>پاسخ به این پست</span>
            </button>
          </div>
        </div>
      </div>

      {/* Children Sub-tree (Collapsed toggle) */}
      {!collapsed && children.length > 0 && (
        <div className="space-y-2">
          {children.map((childNode) => (
            <TreeNodeItem
              key={childNode.event.id}
              node={childNode}
              onReply={onReply}
              onStanceChange={onStanceChange}
            />
          ))}
        </div>
      )}

      {collapsed && children.length > 0 && (
        <div className="mr-6 my-2">
          <button
            onClick={() => setCollapsed(false)}
            className="text-xs text-indigo-600 hover:text-indigo-800 font-medium flex items-center gap-1 bg-indigo-50/60 px-3 py-1.5 rounded-lg border border-dashed border-indigo-200"
          >
            <ChevronLeft className="w-3.5 h-3.5" />
            <span>نمایش {children.length} شاخهٔ بسته شده...</span>
          </button>
        </div>
      )}
    </div>
  );
};

export const ThreadDetailScreen: React.FC<ThreadDetailScreenProps> = ({
  tree,
  onBack,
  onReply,
  onStanceChange,
}) => {
  return (
    <div className="flex-1 flex flex-col h-full bg-slate-100 overflow-hidden">
      {/* Top Bar */}
      <header className="bg-white border-b border-slate-200 px-4 py-3 shrink-0 flex items-center justify-between shadow-xs">
        <div className="flex items-center gap-2">
          <button
            onClick={onBack}
            className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-700 transition"
            title="بازگشت به فهرست"
          >
            <ArrowRight className="w-5 h-5" />
          </button>
          <div>
            <h1 className="text-sm font-bold text-slate-900">درخت سلسله‌مراتبی گفتگو</h1>
            <p className="text-xs text-slate-500">مشاهده و افزودن شاخه‌ها و تغییرات دیدگاه</p>
          </div>
        </div>

        <button
          onClick={() => onReply(tree.event.id, tree.event.text)}
          className="flex items-center gap-1.5 px-3 py-1.5 bg-slate-900 text-white rounded-lg text-xs font-bold hover:bg-slate-800 transition"
        >
          <CornerDownLeft className="w-3.5 h-3.5" />
          <span>پاسخ به ریشه</span>
        </button>
      </header>

      {/* Info Notice */}
      <div className="bg-white/80 border-b border-slate-200 px-4 py-2 flex items-center gap-2 text-xs text-slate-600">
        <GitBranch className="w-4 h-4 text-indigo-600 shrink-0" />
        <span>
          هر یادداشت شاخهٔ مستقلی است. می‌توانید روی «پاسخ به این پست» برای ایجاد گره جدید کلیک کنید.
        </span>
      </div>

      {/* Tree View Container */}
      <main className="flex-1 overflow-y-auto p-4">
        <div className="max-w-3xl mx-auto pb-12">
          <TreeNodeItem
            node={tree}
            onReply={onReply}
            onStanceChange={onStanceChange}
          />
        </div>
      </main>
    </div>
  );
};
