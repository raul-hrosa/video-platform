import type { QualityLevel, RoomDisplayStatus, RoomStatus } from '../types';
import { QUALITY_ICON, QUALITY_LABEL, STATUS_LABEL, STATUS_TONE } from '../services/format';

const DISPLAY_TONE: Record<RoomDisplayStatus, string> = {
  LIVE: 'border-emerald-500/40 bg-emerald-500/10 text-emerald-200',
  IDLE: 'border-slate-600 bg-slate-700/40 text-slate-300',
  ENDED: 'border-slate-700 bg-slate-800/40 text-slate-400',
};

/** Pílula LIVE / IDLE / ENDED do console (Sprint 9 §7). */
export function RoomConsoleBadge({ status }: { status: RoomDisplayStatus }) {
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium ${DISPLAY_TONE[status]}`}
    >
      <span aria-hidden>{status === 'LIVE' ? '●' : status === 'IDLE' ? '○' : '✓'}</span>
      {status}
    </span>
  );
}

export function RoomStatusBadge({ status }: { status: RoomStatus }) {
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium ${STATUS_TONE[status]}`}
    >
      {status === 'ACTIVE' && <span aria-hidden>🔴</span>}
      {status === 'ENDED' && <span aria-hidden>✓</span>}
      {STATUS_LABEL[status]}
    </span>
  );
}

export function QualityBadge({ level }: { level: QualityLevel | null }) {
  if (!level) {
    return <span className="text-xs text-slate-500">Sem dados</span>;
  }
  return (
    <span className="inline-flex items-center gap-1 text-xs text-slate-200">
      <span aria-hidden>{QUALITY_ICON[level]}</span>
      {QUALITY_LABEL[level]}
    </span>
  );
}
