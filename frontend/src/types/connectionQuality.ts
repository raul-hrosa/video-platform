/** Niveis de qualidade de conexao da plataforma, do desconhecido ao melhor. */
export type QualityLevel = 'UNKNOWN' | 'POOR' | 'UNSTABLE' | 'GOOD' | 'EXCELLENT';

export const QUALITY_LEVELS: QualityLevel[] = ['UNKNOWN', 'POOR', 'UNSTABLE', 'GOOD', 'EXCELLENT'];

/** Numero de barras preenchidas por nivel (1..5). */
export const QUALITY_BARS: Record<QualityLevel, number> = {
  UNKNOWN: 0,
  POOR: 1,
  UNSTABLE: 2,
  GOOD: 4,
  EXCELLENT: 5,
};

/** Rotulo acessivel em pt-BR. */
export const QUALITY_LABEL: Record<QualityLevel, string> = {
  UNKNOWN: 'desconhecida',
  POOR: 'ruim',
  UNSTABLE: 'instavel',
  GOOD: 'boa',
  EXCELLENT: 'excelente',
};

/**
 * Metricas coletadas no cliente. Campo ausente = `null` (nunca 0) — nao inferir
 * o que nao foi medido.
 */
export interface ConnectionMetrics {
  rttMs: number | null;
  packetLossPercent: number | null;
  jitterMs: number | null;
  audioBitrate: number | null;
  videoBitrate: number | null;
  videoWidth: number | null;
  videoHeight: number | null;
  videoFps: number | null;
  connectionState: string | null;
}

export const EMPTY_METRICS: ConnectionMetrics = {
  rttMs: null,
  packetLossPercent: null,
  jitterMs: null,
  audioBitrate: null,
  videoBitrate: null,
  videoWidth: null,
  videoHeight: null,
  videoFps: null,
  connectionState: null,
};
