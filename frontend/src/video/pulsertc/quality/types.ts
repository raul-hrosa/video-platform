/**
 * Modelo interno do monitoramento de qualidade PulseRTC (Sprint 12).
 *
 * O frontend **coleta e envia** (`quality_report`); a Quality Engine do PulseRTC
 * **calcula o veredito**. Aqui só ficam os tipos de transporte + a tradução da
 * escala do PulseRTC (`GOOD/WARNING/POOR/UNKNOWN`) para a escala da plataforma
 * (`QualityLevel`, §16).
 */
import type { QualityLevel } from '../../../types/connectionQuality';

/**
 * Uma amostra crua de `getStats()` já normalizada para o formato de `samples`
 * que o PulseRTC espera no `quality_report` (fonte de verdade: `quality.js` da
 * demo do PulseRTC). Contadores são **cumulativos** — a engine faz os deltas.
 * Todo campo além de `kind`/`tMs` é opcional: campo ausente permanece ausente
 * (§6, §12).
 */
export interface QualitySample {
  kind: 'audio' | 'video' | 'connection';
  direction?: 'inbound' | 'outbound';
  publicationId?: string;
  tMs: number;

  // rtp
  packetsSent?: number;
  packetsReceived?: number;
  packetsLost?: number;
  bytesSent?: number;
  bytesReceived?: number;
  jitterMs?: number;
  rttMs?: number;
  enabled?: boolean;

  // vídeo
  framesDecoded?: number;
  framesDropped?: number;
  fps?: number;
  width?: number;
  height?: number;

  // codec
  codec?: string;

  // connection (candidate-pair)
  availableIncomingBitrate?: number;
  availableOutgoingBitrate?: number;
  connState?: string;
  iceState?: string;
}

export interface QualityReport {
  type: 'quality_report';
  samples: QualitySample[];
}

/** Status por stream/participante como o PulseRTC devolve. */
export type PulseStatus = 'GOOD' | 'WARNING' | 'POOR' | 'UNKNOWN' | 'EXCELLENT';

/** Escala do PulseRTC → escala da plataforma (§16). */
export function pulseStatusToLevel(status: string | null | undefined): QualityLevel {
  switch ((status ?? '').toUpperCase()) {
    case 'EXCELLENT':
      return 'EXCELLENT';
    case 'GOOD':
      return 'GOOD';
    case 'WARNING':
    case 'UNSTABLE':
    case 'FAIR':
      return 'UNSTABLE';
    case 'POOR':
    case 'BAD':
    case 'CRITICAL':
      return 'POOR';
    default:
      return 'UNKNOWN';
  }
}

/** Veredito de um stream (audio/video/connection) + métricas para diagnóstico. */
export interface StreamQuality {
  level: QualityLevel;
  score?: number;
  reason?: string;
  metrics?: Record<string, unknown>;
}

/** Veredito de qualidade de um participante para a UI (§17-§19). */
export interface ParticipantQuality {
  level: QualityLevel;
  score?: number;
  reason?: string;
  audio?: StreamQuality;
  video?: StreamQuality;
  connection?: StreamQuality;
  metrics?: Record<string, unknown>;
}

/** Evento `quality_changed` / `quality_degraded` / `quality_recovered` do WS. */
export interface QualityEvent {
  type: 'quality_changed' | 'quality_degraded' | 'quality_recovered';
  participantId?: string;
  direction?: string;
  mediaType?: string;
  from?: string;
  status?: string;
  reason?: string;
}
