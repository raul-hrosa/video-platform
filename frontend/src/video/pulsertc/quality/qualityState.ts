/**
 * Estado de qualidade por participante para a UI (Sprint 12 §16, §19).
 *
 * **Fonte primária: os eventos `quality_*` que o PulseRTC empurra pelo WS** —
 * cada evento traz `participantId` (identidade completa), `mediaType`
 * (`connection`/`audio`/`video`), `status` e `reason`, para todos os
 * participantes que a engine acompanha. O `GET .../media-quality` é só
 * catch-up (score + estado atual p/ quem entrou depois). Não há segunda engine
 * aqui (§16) — só merge de estado e associação ao participante certo (§19).
 */
import { pulseStatusToLevel, type ParticipantQuality, type QualityEvent, type StreamQuality } from './types';

const ORDER = ['POOR', 'UNSTABLE', 'GOOD', 'EXCELLENT'] as const;
type Lvl = ParticipantQuality['level'];
/** Pior entre dois níveis; `UNKNOWN` = "sem dado", nunca é o pior. */
const worst = (a: Lvl, b: Lvl): Lvl => {
  if (a === 'UNKNOWN') return b;
  if (b === 'UNKNOWN') return a;
  return ORDER.indexOf(a as (typeof ORDER)[number]) <= ORDER.indexOf(b as (typeof ORDER)[number])
    ? a
    : b;
};

/** Chave = identidade PulseRTC do participante (`<sub>.<8hex>`). */
export type QualityState = Record<string, ParticipantQuality>;

interface RawStream {
  level?: string | null;
  score?: number | null;
  reason?: string | null;
  metrics?: Record<string, unknown> | null;
}

/**
 * Linha do breakdown que o backend expõe (`GET .../media-quality`) — já na
 * escala da plataforma (`EXCELLENT/GOOD/UNSTABLE/POOR/UNKNOWN`).
 */
export interface QualityBreakdownRow {
  participantRef: string;
  level?: string | null;
  score?: number | null;
  reason?: string | null;
  metrics?: Record<string, unknown> | null;
  audio?: RawStream | null;
  video?: RawStream | null;
  connection?: RawStream | null;
}

const stream = (s: RawStream | null | undefined): StreamQuality | undefined =>
  s == null
    ? undefined
    : {
        level: pulseStatusToLevel(s.level),
        score: s.score ?? undefined,
        reason: s.reason ?? undefined,
        metrics: s.metrics ?? undefined,
      };

function rowToQuality(row: QualityBreakdownRow): ParticipantQuality {
  return {
    level: pulseStatusToLevel(row.level),
    score: row.score ?? undefined,
    reason: row.reason ?? undefined,
    metrics: row.metrics ?? undefined,
    audio: stream(row.audio),
    video: stream(row.video),
    connection: stream(row.connection),
  };
}

/** Substitui o estado pelo breakdown oficial da engine (usado no reset/join). */
export function applyBreakdown(rows: QualityBreakdownRow[]): QualityState {
  const next: QualityState = {};
  for (const row of rows) {
    if (row.participantRef) next[row.participantRef] = rowToQuality(row);
  }
  return next;
}

/**
 * Catch-up: funde o breakdown do GET sobre o estado atual. O GET traz o `score`
 * e o estado de quem entrou depois; os eventos `quality_*` do WS seguem sendo a
 * fonte viva. `score` do GET é preservado mesmo quando um evento chegou depois.
 */
export function mergeBreakdown(prev: QualityState, rows: QualityBreakdownRow[]): QualityState {
  const next = { ...prev };
  for (const row of rows) {
    if (!row.participantRef) continue;
    const fromGet = rowToQuality(row);
    const existing = prev[row.participantRef];
    next[row.participantRef] = existing
      ? { ...fromGet, ...existing, score: fromGet.score ?? existing.score }
      : fromGet;
  }
  return next;
}

/**
 * Aplica um evento `quality_*` empurrado pelo PulseRTC. O evento é por
 * participante (`participantId`) e por `mediaType`. O nível "overall" exibido é
 * o do `mediaType: connection`; se ainda não houve evento de conexão, cai para o
 * pior entre áudio e vídeo. Não recalcula score (§14) — só reflete o veredito.
 */
export function applyQualityEvent(prev: QualityState, event: QualityEvent): QualityState {
  const id = event.participantId;
  if (!id) return prev;
  const current: ParticipantQuality = prev[id] ?? { level: 'UNKNOWN' };
  const level = pulseStatusToLevel(event.status);
  const verdict: StreamQuality = { level, reason: event.reason ?? undefined };

  const next: ParticipantQuality = { ...current };
  const media = event.mediaType ?? (event.direction === 'connection' ? 'connection' : undefined);
  if (media === 'audio') next.audio = verdict;
  else if (media === 'video') next.video = verdict;
  else next.connection = verdict; // connection ou evento sem mediaType

  next.level = next.connection
    ? next.connection.level
    : worst(next.audio?.level ?? 'UNKNOWN', next.video?.level ?? 'UNKNOWN');
  next.reason =
    next.connection?.reason ?? next.video?.reason ?? next.audio?.reason ?? current.reason;

  return { ...prev, [id]: next };
}

/** Reset ao criar uma nova PeerConnection / durante recovery (§20). */
export function resetQualityState(): QualityState {
  return {};
}

/** Qualidade de um participante pela identidade (casa também pelo prefixo `<sub>`). */
export function qualityFor(state: QualityState, identity: string): ParticipantQuality | undefined {
  if (state[identity]) return state[identity];
  const sub = identity.split('.')[0];
  const hit = Object.entries(state).find(([k]) => k.split('.')[0] === sub);
  return hit?.[1];
}
