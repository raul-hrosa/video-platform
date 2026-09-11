import { config } from '../config';
import type {
  Appointment,
  AppointmentInput,
  AppointmentOccurrence,
  AuthUser,
  Organization,
  PublicAppointment,
  OrgMember,
  PageResponse,
  ParticipantSessionResponse,
  RoomAnalytics,
  RoomDashboard,
  RoomHistoryFilters,
  RoomParticipantsSummary,
  RoomProfile,
  RoomProfileInput,
  RoomProfileSummary,
  RoomQuality,
  RoomResponse,
  RoomParticipant,
  ParticipantAnalytics,
  RoomEvent,
  TokenResponse,
} from '../types';
import type { ConnectionMetrics } from '../types/connectionQuality';
import { clearToken, getToken } from './authStorage';

interface SessionResponse {
  sessionId: string;
  participantId: string;
}

export interface QualitySnapshotPayload extends ConnectionMetrics {
  participantId: string;
  qualityLevel: string;
  reconnectCount: number;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export class ApiError extends Error {
  constructor(
    public code: string,
    message: string,
  ) {
    super(message);
  }
}

const PUBLIC_PATHS = ['/v1/auth/login', '/v1/auth/register'];

let onUnauthorizedHandler: (() => void) | null = null;

/** Registra o callback chamado quando a API responde 401 (sessao expirada). */
export function onUnauthorized(handler: () => void): void {
  onUnauthorizedHandler = handler;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(init?.headers as Record<string, string> | undefined),
  };
  const token = getToken();
  if (token && !PUBLIC_PATHS.some((p) => path.startsWith(p))) {
    headers.Authorization = `Bearer ${token}`;
  }

  let response: Response;
  try {
    response = await fetch(`${config.apiBaseUrl}${path}`, { ...init, headers });
  } catch {
    throw new ApiError('NETWORK_ERROR', 'Nao foi possivel falar com o servidor.');
  }

  if (response.status === 401 && !PUBLIC_PATHS.some((p) => path.startsWith(p))) {
    clearToken();
    onUnauthorizedHandler?.();
    const body = await response.json().catch(() => null);
    throw new ApiError(
      body?.code ?? 'UNAUTHORIZED',
      'Sua sessao expirou. Faca login novamente.',
    );
  }

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new ApiError(
      body?.code ?? body?.error ?? 'REQUEST_FAILED',
      body?.message ?? 'A requisicao falhou.',
    );
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

// ---- auth ----

export function register(name: string, email: string, password: string): Promise<AuthUser> {
  return request<AuthUser>('/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify({ name, email, password }),
  });
}

