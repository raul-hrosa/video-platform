import type { QualityLevel, RoomStatus } from '../types';

/**
 * Formatação para o histórico (Sprint 6). O backend guarda tudo em UTC (ISO-8601);
 * aqui exibimos no fuso local do usuário (§16). Nada é inventado — entradas
 * ausentes viram "—".
 */

const dateTime = new Intl.DateTimeFormat(undefined, {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

const timeOnly = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit' });

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '—' : dateTime.format(d);
}

export function formatTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '—' : timeOnly.format(d);
}

/** Duração legível: "19m 04s", "1h 12m", "—" quando não há dado. */
export function formatDuration(seconds: number | null | undefined): string {
  if (seconds == null || seconds < 0) return '—';
  const s = Math.floor(seconds % 60);
  const m = Math.floor((seconds / 60) % 60);
  const h = Math.floor(seconds / 3600);
  if (h > 0) return `${h}h ${String(m).padStart(2, '0')}m`;
  if (m > 0) return `${m}m ${String(s).padStart(2, '0')}s`;
  return `${s}s`;
}

/** Duração "em andamento" para sala ACTIVE, só visual (§43) — a partir de startedAt. */
export function elapsedSince(startedAt: string | null): number | null {
  if (!startedAt) return null;
  const start = new Date(startedAt).getTime();
  if (Number.isNaN(start)) return null;
  return Math.max(0, Math.floor((Date.now() - start) / 1000));
}

export const STATUS_LABEL: Record<RoomStatus, string> = {
  WAITING: 'Aguardando',
  ACTIVE: 'Em andamento',
  ENDED: 'Encerrada',
  EXPIRED: 'Expirada',
};

export const STATUS_TONE: Record<RoomStatus, string> = {
  WAITING: 'bg-slate-500/15 text-slate-300 border-slate-500/30',
  ACTIVE: 'bg-red-500/15 text-red-200 border-red-500/30',
  ENDED: 'bg-emerald-500/15 text-emerald-200 border-emerald-500/30',
  EXPIRED: 'bg-amber-500/15 text-amber-200 border-amber-500/30',
};

/** Indicador visual da escala da plataforma (Sprint 10 §15). */
export const QUALITY_ICON: Record<QualityLevel, string> = {
  EXCELLENT: '🟢',
  GOOD: '🟡',
  UNSTABLE: '🟠',
  POOR: '🔴',
  UNKNOWN: '⚪',
};

export const QUALITY_LABEL: Record<QualityLevel, string> = {
  EXCELLENT: 'Excelente',
  GOOD: 'Boa',
  UNSTABLE: 'Instável',
  POOR: 'Ruim',
  UNKNOWN: 'Desconhecida',
};

/** Meia-noite local de hoje e do início da semana (segunda), em ISO UTC — para o dashboard. */
export function dashboardBounds(now = new Date()): { todayStart: string; weekStart: string } {
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const dow = (today.getDay() + 6) % 7; // 0 = segunda
  const weekStart = new Date(today);
  weekStart.setDate(today.getDate() - dow);
  return { todayStart: today.toISOString(), weekStart: weekStart.toISOString() };
}
