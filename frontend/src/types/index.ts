/** Estados da aplicacao (secao 18 do MVP; estendida na Sprint 5). */
export type AppState =
  | 'INITIAL'
  | 'ROOM_CREATING'
  | 'DEVICE_SETUP'
  | 'CONNECTING'
  | 'CONNECTED'
  | 'DISCONNECTED'
  | 'ERROR'
  | 'ROOM_EXPIRED';

export interface DeviceOption {
  deviceId: string;
  label: string;
}

export interface MediaDevicesState {
  cameras: DeviceOption[];
  microphones: DeviceOption[];
}

export interface TokenResponse {
  token: string;
  roomId: string;
  participantId: string;
  /** URL wss:// do signaling/media plane do provider ativo (Sprint 11 §6). */
  serverUrl: string;
}

export type RoomStatus = 'WAITING' | 'ACTIVE' | 'ENDED' | 'EXPIRED';
/** Status resumido exposto na API/UI (Sprint 9 §7). */
export type RoomDisplayStatus = 'LIVE' | 'IDLE' | 'ENDED';
export type RoomCreationMode = 'DIRECT' | 'PROFILE' | 'OCCURRENCE';

export interface RoomResponse {
  id: string;
  roomId: string;
  /** null para salas diretas de infraestrutura (Sprint 9 §6). */
  name: string | null;
  status: RoomStatus;
  displayStatus: RoomDisplayStatus;
  creationMode: RoomCreationMode;
  roomProfileId: string | null;
  /** Nome/tipo do Profile de origem — presentes no histórico (Sprint 6 §10). */
  roomProfileName: string | null;
  roomProfileType: RoomType | null;
  /** Quem criou a sala (Sprint 7 §42) — uma Organization tem vários criadores. */
  ownerId: string;
  ownerName: string | null;
  durationMinutes: number | null;
  createdAt: string;
  startedAt: string | null;
  endedAt: string | null;
  expiresAt: string | null;
  /** Pessoas com sessao aberta agora nesta sala. So vem preenchido na listagem. */
  connectedCount: number;
}

/** GET /rooms/{roomId}/participants (Sprint 9 §10) — identidades agregadas. */
export interface RoomParticipant {
  participantRef: string;
  kind: 'USER' | 'GUEST';
  displayName: string;
  totalSessions: number;
  totalConnectedSeconds: number;
  reconnections: number;
  currentSessionOpen: boolean;
  firstSeenAt: string;
  lastSeenAt: string;
  latestQualityLevel: QualityLevel | null;
}

/** GET /rooms/{roomId}/participants/{ref} (Sprint 9 §11). */
export interface ParticipantAnalytics {
  participantRef: string;
  kind: 'USER' | 'GUEST';
  displayName: string;
  currentSession: { sessionId: string; joinedAt: string; connectedSeconds: number } | null;
  history: { totalSessions: number; totalConnectedSeconds: number; reconnections: number };
  quality: { average: QualityLevel; current: QualityLevel } | null;
  sessions: Array<{
    sessionId: string;
    joinedAt: string;
    leftAt: string | null;
    durationSeconds: number | null;
    reconnectCount: number;
  }>;
}

/** GET /rooms/{roomId}/events (Sprint 9 §14). */
export interface RoomEvent {
  type: string;
  at: string;
  participantRef: string | null;
  detail: string | null;
}

/** Envelope de paginação estável da API (Sprint 6 §9). */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface RoomHistoryFilters {
  status?: RoomStatus;
  roomProfileId?: string;
  createdFrom?: string;
  createdTo?: string;
  page?: number;
  size?: number;
}

export type QualityLevel = 'UNKNOWN' | 'POOR' | 'UNSTABLE' | 'GOOD' | 'EXCELLENT';

/** GET /rooms/{roomId}/analytics (Sprint 6 §17). */
export interface RoomAnalytics {
  roomId: string;
  durationSeconds: number | null;
  participants: number;
  peakParticipants: number;
  totalParticipantSeconds: number;
  quality: {
    avgRttMs: number | null;
    avgPacketLossPercent: number | null;
    totalReconnects: number;
    levelDistribution: Record<string, number>;
  } | null;
}

/** GET /rooms/{roomId}/sessions (Sprint 6 §19). */
export interface ParticipantSessionResponse {
  sessionId: string;
  participantId: string;
  participantName: string;
  joinedAt: string;
  leftAt: string | null;
  durationSeconds: number | null;
  reconnectCount: number;
}

/** GET /rooms/{roomId}/participants/summary (Sprint 6 §30). */
export interface ParticipantSummary {
  participantId: string;
  participantName: string;
  isGuest: boolean;
  sessions: number;
  completedSessions: number;
  reconnects: number;
  totalDurationSeconds: number;
  firstJoinedAt: string | null;
  lastLeftAt: string | null;
}