export function login(email: string, password: string): Promise<LoginResponse> {
  return request<LoginResponse>('/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
}

export function me(): Promise<AuthUser> {
  return request<AuthUser>('/v1/auth/me');
}

// ---- organization (Sprint 7) ----

export function getCurrentOrganization(): Promise<Organization> {
  return request<Organization>('/v1/organizations/current');
}

export function updateOrganizationName(name: string): Promise<Organization> {
  return request<Organization>('/v1/organizations/current', {
    method: 'PUT',
    body: JSON.stringify({ name }),
  });
}

interface MemberListResponse {
  content: OrgMember[];
}

export async function listOrgMembers(): Promise<OrgMember[]> {
  const res = await request<MemberListResponse>('/v1/organizations/current/members');
  return res.content;
}

// ---- room profiles ----

interface RoomProfileListResponse {
  content: RoomProfile[];
}

export async function listRoomProfiles(): Promise<RoomProfile[]> {
  const res = await request<RoomProfileListResponse>('/v1/room-profiles');
  return res.content;
}

export function getRoomProfile(id: string): Promise<RoomProfile> {
  return request<RoomProfile>(`/v1/room-profiles/${encodeURIComponent(id)}`);
}

export function createRoomProfile(input: RoomProfileInput): Promise<RoomProfile> {
  return request<RoomProfile>('/v1/room-profiles', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function updateRoomProfile(id: string, input: RoomProfileInput): Promise<RoomProfile> {
  return request<RoomProfile>(`/v1/room-profiles/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  });
}

export function deleteRoomProfile(id: string): Promise<void> {
  return request<void>(`/v1/room-profiles/${encodeURIComponent(id)}`, { method: 'DELETE' });
}

// ---- rooms ----

/** Cria uma Room a partir de um Profile. Nao retorna token — ver fetchRoomToken. */
export function createRoomFromProfile(profileId: string): Promise<RoomResponse> {
  return request<RoomResponse>(`/v1/room-profiles/${encodeURIComponent(profileId)}/rooms`, {
    method: 'POST',
  });
}

/** Cria uma Room de infraestrutura direta (Sprint 9 §6): sem profile, sem nome. */
export function createDirectRoom(): Promise<RoomResponse> {
  return request<RoomResponse>('/v1/rooms', { method: 'POST' });
}

// ---- room history & analytics (Sprint 6) ----

/** Histórico paginado de salas do usuário. Ordenação padrão: createdAt DESC. */
export function listRooms(filters: RoomHistoryFilters = {}): Promise<PageResponse<RoomResponse>> {
  const q = new URLSearchParams();
  if (filters.status) q.set('status', filters.status);
  if (filters.roomProfileId) q.set('roomProfileId', filters.roomProfileId);
  if (filters.createdFrom) q.set('createdFrom', filters.createdFrom);
  if (filters.createdTo) q.set('createdTo', filters.createdTo);
  q.set('page', String(filters.page ?? 0));
  q.set('size', String(filters.size ?? 20));
  return request<PageResponse<RoomResponse>>(`/v1/rooms?${q.toString()}`);
}

export function getRoom(roomId: string): Promise<RoomResponse> {
  return request<RoomResponse>(`/v1/rooms/${encodeURIComponent(roomId)}`);
}

export function getRoomAnalytics(roomId: string): Promise<RoomAnalytics> {
  return request<RoomAnalytics>(`/v1/rooms/${encodeURIComponent(roomId)}/analytics`);
}

export function getRoomSessions(roomId: string): Promise<ParticipantSessionResponse[]> {
  return request<ParticipantSessionResponse[]>(`/v1/rooms/${encodeURIComponent(roomId)}/sessions`);
}

export function getParticipantsSummary(roomId: string): Promise<RoomParticipantsSummary> {
  return request<RoomParticipantsSummary>(
    `/v1/rooms/${encodeURIComponent(roomId)}/participants/summary`,
  );
}

/** Identidades de participante da sala, agregadas (Sprint 9 §10). */
export function getRoomParticipants(roomId: string): Promise<RoomParticipant[]> {
  return request<RoomParticipant[]>(`/v1/rooms/${encodeURIComponent(roomId)}/participants`);
}

/** Analytics detalhado de um participante (Sprint 9 §11). */
export function getParticipantAnalytics(
  roomId: string,
  participantRef: string,
): Promise<ParticipantAnalytics> {
  return request<ParticipantAnalytics>(
    `/v1/rooms/${encodeURIComponent(roomId)}/participants/${encodeURIComponent(participantRef)}`,
  );
}

/** Linha do tempo de eventos da sala (Sprint 9 §14). */
export function getRoomEvents(roomId: string): Promise<RoomEvent[]> {
  return request<RoomEvent[]>(`/v1/rooms/${encodeURIComponent(roomId)}/events`);
}

export function getRoomQuality(roomId: string): Promise<RoomQuality> {
  return request<RoomQuality>(`/v1/rooms/${encodeURIComponent(roomId)}/quality`);
}

export function getRoomDashboard(todayStart: string, weekStart: string): Promise<RoomDashboard> {
  const q = new URLSearchParams({ todayStart, weekStart });
  return request<RoomDashboard>(`/v1/rooms/summary?${q.toString()}`);
}

export function getProfileSummary(profileId: string): Promise<RoomProfileSummary> {
  return request<RoomProfileSummary>(
    `/v1/room-profiles/${encodeURIComponent(profileId)}/summary`,
  );
}

/** Token de acesso do LiveKit. A identidade vem do usuario autenticado. */
export function fetchRoomToken(roomId: string): Promise<TokenResponse> {
  return request<TokenResponse>(`/v1/rooms/${encodeURIComponent(roomId)}/token`, {
    method: 'POST',
  });
}

/**
 * Nomes de exibicao por identidade dos participantes da sala (Sprint 11). Rota
 * de leitura publica: o provider de midia nao propaga o nome dos outros, entao
 * o cliente na chamada casa o prefixo `<sub>` da identidade `<sub>.<8hex>`.
 */
export function fetchParticipantNames(roomId: string): Promise<Record<string, string>> {
  return request<Record<string, string>>(
    `/v1/rooms/${encodeURIComponent(roomId)}/participant-names`,
  );
}

/**
 * Qualidade ao vivo por participante, calculada pela Quality Engine do provider
 * de mídia (Sprint 12 §16). Rota de leitura pública; só existe com PulseRTC —
 * com LiveKit responde 404 e o cliente ignora.
 */
export type QualityScale = 'EXCELLENT' | 'GOOD' | 'UNSTABLE' | 'POOR' | 'UNKNOWN';

export interface StreamQuality {
  level: QualityScale;
  score?: number | null;
  reason?: string | null;
  metrics?: Record<string, unknown> | null;
}

export interface MediaQualityRow {
  participantRef: string;
  level: QualityScale;
  score?: number | null;
  reason?: string | null;
  metrics?: Record<string, unknown> | null;
  audio?: StreamQuality | null;
  video?: StreamQuality | null;
  connection?: StreamQuality | null;
}

export function fetchMediaQuality(roomId: string): Promise<MediaQualityRow[]> {
  return request<MediaQualityRow[]>(`/v1/rooms/${encodeURIComponent(roomId)}/media-quality`);
}

/** Token de visitante sem conta (rota publica). Identidade = `guest:{uuid}` do backend. */
export function fetchGuestToken(roomId: string, name: string): Promise<TokenResponse> {
  return request<TokenResponse>(`/v1/rooms/${encodeURIComponent(roomId)}/guest-token`, {
    method: 'POST',
    body: JSON.stringify({ name }),
  });
}

/** Resolve a propria ParticipantSession aberta do usuario na sala. */
export async function findMySession(roomId: string): Promise<SessionResponse | null> {
  try {
    return await request<SessionResponse>(
      `/v1/rooms/${encodeURIComponent(roomId)}/sessions/mine`,
    );
  } catch (err) {
    if (err instanceof ApiError && err.code === 'SESSION_NOT_FOUND') return null;
    throw err;
  }
}

// ---- appointments (Sprint 8) ----

export async function listAppointments(): Promise<Appointment[]> {
  const res = await request<{ content: Appointment[] }>('/v1/appointments');
  return res.content;
}

export function getAppointment(id: string): Promise<Appointment> {
  return request<Appointment>(`/v1/appointments/${encodeURIComponent(id)}`);
}

export function createAppointment(input: AppointmentInput): Promise<Appointment> {
  return request<Appointment>('/v1/appointments', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function updateAppointment(
  id: string,
  input: Omit<AppointmentInput, 'roomProfileId'>,
): Promise<Appointment> {
  return request<Appointment>(`/v1/appointments/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  });
}

export function cancelAppointment(id: string): Promise<void> {
  return request<void>(`/v1/appointments/${encodeURIComponent(id)}/cancel`, { method: 'POST' });
}

export async function listAppointmentOccurrences(id: string): Promise<AppointmentOccurrence[]> {
  const res = await request<{ content: AppointmentOccurrence[] }>(
    `/v1/appointments/${encodeURIComponent(id)}/occurrences`,
  );
  return res.content;
}

/** Link publico — rota aberta, so participacao (§32). */
export function getPublicAppointment(publicAccessId: string): Promise<PublicAppointment> {
  return request<PublicAppointment>(
    `/v1/public/appointments/${encodeURIComponent(publicAccessId)}`,
  );
}

/** Token de visitante pelo link do atendimento. Identidade = `guest:{uuid}` do backend. */
export function fetchAppointmentToken(publicAccessId: string, name: string): Promise<TokenResponse> {
  return request<TokenResponse>(
    `/v1/public/appointments/${encodeURIComponent(publicAccessId)}/token`,
    { method: 'POST', body: JSON.stringify(name ? { name } : {}) },
  );
}

/** Envia um snapshot de qualidade. Nunca inclui token/credenciais. */
export function postQualitySnapshot(
  roomId: string,
  sessionId: string,
  payload: QualitySnapshotPayload,
): Promise<{ id: string; qualityLevel: string }> {
  return request(
    `/v1/rooms/${encodeURIComponent(roomId)}/sessions/${encodeURIComponent(sessionId)}/quality`,
    { method: 'POST', body: JSON.stringify(payload) },
  );
}
