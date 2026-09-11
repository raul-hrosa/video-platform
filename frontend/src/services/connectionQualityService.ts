import type { ConnectionMetrics, QualityLevel } from '../types/connectionQuality';

/**
 * Classificacao de qualidade de conexao — modelo da plataforma, sem dependencia
 * do provider de midia. A coleta de metricas especificas do LiveKit e o
 * mapeamento da escala nativa vivem em `video/livekit/livekitMetrics.ts` (a
 * borda). Aqui ficam apenas os limiares e a regra de classificacao.
 *
 * Limites de classificacao (heuristica inicial — nao um padrao universal de
 * qualidade de internet). Espelham `ConnectionQualityService` do backend.
 * Cada valor e' o limite superior (exclusivo) do nivel; acima do ultimo = POOR.
 * Escala da plataforma (Sprint 10 §8), do pior ao melhor:
 * UNKNOWN, POOR, UNSTABLE, GOOD, EXCELLENT.
 */
export const THRESHOLDS = {
  rttMs: [80, 150, 250, 400],
  packetLossPercent: [1, 2, 5, 10],
  jitterMs: [15, 30, 50, 100],
} as const;

const BY_INDEX: QualityLevel[] = ['EXCELLENT', 'GOOD', 'UNSTABLE', 'POOR'];
const ORDER: QualityLevel[] = ['UNKNOWN', 'POOR', 'UNSTABLE', 'GOOD', 'EXCELLENT'];

function levelFor(value: number, thresholds: readonly number[]): QualityLevel {
  for (let i = 0; i < thresholds.length; i++) {
    if (value < thresholds[i]) return BY_INDEX[i];
  }
  return 'POOR';
}

function worst(a: QualityLevel | null, b: QualityLevel): QualityLevel {
  if (a === null) return b;
  return ORDER.indexOf(a) <= ORDER.indexOf(b) ? a : b;
}

/** Classifica pela pior metrica disponivel. Sem metricas -> UNSTABLE (neutro). */
export function classify(m: ConnectionMetrics): QualityLevel {
  let level: QualityLevel | null = null;
  if (m.rttMs != null) level = worst(level, levelFor(m.rttMs, THRESHOLDS.rttMs));
  if (m.packetLossPercent != null) {
    level = worst(level, levelFor(m.packetLossPercent, THRESHOLDS.packetLossPercent));
  }
  if (m.jitterMs != null) {
    let jitter = levelFor(m.jitterMs, THRESHOLDS.jitterMs);
    if (
      m.rttMs == null &&
      m.packetLossPercent == null &&
      ORDER.indexOf(jitter) < ORDER.indexOf('UNSTABLE')
    ) {
      jitter = 'UNSTABLE';
    }
    level = worst(level, jitter);
  }
  return level ?? 'UNSTABLE';
}
