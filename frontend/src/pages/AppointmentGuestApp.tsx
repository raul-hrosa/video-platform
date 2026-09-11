import { type ReactNode, useCallback, useEffect, useRef, useState } from 'react';
import { ErrorNotice } from '../components/ErrorNotice';
import { config, configuredServerUrl } from '../config';
import { ApiError, fetchAppointmentToken, getPublicAppointment } from '../services/api';
import { formatTime } from '../services/format';
import { LogEvent, logger } from '../services/logger';
import type { FriendlyError, JoinConfig, PublicAppointment } from '../types';
import { CallRoom } from './CallRoom';
import { DeviceSetup } from './DeviceSetup';

type Phase =
  | 'LOADING'
  | 'BEFORE_WINDOW'
  | 'CANCELLED'
  | 'ENDED'
  | 'SETUP'
  | 'PRECALL_WAIT'
  | 'CONNECTED'
  | 'CALL_ENDED'
  | 'ERROR';

interface Session {
  token: string;
  participantId: string;
  serverUrl: string;
  joinConfig: JoinConfig;
}

function phaseFor(info: PublicAppointment): Phase {
  switch (info.state) {
    case 'CANCELLED':
      return 'CANCELLED';
    case 'ENDED':
      return 'ENDED';
    case 'BEFORE_WINDOW':
      return 'BEFORE_WINDOW';
    default:
      return 'SETUP';
  }
}

/**
 * Fluxo do participante pelo link permanente do atendimento (§13, §28, §41-§43).
 * O mesmo link resolve automaticamente a ocorrencia correta e leva a pessoa para
 * "aguardando", sala de espera, chamada ou "chamada encerrada" conforme o horario.
 */
