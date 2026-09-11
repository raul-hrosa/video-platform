import { useCallback, useEffect, useState } from 'react';
import { CopyLinkButton } from '../components/CopyLinkButton';
import { ErrorNotice } from '../components/ErrorNotice';
import { QualityBadge, RoomConsoleBadge, RoomStatusBadge } from '../components/RoomBadges';
import {
  ApiError,
  getParticipantAnalytics,
  getParticipantsSummary,
  getRoom,
  getRoomAnalytics,
  getRoomEvents,
  getRoomQuality,
  getRoomSessions,
} from '../services/api';
import { roomLink } from '../services/roomLink';
import { QUALITY_LABEL, elapsedSince, formatDateTime, formatDuration, formatTime } from '../services/format';
import type {
  FriendlyError,
  ParticipantAnalytics,
  ParticipantSessionResponse,
  RoomAnalytics,
  RoomEvent,
  RoomParticipantsSummary,
  RoomQuality,
  RoomResponse,
} from '../types';

interface Props {
  roomId: string;
  onBack: () => void;
  /** Sala ACTIVE: voltar para a chamada (§12). */
  onRejoin: (roomId: string) => void;
}

interface Bundle {
  room: RoomResponse;
  analytics: RoomAnalytics | null;
  summary: RoomParticipantsSummary | null;
  sessions: ParticipantSessionResponse[];
  quality: RoomQuality | null;
  events: RoomEvent[];
}

function toFriendly(err: unknown): FriendlyError {
  if (err instanceof ApiError) {
    if (err.code === 'FORBIDDEN') {
      return { code: err.code, message: 'Voce nao tem permissao para visualizar esta sala.' };
    }
    if (err.code === 'ROOM_NOT_FOUND') {
      return { code: err.code, message: 'Sala nao encontrada.' };
    }
    return { code: err.code, message: 'Nao foi possivel carregar os detalhes da sala.' };
  }
  return { code: 'UNKNOWN', message: 'Nao foi possivel carregar os detalhes da sala.' };
}

/** Endpoints auxiliares nunca derrubam a tela — o detalhe da sala ainda abre. */
async function soft<T>(p: Promise<T>): Promise<T | null> {
  try {
    return await p;
  } catch {
    return null;
  }
}

export function RoomDetailScreen({ roomId, onBack, onRejoin }: Props) {
  const [bundle, setBundle] = useState<Bundle | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<FriendlyError | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const room = await getRoom(roomId);
      const [analytics, summary, sessions, quality, events] = await Promise.all([
        soft(getRoomAnalytics(roomId)),
        soft(getParticipantsSummary(roomId)),
        soft(getRoomSessions(roomId)),
        soft(getRoomQuality(roomId)),
        soft(getRoomEvents(roomId)),
      ]);
      setBundle({ room, analytics, summary, sessions: sessions ?? [], quality, events: events ?? [] });
    } catch (err) {
      setError(toFriendly(err));
    } finally {
      setLoading(false);
    }
  }, [roomId]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="mx-auto flex min-h-full max-w-2xl flex-col gap-5 p-4 sm:p-6">
      <div className="flex items-center justify-between">
        <button type="button" onClick={onBack} className="text-sm text-slate-400 hover:text-slate-200">
          ← Historico
        </button>
        <button type="button" onClick={() => void load()} className="text-sm text-slate-400 hover:text-slate-200">
          Atualizar
        </button>
      </div>

      {loading && (
        <div className="space-y-3">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="h-24 animate-pulse rounded-xl border border-slate-800 bg-slate-800/40" />
          ))}
        </div>
      )}

      {error && <ErrorNotice error={error} onRetry={load} />}

      {!loading && !error && bundle && <Detail bundle={bundle} onRejoin={onRejoin} />}
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-xl border border-slate-700 bg-slate-800/60 p-4">
      <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-400">{title}</h2>
      {children}
    </section>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-slate-400">{label}</p>
      <p className="text-slate-100">{value}</p>
    </div>
  );
}