export interface RoomParticipantsSummary {
  distinctParticipants: number;
  totalSessions: number;
  totalEntries: number;
  totalParticipantSeconds: number;
  participants: ParticipantSummary[];
}

/** GET /rooms/{roomId}/quality (Sprint 6 §21-24). */
export interface RoomQuality {
  hasData: boolean;
  timelineAvailable: boolean;
  participants: Array<{
    participantId: string;
    participantName: string;
    isGuest: boolean;
    /** null = participante sem nenhuma medição de qualidade (ex.: convidado). */
    latestLevel: QualityLevel | null;
    rttMs: number | null;
    packetLossPercent: number | null;
    jitterMs: number | null;
    lastRecordedAt: string | null;
    snapshots: number;
  }>;
}

/** GET /rooms/summary (Sprint 6 §38). */
export interface RoomDashboard {
  roomsToday: number;
  roomsThisWeek: number;
  totalRooms: number;
  totalCallSeconds: number;
  distinctParticipants: number;
}

/** GET /room-profiles/{id}/summary (Sprint 6 §39). */
export interface RoomProfileSummary {
  roomProfileId: string;
  roomsTotal: number;
  totalCallSeconds: number;
  participations: number;
}

export type RoomType = 'LESSON' | 'CONSULTATION' | 'MEETING' | 'INTERVIEW' | 'OTHER';

export interface RoomProfile {
  id: string;
  name: string;
  durationMinutes: number;
  type: RoomType;
  createdAt: string;
  updatedAt: string;
}

export interface RoomProfileInput {
  name: string;
  durationMinutes: number;
  type: RoomType;
}

// ---- Appointments (Sprint 8) ----

export type RecurrenceType = 'NONE' | 'WEEKLY';
export type AppointmentStatusValue = 'ACTIVE' | 'CANCELLED';

/** GET/POST /api/v1/appointments (§23, §25). */
export interface Appointment {
  id: string;
  title: string;
  participantName: string | null;
  publicAccessId: string;
  joinUrl: string;
  roomProfileId: string;
  durationMinutes: number;
  timezone: string;
  startsAt: string;
  recurrenceType: RecurrenceType;
  recurrenceDayOfWeek: string | null;
  recurrenceUntil: string | null;
  status: AppointmentStatusValue;
  nextOccurrence: string | null;
}

export interface AppointmentInput {
  roomProfileId: string;
  title: string;
  participantName?: string | null;
  startsAt: string;
  timezone: string;
  recurrence?: { type: RecurrenceType; dayOfWeek?: string | null; until?: string | null } | null;
}

export type AppointmentOccurrenceStatus =
  | 'SCHEDULED'
  | 'WAITING'
  | 'ACTIVE'
  | 'ENDED'
  | 'EXPIRED'
  | 'CANCELLED';

export interface AppointmentOccurrence {
  id: string;
  scheduledStart: string;
  scheduledEnd: string;
  status: AppointmentOccurrenceStatus;
  roomId: string | null;
}

/** GET /api/v1/public/appointments/{publicAccessId} (§32) — payload minimo. */
export type PublicAppointmentState =
  | 'BEFORE_WINDOW'
  | 'WAITING_ROOM'
  | 'JOINABLE'
  | 'ENDED'
  | 'CANCELLED';

export interface PublicAppointment {
  title: string;
  participantName: string | null;
  state: PublicAppointmentState;
  scheduledStart: string | null;
  scheduledEnd: string | null;
  joinWindowOpensAt: string | null;
  roomId: string | null;
  nextOccurrenceStart: string | null;
}

export interface JoinConfig {
  roomId: string;
  cameraId?: string;
  microphoneId?: string;
  cameraEnabled: boolean;
  microphoneEnabled: boolean;
}

/** Mensagem de erro amigavel + codigo interno para log/depuracao. */
export interface FriendlyError {
  code: string;
  message: string;
}

export interface AuthUser {
  id: string;
  name: string;
  email: string;
}

// ---- Organizations (Sprint 7) ----

export type OrgRole = 'OWNER' | 'ADMIN' | 'MEMBER';

/** GET /api/v1/organizations/current (Sprint 7 §28). */
export interface Organization {
  id: string;
  name: string;
  slug: string;
  /** Papel do usuário autenticado nesta Organization. */
  role: OrgRole;
}

/** Um membro em GET /api/v1/organizations/current/members (Sprint 7 §30). */
export interface OrgMember {
  userId: string;
  name: string;
  email: string;
  role: OrgRole;
  memberSince: string;
}