export function AppointmentGuestApp({ publicAccessId }: { publicAccessId: string }) {
  const [info, setInfo] = useState<PublicAppointment | null>(null);
  const [phase, setPhase] = useState<Phase>('LOADING');
  const [name, setName] = useState('');
  const [session, setSession] = useState<Session | null>(null);
  const [error, setError] = useState<FriendlyError | null>(null);
  const [nowMs, setNowMs] = useState(() => Date.now());
  const pendingJoin = useRef<JoinConfig | null>(null);

  const load = useCallback(async () => {
    try {
      const a = await getPublicAppointment(publicAccessId);
      setInfo(a);
      setName((prev) => prev || a.participantName || '');
      setPhase((prev) =>
        prev === 'LOADING' || prev === 'BEFORE_WINDOW' ? phaseFor(a) : prev,
      );
      return a;
    } catch (err) {
      setError(toFriendly(err, 'Nao foi possivel abrir o atendimento.'));
      setPhase('ERROR');
      return null;
    }
  }, [publicAccessId]);

  useEffect(() => {
    void load();
  }, [load]);

  // Relogio local enquanto aguarda (§15, §46) — sem WebSocket.
  useEffect(() => {
    if (phase !== 'BEFORE_WINDOW' && phase !== 'PRECALL_WAIT') return;
    const t = setInterval(() => setNowMs(Date.now()), 1000);
    return () => clearInterval(t);
  }, [phase]);

  // Antes da janela: re-checa o backend periodicamente ate a janela abrir.
  useEffect(() => {
    if (phase !== 'BEFORE_WINDOW') return;
    const opensAt = info?.joinWindowOpensAt ? new Date(info.joinWindowOpensAt).getTime() : null;
    if (opensAt != null && nowMs >= opensAt) void load();
    const t = setInterval(() => void load(), 15000);
    return () => clearInterval(t);
  }, [phase, info?.joinWindowOpensAt, nowMs, load]);

  const connect = useCallback(
    async (joinConfig: JoinConfig) => {
      setError(null);
      logger.info({ event: LogEvent.LIVEKIT_TOKEN_REQUESTED, guest: true });
      try {
        const { token, participantId, serverUrl } = await fetchAppointmentToken(publicAccessId, name.trim());
        setSession({ token, participantId, serverUrl: serverUrl || configuredServerUrl(), joinConfig });
        setPhase('CONNECTED');
      } catch (err) {
        if (err instanceof ApiError && err.code === 'APPOINTMENT_CANCELLED') {
          setPhase('CANCELLED');
          return;
        }
        if (
          err instanceof ApiError &&
          (err.code === 'APPOINTMENT_ENDED' || err.code === 'ROOM_EXPIRED')
        ) {
          setPhase('ENDED');
          return;
        }
        setError(toFriendly(err, 'Nao foi possivel entrar na chamada.'));
        setPhase('SETUP');
      }
    },
    [publicAccessId, name],
  );

  // Sala de espera: no horario agendado, conecta sozinho (§15).
  useEffect(() => {
    if (phase !== 'PRECALL_WAIT' || !info?.scheduledStart || !pendingJoin.current) return;
    if (nowMs >= new Date(info.scheduledStart).getTime()) {
      const jc = pendingJoin.current;
      pendingJoin.current = null;
      void connect(jc);
    }
  }, [phase, info?.scheduledStart, nowMs, connect]);

  function onDeviceJoin(joinConfig: JoinConfig) {
    const startMs = info?.scheduledStart ? new Date(info.scheduledStart).getTime() : 0;
    if (Date.now() >= startMs) {
      void connect(joinConfig);
    } else {
      pendingJoin.current = joinConfig;
      setPhase('PRECALL_WAIT');
    }
  }

  function backToStart() {
    window.history.replaceState(null, '', '/');
    window.location.reload();
  }

  if (config.mediaProvider === 'livekit' && !config.livekitUrl) {
    return (
      <div className="mx-auto max-w-md p-6">
        <ErrorNotice error={{ code: 'CONFIG_MISSING', message: 'VITE_LIVEKIT_URL nao foi configurada.' }} />
      </div>
    );
  }

  if (phase === 'LOADING') {
    return <Centered>Carregando atendimento...</Centered>;
  }

  if (phase === 'ERROR') {
    return (
      <Centered>
        {error && <ErrorNotice error={error} />}
        <PrimaryButton onClick={backToStart}>Voltar para inicio</PrimaryButton>
      </Centered>
    );
  }

  if (phase === 'CANCELLED') {
    return (
      <Card title="Atendimento cancelado">
        <p className="text-slate-300">Este atendimento foi cancelado.</p>
        <PrimaryButton onClick={backToStart}>Voltar para inicio</PrimaryButton>
      </Card>
    );
  }

  if (phase === 'ENDED') {
    return (
      <Card title="Atendimento encerrado">
        <p className="text-slate-300">Este atendimento ja foi encerrado.</p>
        {info?.nextOccurrenceStart && (
          <p className="text-sm text-slate-400">
            Proximo atendimento: {formatTime(info.nextOccurrenceStart)} ({formatDate(info.nextOccurrenceStart)})
          </p>
        )}
        <PrimaryButton onClick={backToStart}>Voltar para inicio</PrimaryButton>
      </Card>
    );
  }

  if (phase === 'BEFORE_WINDOW' && info) {
    return (
      <Card title={info.title}>
        <p className="text-slate-300">
          Atendimento agendado{info.scheduledStart ? ` para ${formatTime(info.scheduledStart)}` : ''}.
        </p>
        {info.joinWindowOpensAt && (
          <p className="text-sm text-slate-400">
            Voce podera entrar a partir das {formatTime(info.joinWindowOpensAt)}.
          </p>
        )}
        <p className="text-xs text-slate-500">🕐 {formatClock(nowMs)}</p>
      </Card>
    );
  }

  if (phase === 'CONNECTED' && session) {
    return (
      <CallRoom
        serverUrl={session.serverUrl}
        token={session.token}
        participantId={session.participantId}
        joinConfig={session.joinConfig}
        resolveSession={false}
        onLeave={() => setPhase('CALL_ENDED')}
        onError={() => {
          setError({ code: 'CONNECTION_ERROR', message: 'Conexao com a sala foi perdida.' });
          setPhase('CALL_ENDED');
        }}
      />
    );
  }

  if (phase === 'CALL_ENDED') {
    return (
      <Card title="Chamada encerrada">
        <p className="text-slate-300">Obrigado por participar.</p>
        <PrimaryButton onClick={backToStart}>Voltar para inicio</PrimaryButton>
      </Card>
    );
  }

  if (phase === 'PRECALL_WAIT' && info) {
    return (
      <Card title={info.title}>
        <p className="text-3xl">🕐 {formatClock(nowMs)}</p>
        <p className="text-slate-300">Aguardando inicio</p>
        {info.scheduledStart && (
          <p className="text-sm text-slate-400">A chamada comeca as {formatTime(info.scheduledStart)}.</p>
        )}
        <p className="text-xs text-slate-500">Camera e microfone prontos 🟢</p>
      </Card>
    );
  }

  // SETUP
  return (
    <DeviceSetup
      roomId={info?.roomId ?? ''}
      userName={name}
      guestName={name}
      onGuestNameChange={setName}
      joining={false}
      joinError={error}
      onJoin={onDeviceJoin}
      onBack={backToStart}
    />
  );
}

function toFriendly(err: unknown, fallback: string): FriendlyError {
  return err instanceof ApiError ? { code: err.code, message: err.message } : { code: 'UNKNOWN', message: fallback };
}

function formatClock(ms: number): string {
  return new Date(ms).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { day: '2-digit', month: '2-digit' });
}

function Centered({ children }: { children: ReactNode }) {
  return (
    <div className="mx-auto flex min-h-full max-w-md flex-col items-center justify-center gap-4 p-6 text-center text-slate-300">
      {children}
    </div>
  );
}

function Card({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="mx-auto flex min-h-full max-w-md flex-col items-center justify-center gap-3 p-6 text-center">
      <h1 className="text-xl font-semibold text-slate-100">{title}</h1>
      {children}
    </div>
  );
}

function PrimaryButton({ onClick, children }: { onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="rounded-lg bg-indigo-600 px-4 py-3 font-semibold text-white hover:bg-indigo-500"
    >
      {children}
    </button>
  );
}
