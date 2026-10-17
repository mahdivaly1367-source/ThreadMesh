export type EventType = 'ROOT_THREAD' | 'REPLY' | 'STANCE_CHANGED';

export type PublishScope = 'LOCAL_ONLY' | 'MESH_PUBLIC' | 'GLOBAL_PUBLIC';

export interface EventEntity {
  id: string; // UUID
  localAuthorId: string;
  displayName: string;
  createdAt: number;
  eventType: EventType;
  rootThreadId: string;
  parentEventId: string | null;
  targetEventId: string | null; // For STANCE_CHANGED pointing to previous event
  text: string;
  publishScope: PublishScope;
  lastViewedAt?: number | null;
}

export interface TreeNode {
  event: EventEntity;
  depth: number;
  children: TreeNode[];
  stanceChanges: EventEntity[];
  isExpanded: boolean;
}

export interface ThreadListItem {
  rootEvent: EventEntity;
  replyCount: number;
  lastActivityTime: number;
  lastPreviewText: string;
  hasUnseenChanges: boolean;
}

export interface RecentChangeEventItem {
  event: EventEntity;
  rootEventTitle: string;
  parentPreviewText?: string;
}

export type PeerConnectionStatus =
  | 'DISCOVERED'
  | 'CONNECTING'
  | 'AUTHENTICATION_PENDING'
  | 'CONNECTED'
  | 'REJECTED'
  | 'DISCONNECTED';

export interface DiscoveredPeer {
  endpointId: string;
  endpointName: string;
  status: PeerConnectionStatus;
  authDigits?: string;
  isIncomingRequest?: boolean;
}

export type MeshServiceState = 'STOPPED' | 'STARTING' | 'RUNNING' | 'ERROR';

export const PUBLISH_SCOPE_LABELS: Record<PublishScope, { title: string; desc: string }> = {
  LOCAL_ONLY: {
    title: 'فقط محلی (LOCAL_ONLY)',
    desc: 'ذخیره صرفاً روی حافظه این دستگاه بدون انتشار به همتاها',
  },
  MESH_PUBLIC: {
    title: 'عمومی در مش (MESH_PUBLIC)',
    desc: 'قابل انتقال به دستگاه‌های نزدیک متصل با Nearby Connections (فعال)',
  },
  GLOBAL_PUBLIC: {
    title: 'عمومی سراسری (GLOBAL_PUBLIC)',
    desc: 'انتشار جهانی به کل اینترنت (انتقال در این فاز فعال نیست)',
  },
};

export const PHASE2_NOTICE_GLOBAL = 'انتقال جهانی در فازهای بعدی فعال خواهد شد';
