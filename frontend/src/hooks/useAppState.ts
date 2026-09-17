import { useCallback, useState } from 'react';
import { ApiError, createDirectRoom, createRoomFromProfile, fetchRoomToken } from '../services/api';
import { LogEvent, logger } from '../services/logger';
import type { AppState, FriendlyError, JoinConfig } from '../types';

interface Session {
  token: string;
  serverUrl: string;
  participantId: string;
  joinConfig: JoinConfig;
}

/** Dados da sala recem-criada, para o banner "Sala criada / copiar link". */
interface CreatedRoom {
  roomId: string;
  name: string | null;
  expiresAt: string | null;
}

interface UseAppState {
  state: AppState;
  roomId: string | null;
  session: Session | null;
  createdRoom: CreatedRoom | null;
  error: FriendlyError | null;
  selectRoom: (roomId: string) => void;
  createRoom: (profileId: string) => Promise<void>;
  createDirectRoom: () => Promise<void>;
  join: (config: JoinConfig) => Promise<void>;
  leave: () => void;
  fail: (error: Error) => void;
  reset: () => void;
}

/**
 * Maquina de estados da aplicacao. Estendida na Sprint 5 com ROOM_CREATING
 * (criando a sala a partir de um Profile) e ROOM_EXPIRED (link de sala vencida).
 *
 * INITIAL       -> escolher um Profile ("Nova sala") ou colar um link
 * ROOM_CREATING -> POST /room-profiles/{id}/rooms em andamento
 * DEVICE_SETUP  -> preview e selecao de dispositivos
 * CONNECTING    -> pedindo token
 * CONNECTED     -> na chamada
 * DISCONNECTED / ERROR -> saiu ou falhou
 * ROOM_EXPIRED  -> tentou entrar numa sala expirada
 */
export function useAppState(serverUrl: string): UseAppState {
  const [state, setState] = useState<AppState>('INITIAL');
  const [roomId, setRoomId] = useState<string | null>(null);
  const [session, setSession] = useState<Session | null>(null);
  const [createdRoom, setCreatedRoom] = useState<CreatedRoom | null>(null);
  const [error, setError] = useState<FriendlyError | null>(null);

  const selectRoom = useCallback((selectedRoomId: string) => {
    setError(null);
    setCreatedRoom(null);
    setRoomId(selectedRoomId);
    setState('DEVICE_SETUP');
  }, []);

  const createRoom = useCallback(async (profileId: string) => {
    setError(null);
    setState('ROOM_CREATING');
    try {
      const room = await createRoomFromProfile(profileId);
      logger.info({ event: LogEvent.ROOM_CREATED_FROM_PROFILE, roomId: room.roomId, profileId });
      setRoomId(room.roomId);
      setCreatedRoom({ roomId: room.roomId, name: room.name, expiresAt: room.expiresAt });
      setState('DEVICE_SETUP');
    } catch (err) {
      const friendly: FriendlyError =
        err instanceof ApiError
          ? {
              code: err.code,
              message:
                err.code === 'ROOM_PROFILE_NOT_FOUND'
                  ? 'Perfil nao encontrado.'
                  : 'Nao foi possivel criar a sala.',
            }
          : { code: 'UNKNOWN', message: 'Nao foi possivel criar a sala.' };
      setError(friendly);
      setState('INITIAL');
    }
  }, []);

  const createDirect = useCallback(async () => {
    setError(null);
    setState('ROOM_CREATING');
    try {
      const room = await createDirectRoom();
      logger.info({ event: LogEvent.ROOM_CREATED_FROM_PROFILE, roomId: room.roomId });
      setRoomId(room.roomId);
      setCreatedRoom({ roomId: room.roomId, name: room.name, expiresAt: room.expiresAt });
      setState('DEVICE_SETUP');
    } catch (err) {
      const friendly: FriendlyError =
        err instanceof ApiError
          ? { code: err.code, message: 'Nao foi possivel criar a sala.' }
          : { code: 'UNKNOWN', message: 'Nao foi possivel criar a sala.' };
      setError(friendly);
      setState('INITIAL');
    }
  }, []);

  const join = useCallback(
    async (config: JoinConfig) => {
      setError(null);
      setState('CONNECTING');
      logger.info({ event: LogEvent.LIVEKIT_TOKEN_REQUESTED, roomId: config.roomId });
      try {
        const { token, participantId, serverUrl: tokenServerUrl } = await fetchRoomToken(config.roomId);
        setSession({ token, serverUrl: tokenServerUrl || serverUrl, participantId, joinConfig: config });
        setState('CONNECTED');
      } catch (err) {
        if (err instanceof ApiError && err.code === 'ROOM_EXPIRED') {
          logger.warn({ event: LogEvent.ROOM_EXPIRED_VIEW, roomId: config.roomId });
          setError(null);
          setState('ROOM_EXPIRED');
          return;
        }
        const friendly: FriendlyError =
          err instanceof ApiError
            ? { code: err.code, message: err.message }
            : { code: 'UNKNOWN', message: 'Nao foi possivel entrar na sala.' };
        logger.error({ event: LogEvent.LIVEKIT_CONNECTION_ERROR, roomId: config.roomId, code: friendly.code });
        setError(friendly);
        setState('DEVICE_SETUP');
      }
    },
    [serverUrl],
  );

  const leave = useCallback(() => {
    setSession(null);
    setState('DISCONNECTED');
  }, []);

  const fail = useCallback((err: Error) => {
    const isToken = err.name === 'TokenError';
    const message = isToken
      ? 'Sessao expirada. Volte e entre na sala novamente.'
      : 'Nao foi possivel conectar a sala. Verifique sua conexao com a internet e tente de novo.';
    setError({ code: 'CONNECTION_ERROR', message });
    setSession(null);
    setState('ERROR');
    logger.error({ event: LogEvent.LIVEKIT_CONNECTION_ERROR, message: err.message });
  }, []);

  const reset = useCallback(() => {
    setError(null);
    setSession(null);
    setRoomId(null);
    setCreatedRoom(null);
    setState('INITIAL');
    if (typeof window !== 'undefined' && window.location.pathname !== '/') {
      window.history.replaceState(null, '', '/');
    }
  }, []);

  return {
    state,
    roomId,
    session,
    createdRoom,
    error,
    selectRoom,
    createRoom,
    createDirectRoom: createDirect,
    join,
    leave,
    fail,
    reset,
  };
}