function Detail({ bundle, onRejoin }: { bundle: Bundle; onRejoin: (roomId: string) => void }) {
  const { room, analytics, summary, sessions, quality, events } = bundle;
  const [selected, setSelected] = useState<ParticipantAnalytics | null>(null);
  const [selectedRef, setSelectedRef] = useState<string | null>(null);

  const openParticipant = async (participantRef: string) => {
    setSelectedRef(participantRef);
    setSelected(null);
    try {
      setSelected(await getParticipantAnalytics(room.roomId, participantRef));
    } catch {
      setSelectedRef(null);
    }
  };

  const roomDuration =
    analytics?.durationSeconds != null
      ? formatDuration(analytics.durationSeconds)
      : room.status === 'ACTIVE'
        ? `${formatDuration(elapsedSince(room.startedAt))} (em andamento)`
        : room.status === 'EXPIRED'
          ? 'Nunca iniciada'
          : '—';

  return (
    <>
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-slate-100">{room.name ?? room.roomId}</h1>
          <p className="font-mono text-xs text-slate-500">{room.roomId}</p>
          {room.roomProfileName && (
            <p className="text-sm text-slate-400">Perfil: {room.roomProfileName}</p>
          )}
          {room.ownerName && (
            <p className="text-sm text-slate-400">Criada por {room.ownerName}</p>
          )}
        </div>
        <div className="flex flex-col items-end gap-1">
          <RoomConsoleBadge status={room.displayStatus} />
          <RoomStatusBadge status={room.status} />
        </div>
      </div>

      {room.displayStatus !== 'ENDED' && (
        <button
          type="button"
          onClick={() => onRejoin(room.roomId)}
          className="rounded-lg bg-indigo-600 px-4 py-2 font-semibold text-white hover:bg-indigo-500"
        >
          {room.displayStatus === 'LIVE' ? 'Voltar para a chamada' : 'Entrar na sala'}
        </button>
      )}

      <Section title="Access link">
        <p className="mb-2 break-all font-mono text-xs text-slate-300">{roomLink(room.roomId)}</p>
        <div className="flex gap-2">
          <CopyLinkButton roomId={room.roomId} />
          <a
            href={roomLink(room.roomId)}
            target="_blank"
            rel="noreferrer"
            className="rounded-lg border border-slate-600 px-3 py-2 text-sm font-medium text-slate-200 hover:bg-slate-700"
          >
            Abrir link
          </a>
        </div>
      </Section>

      <Section title="Informacoes">
        <div className="grid grid-cols-2 gap-3">
          <Field label="Status" value={room.status} />
          <Field label="Duracao" value={roomDuration} />
          <Field label="Criada" value={formatDateTime(room.createdAt)} />
          <Field label="Expira" value={formatDateTime(room.expiresAt)} />
          <Field label="Inicio" value={formatDateTime(room.startedAt)} />
          <Field label="Termino" value={formatDateTime(room.endedAt)} />
        </div>
      </Section>

      <Section title="Analytics">
        {analytics ? (
          <div className="grid grid-cols-2 gap-3">
            <Field label="Duracao" value={formatDuration(analytics.durationSeconds)} />
            <Field label="Participantes" value={`${analytics.participants}`} />
            <Field label="Pico simultaneo" value={`${analytics.peakParticipants}`} />
            <Field
              label="Tempo total de participantes"
              value={formatDuration(analytics.totalParticipantSeconds)}
            />
            {analytics.quality && (
              <Field label="Reconexoes" value={`${analytics.quality.totalReconnects}`} />
            )}
          </div>
        ) : (
          <p className="text-sm text-slate-400">Sem analytics para esta sala.</p>
        )}
      </Section>

      <Section title="Participantes">
        {summary && summary.participants.length > 0 ? (
          <>
            <p className="mb-3 text-sm text-slate-400">
              {summary.distinctParticipants} participante(s) distinto(s) · {summary.totalEntries} entrada(s)
            </p>
            <ul className="space-y-3">
              {summary.participants.map((p) => (
                <li key={p.participantId}>
                  <button
                    type="button"
                    onClick={() => void openParticipant(p.participantId)}
                    className="w-full rounded-lg border border-slate-700 p-3 text-left transition hover:border-slate-500"
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-medium text-slate-100">
                        {p.participantName}
                        {p.isGuest && <span className="ml-1 text-xs text-slate-500">convidado</span>}
                      </span>
                      <span className="text-sm text-slate-300">{formatDuration(p.totalDurationSeconds)}</span>
                    </div>
                    <p className="mt-1 text-xs text-slate-400">
                      Entrou {formatTime(p.firstJoinedAt)} · Saiu {formatTime(p.lastLeftAt)} ·{' '}
                      {p.sessions} sessao(oes)
                      {p.reconnects > 0 && ` · ${p.reconnects} reconexao(oes)`}
                    </p>
                  </button>
                  {selectedRef === p.participantId && (
                    <ParticipantPanel analytics={selected} onClose={() => setSelectedRef(null)} />
                  )}
                </li>
              ))}
            </ul>
          </>
        ) : (
          <p className="text-sm text-slate-400">Nenhum participante registrado.</p>
        )}
      </Section>

      {sessions.length > 0 && (
        <Section title="Sessoes">
          <ul className="space-y-2">
            {sessions.map((s) => (
              <li key={s.sessionId} className="flex items-center justify-between text-sm">
                <span className="text-slate-200">{s.participantName}</span>
                <span className="text-slate-400">
                  {formatTime(s.joinedAt)} → {formatTime(s.leftAt)} · {formatDuration(s.durationSeconds)}
                </span>
              </li>
            ))}
          </ul>
        </Section>
      )}

      <Section title="Qualidade de conexao">
        {quality && quality.hasData ? (
          <ul className="space-y-3">
            {quality.participants.map((p) => (
              <li key={p.participantId} className="rounded-lg border border-slate-700 p-3">
                <div className="flex items-center justify-between">
                  <span className="font-medium text-slate-100">{p.participantName}</span>
                  <QualityBadge level={p.latestLevel} />
                </div>
                <p className="mt-1 text-xs text-slate-400">
                  {p.latestLevel == null
                    ? p.isGuest
                      ? 'Convidado — sem medicao de qualidade'
                      : 'Sem medicao de qualidade'
                    : p.rttMs == null && p.packetLossPercent == null && p.jitterMs == null
                      ? `Nivel: ${QUALITY_LABEL[p.latestLevel]}`
                      : [
                          p.rttMs != null && `RTT ${p.rttMs}ms`,
                          p.packetLossPercent != null && `Perda ${p.packetLossPercent}%`,
                          p.jitterMs != null && `Jitter ${p.jitterMs}ms`,
                        ]
                          .filter(Boolean)
                          .join(' · ')}
                </p>
              </li>
            ))}
            {!quality.timelineAvailable && (
              <li className="text-xs text-slate-500">
                Historico temporal de qualidade indisponivel — exibindo apenas o resumo final.
              </li>
            )}
          </ul>
        ) : (
          <p className="text-sm text-slate-400">Sem dados de qualidade para esta sala.</p>
        )}
      </Section>

      <Section title="Events">
        {events.length > 0 ? (
          <ul className="space-y-1.5 text-xs">
            {events.map((e, i) => (
              <li key={i} className="flex items-baseline justify-between gap-3">
                <span className="font-mono text-slate-300">{e.type}</span>
                <span className="shrink-0 text-slate-500">
                  {formatTime(e.at)}
                  {e.participantRef && ` · ${e.participantRef}`}
                  {e.detail && ` · ${e.detail}`}
                </span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-slate-400">Nenhum evento registrado.</p>
        )}
      </Section>
    </>
  );
}

function ParticipantPanel({
  analytics,
  onClose,
}: {
  analytics: ParticipantAnalytics | null;
  onClose: () => void;
}) {
  return (
    <div className="mt-2 rounded-lg border border-slate-600 bg-slate-900/60 p-3 text-xs">
      {!analytics ? (
        <p className="text-slate-400">Carregando...</p>
      ) : (
        <>
          <div className="mb-2 flex items-center justify-between">
            <span className="font-mono text-slate-300">{analytics.participantRef}</span>
            <button type="button" onClick={onClose} className="text-slate-500 hover:text-slate-300">
              fechar
            </button>
          </div>
          <div className="grid grid-cols-3 gap-2 text-slate-300">
            <div>
              <p className="text-slate-500">Sessões</p>
              <p>{analytics.history.totalSessions}</p>
            </div>
            <div>
              <p className="text-slate-500">Tempo total</p>
              <p>{formatDuration(analytics.history.totalConnectedSeconds)}</p>
            </div>
            <div>
              <p className="text-slate-500">Reconexões</p>
              <p>{analytics.history.reconnections}</p>
            </div>
          </div>
          {analytics.currentSession && (
            <p className="mt-2 text-emerald-300">
              Sessão atual: conectado há {formatDuration(analytics.currentSession.connectedSeconds)}
            </p>
          )}
          {analytics.quality && (
            <p className="mt-2 text-slate-400">
              Qualidade média {QUALITY_LABEL[analytics.quality.average]} · atual{' '}
              {QUALITY_LABEL[analytics.quality.current]}
            </p>
          )}
          {analytics.sessions.length > 0 && (
            <ul className="mt-2 space-y-1 text-slate-400">
              {analytics.sessions.map((s) => (
                <li key={s.sessionId}>
                  {formatTime(s.joinedAt)} → {formatTime(s.leftAt)} · {formatDuration(s.durationSeconds)}
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  );
}
