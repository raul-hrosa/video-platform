import { useState } from 'react';
import { ErrorNotice } from '../components/ErrorNotice';
import { config, configuredServerUrl } from '../config';
import { ApiError, fetchGuestToken } from '../services/api';
import { LogEvent, logger } from '../services/logger';
import type { FriendlyError, JoinConfig } from '../types';
import { CallRoom } from './CallRoom';
import { DeviceSetup } from './DeviceSetup';
import { RoomExpiredNotice } from './RoomExpiredNotice';

type State = 'SETUP' | 'CONNECTING' | 'CONNECTED' | 'LEFT' | 'ERROR' | 'ROOM_EXPIRED';

interface Session {
  token: string;
  participantId: string;
  serverUrl: string;
  joinConfig: JoinConfig;
}

/**
 * Fluxo de visitante sem conta: alguem abre `/room/{roomId}` deslogado, informa
 * um nome e entra na chamada. Gestao, perfis e persistencia de qualidade
 * continuam exigindo conta (Sprint 5 §63.6).
 */
export function GuestApp({ roomId }: { roomId: string }) {
  const [name, setName] = useState('');
  const [state, setState] = useState<State>('SETUP');
  const [session, setSession] = useState<Session | null>(null);
  const [error, setError] = useState<FriendlyError | null>(null);

  function backToStart() {
    window.history.replaceState(null, '', '/');
    window.location.reload();
  }

  async function join(joinConfig: JoinConfig) {
    setError(null);
    setState('CONNECTING');
    logger.info({ event: LogEvent.LIVEKIT_TOKEN_REQUESTED, roomId, guest: true });
    try {
      const { token, participantId, serverUrl } = await fetchGuestToken(roomId, name.trim());
      setSession({ token, participantId, serverUrl: serverUrl || configuredServerUrl(), joinConfig });
      setState('CONNECTED');
    } catch (err) {
      if (err instanceof ApiError && err.code === 'ROOM_EXPIRED') {
        logger.warn({ event: LogEvent.ROOM_EXPIRED_VIEW, roomId, guest: true });
        setState('ROOM_EXPIRED');
        return;
      }
      setError(
        err instanceof ApiError
          ? { code: err.code, message: err.message }
          : { code: 'UNKNOWN', message: 'Nao foi possivel entrar na sala.' },
      );
      setState('SETUP');
    }
  }

  if (config.mediaProvider === 'livekit' && !config.livekitUrl) {
    return (
      <div className="mx-auto max-w-md p-6">
        <ErrorNotice
          error={{ code: 'CONFIG_MISSING', message: 'VITE_LIVEKIT_URL nao foi configurada.' }}
        />
      </div>
    );
  }

  if (state === 'CONNECTED' && session) {
    return (
      <CallRoom
        serverUrl={session.serverUrl}
        token={session.token}
        participantId={session.participantId}
        joinConfig={session.joinConfig}
        resolveSession={false}
        onLeave={() => setState('LEFT')}
        onError={(err: Error) => {
          const isToken = err.name === 'TokenError';
          const message = isToken
            ? 'Sessao expirada. Volte e entre na sala novamente.'
            : 'Nao foi possivel conectar a sala. Verifique sua conexao com a internet e tente de novo.';
          setError({ code: 'CONNECTION_ERROR', message });
          setState('ERROR');
        }}
      />
    );
  }

  if (state === 'ROOM_EXPIRED') {
    return <RoomExpiredNotice onBack={backToStart} />;
  }

  if (state === 'LEFT' || state === 'ERROR') {
    return (
      <div className="mx-auto flex min-h-full max-w-md flex-col justify-center gap-4 p-6 text-center">
        <h1 className="text-xl font-semibold text-slate-100">
          {state === 'ERROR' ? 'A chamada foi encerrada' : 'Voce saiu da chamada'}
        </h1>
        {error && <ErrorNotice error={error} />}
        <button
          type="button"
          onClick={backToStart}
          className="rounded-lg bg-indigo-600 px-4 py-3 font-semibold text-white hover:bg-indigo-500"
        >
          Voltar
        </button>
      </div>
    );
  }

  return (
    <DeviceSetup
      roomId={roomId}
      userName={name}
      guestName={name}
      onGuestNameChange={setName}
      joining={state === 'CONNECTING'}
      joinError={error}
      onJoin={join}
      onBack={backToStart}
    />
  );
}
