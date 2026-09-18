/**
 * Logger simples do frontend. Uso: logger.info({ event: 'LIVEKIT_CONNECTED', roomId }).
 *
 * - saida via console (nao envia nada para o backend nesta fase)
 * - debug so aparece em desenvolvimento
 * - nunca deve receber token/credenciais (o chamador nao passa esses campos)
 */
type Level = 'debug' | 'info' | 'warn' | 'error';

export interface LogFields {
  event: string;
  [key: string]: unknown;
}

const isDev = import.meta.env.DEV;

function emit(level: Level, fields: LogFields): void {
  if (level === 'debug' && !isDev) return;
  const line = { ts: new Date().toISOString(), level, ...fields };
  // eslint-disable-next-line no-console
  console[level](`[${fields.event}]`, line);
}

export const logger = {
  debug: (fields: LogFields) => emit('debug', fields),
  info: (fields: LogFields) => emit('info', fields),
  warn: (fields: LogFields) => emit('warn', fields),
  error: (fields: LogFields) => emit('error', fields),
};

export const LogEvent = {
  VIDEO_PERMISSION_GRANTED: 'VIDEO_DEVICE_PERMISSION_GRANTED',
  VIDEO_PERMISSION_DENIED: 'VIDEO_DEVICE_PERMISSION_DENIED',
  AUDIO_PERMISSION_GRANTED: 'AUDIO_DEVICE_PERMISSION_GRANTED',
  AUDIO_PERMISSION_DENIED: 'AUDIO_DEVICE_PERMISSION_DENIED',
  DEVICE_SELECTED: 'DEVICE_SELECTED',
  LIVEKIT_TOKEN_REQUESTED: 'LIVEKIT_TOKEN_REQUESTED',
  LIVEKIT_CONNECTING: 'LIVEKIT_CONNECTING',
  LIVEKIT_CONNECTED: 'LIVEKIT_CONNECTED',
  LIVEKIT_DISCONNECTED: 'LIVEKIT_DISCONNECTED',
  LIVEKIT_CONNECTION_ERROR: 'LIVEKIT_CONNECTION_ERROR',
  PARTICIPANT_RECONNECTING: 'PARTICIPANT_RECONNECTING',
  PARTICIPANT_RECONNECTED: 'PARTICIPANT_RECONNECTED',
  SCREEN_SHARE_STARTED: 'SCREEN_SHARE_STARTED',
  SCREEN_SHARE_STOPPED: 'SCREEN_SHARE_STOPPED',
  QUALITY_REPORTER_STARTED: 'QUALITY_REPORTER_STARTED',
  QUALITY_REPORTER_STOPPED: 'QUALITY_REPORTER_STOPPED',
  QUALITY_REPORT_FAILED: 'QUALITY_REPORT_FAILED',
  QUALITY_STATE_CHANGED: 'QUALITY_STATE_CHANGED',
  ROOM_CREATED: 'ROOM_CREATED',
  ROOM_PROFILE_CREATED: 'ROOM_PROFILE_CREATED',
  ROOM_PROFILE_UPDATED: 'ROOM_PROFILE_UPDATED',
  ROOM_PROFILE_DELETED: 'ROOM_PROFILE_DELETED',
  ROOM_CREATED_FROM_PROFILE: 'ROOM_CREATED_FROM_PROFILE',
  ROOM_LINK_COPIED: 'ROOM_LINK_COPIED',
  ROOM_EXPIRED_VIEW: 'ROOM_EXPIRED_VIEW',
  CONNECTION_MONITOR_STARTED: 'CONNECTION_MONITOR_STARTED',
  CONNECTION_MONITOR_STOPPED: 'CONNECTION_MONITOR_STOPPED',
  CONNECTION_QUALITY_CHANGED: 'CONNECTION_QUALITY_CHANGED',
  CONNECTION_METRICS_ERROR: 'CONNECTION_METRICS_ERROR',
  LOGIN_SUCCESS: 'LOGIN_SUCCESS',
  LOGIN_FAILED: 'LOGIN_FAILED',
  LOGOUT: 'LOGOUT',
  AUTH_SESSION_EXPIRED: 'AUTH_SESSION_EXPIRED',
} as const;
