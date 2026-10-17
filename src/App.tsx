import React, { useState, useEffect } from 'react';
import {
  MessageSquare,
  History,
  WifiOff,
  Settings,
  Code2,
  Smartphone,
  Sparkles,
  Plane,
  BatteryCharging,
  Wifi,
  Radio,
} from 'lucide-react';
import { ThreadMeshStorage, LocalSettings } from './storage/threadmeshDb';
import { ThreadListItem, TreeNode, RecentChangeEventItem, PublishScope } from './types/threadmesh';
import { ThreadListScreen } from './components/screens/ThreadListScreen';
import { ThreadDetailScreen } from './components/screens/ThreadDetailScreen';
import { CreatePostModal } from './components/screens/CreatePostModal';
import { StanceChangeModal } from './components/screens/StanceChangeModal';
import { RecentChangesScreen } from './components/screens/RecentChangesScreen';
import { SettingsScreen } from './components/screens/SettingsScreen';
import { ConnectionStatusScreen } from './components/screens/ConnectionStatusScreen';
import { AndroidCodeExplorer } from './components/AndroidCodeExplorer';

type Tab = 'threads' | 'recent' | 'status' | 'settings';

export default function App() {
  const [activeTab, setActiveTab] = useState<Tab>('threads');
  const [threads, setThreads] = useState<ThreadListItem[]>([]);
  const [selectedThreadId, setSelectedThreadId] = useState<string | null>(null);
  const [currentTree, setCurrentTree] = useState<TreeNode | null>(null);
  const [recentChanges, setRecentChanges] = useState<RecentChangeEventItem[]>([]);
  const [settings, setSettings] = useState<LocalSettings>(ThreadMeshStorage.getSettings());

  // Modals state
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [replyTarget, setReplyTarget] = useState<{ id: string; preview: string } | null>(null);
  const [stanceTarget, setStanceTarget] = useState<{ id: string; preview: string } | null>(null);
  const [showCodeExplorer, setShowCodeExplorer] = useState(false);
  const [isAirplaneMode, setIsAirplaneMode] = useState(true);

  // Load and refresh state from persistent storage
  const refreshData = () => {
    const list = ThreadMeshStorage.getThreadListItems();
    setThreads(list);
    setRecentChanges(ThreadMeshStorage.getRecentChanges());

    if (selectedThreadId) {
      const tree = ThreadMeshStorage.buildTree(selectedThreadId);
      setCurrentTree(tree);
    }
  };

  useEffect(() => {
    refreshData();
  }, [selectedThreadId]);

  const handleSelectThread = (threadId: string) => {
    ThreadMeshStorage.markThreadViewed(threadId);
    setSelectedThreadId(threadId);
    const tree = ThreadMeshStorage.buildTree(threadId);
    setCurrentTree(tree);
    setRecentChanges(ThreadMeshStorage.getRecentChanges());
  };

  const handleBackFromDetail = () => {
    setSelectedThreadId(null);
    setCurrentTree(null);
    refreshData();
  };

  const handleSeedDemo = () => {
    ThreadMeshStorage.seedDemoData();
    refreshData();
  };

  const handleCreateTopic = (text: string, scope: PublishScope) => {
    const res = ThreadMeshStorage.createRootTopic(text, scope, settings.localAuthorId, settings.displayName);
    if (res.success) {
      refreshData();
      if (res.event) {
        handleSelectThread(res.event.id);
      }
    }
    return res;
  };

  const handleAddReply = (text: string, scope: PublishScope) => {
    if (!replyTarget) return { success: false, error: 'والد مشخص نشده است.' };
    const res = ThreadMeshStorage.addReply(replyTarget.id, text, scope, settings.localAuthorId, settings.displayName);
    if (res.success) {
      refreshData();
    }
    return res;
  };

  const handleRecordStanceChange = (targetEventId: string, explanation: string, scope: PublishScope) => {
    const res = ThreadMeshStorage.recordStanceChange(
      targetEventId,
      explanation,
      scope,
      settings.localAuthorId,
      settings.displayName
    );
    if (res.success) {
      refreshData();
    }
    return res;
  };

  const handleUpdateSettings = (newSettings: LocalSettings) => {
    ThreadMeshStorage.saveSettings(newSettings);
    setSettings(newSettings);
  };

  return (
    <div className="min-h-screen bg-slate-900 flex flex-col items-center justify-center p-0 sm:p-4 text-slate-900 select-none">
      {/* Top Banner with Workspace Controls */}
      <div className="w-full max-w-md bg-slate-800 text-slate-200 px-4 py-2.5 rounded-t-2xl sm:rounded-2xl mb-0 sm:mb-3 flex items-center justify-between text-xs border border-slate-700/60 shadow-lg">
        <div className="flex items-center gap-2">
          <span className="w-2.5 h-2.5 rounded-full bg-emerald-400"></span>
          <span className="font-bold">ThreadMesh (فاز ۱ - بومی/آفلاین)</span>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setIsAirplaneMode(!isAirplaneMode)}
            className={`flex items-center gap-1 px-2.5 py-1 rounded-md transition ${
              isAirplaneMode ? 'bg-indigo-600 text-white font-medium' : 'bg-slate-700 text-slate-300'
            }`}
            title="تغییر شبیه‌سازی وضعیت پرواز (Airplane Mode)"
          >
            <Plane className="w-3.5 h-3.5" />
            <span>حالت پرواز: {isAirplaneMode ? 'روشن' : 'خاموش'}</span>
          </button>

          <button
            onClick={() => setShowCodeExplorer(true)}
            className="flex items-center gap-1 px-2.5 py-1 bg-slate-700 hover:bg-slate-600 text-indigo-300 rounded-md transition font-medium"
            title="مشاهده کدهای کاتلین و ساختار Android پروژه"
          >
            <Code2 className="w-3.5 h-3.5" />
            <span>فایل‌های بومی Kotlin</span>
          </button>
        </div>
      </div>

      {/* Android Device Mockup Frame */}
      <div className="w-full max-w-md h-[100dvh] sm:h-[820px] bg-slate-950 sm:rounded-[36px] shadow-2xl overflow-hidden flex flex-col border sm:border-8 border-slate-800 relative">
        {/* Android Status Bar */}
        <div className="bg-white text-slate-800 px-6 pt-3 pb-1.5 flex items-center justify-between text-[11px] font-mono shrink-0 select-none border-b border-slate-100">
          <span className="font-bold">
            {new Date().toLocaleTimeString('fa-IR', { hour: '2-digit', minute: '2-digit' })}
          </span>
          <div className="flex items-center gap-2">
            {isAirplaneMode ? (
              <span className="flex items-center gap-1 text-slate-600 font-sans text-[10px]">
                <Plane className="w-3 h-3 text-slate-500" />
                آفلاین
              </span>
            ) : (
              <WifiOff className="w-3.5 h-3.5 text-slate-400" />
            )}
            <div className="w-5 h-2.5 border border-slate-400 rounded-sm p-0.5 flex items-center">
              <div className="h-full bg-slate-700 w-4/5 rounded-xs"></div>
            </div>
          </div>
        </div>

        {/* Dynamic Screen Area */}
        <div className="flex-1 flex flex-col overflow-hidden relative">
          {selectedThreadId && currentTree ? (
            <ThreadDetailScreen
              tree={currentTree}
              onBack={handleBackFromDetail}
              onReply={(id, preview) => setReplyTarget({ id, preview })}
              onStanceChange={(id, preview) => setStanceTarget({ id, preview })}
            />
          ) : (
            <>
              {activeTab === 'threads' && (
                <ThreadListScreen
                  threads={threads}
                  onSelectThread={handleSelectThread}
                  onCreateThread={() => setIsCreateModalOpen(true)}
                  onSeedDemo={handleSeedDemo}
                />
              )}
              {activeTab === 'recent' && (
                <RecentChangesScreen
                  items={recentChanges}
                  onSelectThread={handleSelectThread}
                />
              )}
              {activeTab === 'status' && <ConnectionStatusScreen onRefreshData={refreshData} />}
              {activeTab === 'settings' && (
                <SettingsScreen
                  settings={settings}
                  onUpdateSettings={handleUpdateSettings}
                />
              )}
            </>
          )}
        </div>

        {/* Android Material 3 Bottom Navigation Bar */}
        {!selectedThreadId && (
          <nav className="bg-white border-t border-slate-200 px-2 py-1.5 flex items-center justify-around shrink-0 shadow-lg">
            <button
              onClick={() => setActiveTab('threads')}
              className={`flex flex-col items-center gap-1 py-1 px-3 rounded-xl transition ${
                activeTab === 'threads' ? 'text-indigo-600 font-bold' : 'text-slate-400 hover:text-slate-600'
              }`}
            >
              <div
                className={`p-1 rounded-full ${activeTab === 'threads' ? 'bg-indigo-50 text-indigo-600' : ''}`}
              >
                <MessageSquare className="w-5 h-5" />
              </div>
              <span className="text-[11px]">گفتگوها</span>
            </button>

            <button
              onClick={() => setActiveTab('recent')}
              className={`flex flex-col items-center gap-1 py-1 px-3 rounded-xl transition relative ${
                activeTab === 'recent' ? 'text-indigo-600 font-bold' : 'text-slate-400 hover:text-slate-600'
              }`}
            >
              <div
                className={`p-1 rounded-full ${activeTab === 'recent' ? 'bg-indigo-50 text-indigo-600' : ''}`}
              >
                <History className="w-5 h-5" />
              </div>
              <span className="text-[11px]">تغییرات جدید</span>
              {recentChanges.length > 0 && (
                <span className="absolute top-1 right-3 w-2 h-2 rounded-full bg-amber-500 ring-2 ring-white"></span>
              )}
            </button>

            <button
              onClick={() => setActiveTab('status')}
              className={`flex flex-col items-center gap-1 py-1 px-3 rounded-xl transition ${
                activeTab === 'status' ? 'text-indigo-600 font-bold' : 'text-slate-400 hover:text-slate-600'
              }`}
            >
              <div
                className={`p-1 rounded-full ${activeTab === 'status' ? 'bg-indigo-50 text-indigo-600' : ''}`}
              >
                <Radio className="w-5 h-5" />
              </div>
              <span className="text-[11px]">مش نزدیک</span>
            </button>

            <button
              onClick={() => setActiveTab('settings')}
              className={`flex flex-col items-center gap-1 py-1 px-3 rounded-xl transition ${
                activeTab === 'settings' ? 'text-indigo-600 font-bold' : 'text-slate-400 hover:text-slate-600'
              }`}
            >
              <div
                className={`p-1 rounded-full ${activeTab === 'settings' ? 'bg-indigo-50 text-indigo-600' : ''}`}
              >
                <Settings className="w-5 h-5" />
              </div>
              <span className="text-[11px]">تنظیمات</span>
            </button>
          </nav>
        )}
      </div>

      {/* Modals */}
      <CreatePostModal
        isOpen={isCreateModalOpen}
        onClose={() => setIsCreateModalOpen(false)}
        onSubmit={(text, scope) => handleCreateTopic(text, scope)}
        isReply={false}
      />

      <CreatePostModal
        isOpen={!!replyTarget}
        onClose={() => setReplyTarget(null)}
        onSubmit={(text, scope) => handleAddReply(text, scope)}
        parentPreview={replyTarget?.preview}
        isReply={true}
      />

      <StanceChangeModal
        isOpen={!!stanceTarget}
        onClose={() => setStanceTarget(null)}
        targetEventId={stanceTarget?.id || ''}
        targetPreview={stanceTarget?.preview || ''}
        onSubmit={(id, explanation, scope) => handleRecordStanceChange(id, explanation, scope)}
      />

      {showCodeExplorer && <AndroidCodeExplorer onClose={() => setShowCodeExplorer(false)} />}
    </div>
  );
}
