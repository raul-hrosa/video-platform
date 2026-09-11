import type { ConnectionQuality, LocalParticipant, Participant, Room } from 'livekit-client';
import { EMPTY_METRICS, type ConnectionMetrics, type QualityLevel } from '../../types/connectionQuality';

/**
 * Borda LiveKit -> plataforma para métricas de conexão (Sprint 10.5 §4).
 *
 * Este é o único módulo da coleta de qualidade que conhece o `livekit-client`:
 * lê os stats WebRTC do SDK e o `ConnectionQuality` nativo e devolve sempre
 * `ConnectionMetrics` / `QualityLevel` da plataforma. Nada acima de
 * `video/livekit/` importa o SDK para isso.
 */

/** Mapeia o ConnectionQuality do LiveKit (4 estados) para a escala da plataforma. */
export function mapLiveKitQuality(q: ConnectionQuality): QualityLevel | null {
  switch (q) {
    case 'excellent':
      return 'EXCELLENT';
    case 'good':
      return 'GOOD';
    case 'poor':
      return 'POOR';
    case 'lost':
      return 'POOR';
    default:
      return null;
  }
}

function n(value: number | undefined | null): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function lossPercent(lost: number | null, received: number | null): number | null {
  if (lost == null || received == null) return null;
  const total = lost + received;
  if (total <= 0) return null;
  return Math.round((lost / total) * 1000) / 10;
}

/**
 * Coleta as metricas do participante local a partir dos stats de envio dos
 * proprios tracks publicados. Tudo que nao puder ser medido fica `null`.
 */
export async function collectLocalMetrics(room: Room): Promise<ConnectionMetrics> {
  const local: LocalParticipant | undefined = room.localParticipant;
  if (!local) return { ...EMPTY_METRICS, connectionState: room.state };

  const metrics: ConnectionMetrics = { ...EMPTY_METRICS, connectionState: room.state };

  try {
    const videoPub = [...local.videoTrackPublications.values()][0];
    const audioPub = [...local.audioTrackPublications.values()][0];
    const videoTrack = videoPub?.track;
    const audioTrack = audioPub?.track;

    if (videoTrack && 'getSenderStats' in videoTrack) {
      const statsList = await (videoTrack as { getSenderStats(): Promise<unknown[]> }).getSenderStats();
      const s = (Array.isArray(statsList) ? statsList[0] : statsList) as
        | Record<string, number | undefined>
        | undefined;
      if (s) {
        metrics.rttMs = n(s.roundTripTime != null ? s.roundTripTime * 1000 : undefined);
        metrics.jitterMs = n(s.jitter != null ? s.jitter * 1000 : undefined);
        metrics.packetLossPercent = lossPercent(n(s.packetsLost), n(s.packetsSent));
        metrics.videoWidth = n(s.frameWidth);
        metrics.videoHeight = n(s.frameHeight);
        metrics.videoFps = n(s.framesPerSecond);
      }
      metrics.videoBitrate = n(videoTrack.currentBitrate) || null;
    }

    if (audioTrack && 'getSenderStats' in audioTrack) {
      const a = (await (audioTrack as { getSenderStats(): Promise<unknown> }).getSenderStats()) as
        | Record<string, number | undefined>
        | undefined;
      if (a) {
        if (metrics.rttMs == null && a.roundTripTime != null) metrics.rttMs = n(a.roundTripTime * 1000);
        if (metrics.jitterMs == null && a.jitter != null) metrics.jitterMs = n(a.jitter * 1000);
        if (metrics.packetLossPercent == null) {
          metrics.packetLossPercent = lossPercent(n(a.packetsLost), n(a.packetsSent));
        }
      }
      metrics.audioBitrate = n(audioTrack.currentBitrate) || null;
    }
  } catch {
    // stats indisponiveis neste navegador/estado — mantem os campos como null
  }

  return metrics;
}

/**
 * Metricas do stream que ESTE cliente recebe de um participante remoto
 * (`getReceiverStats`). Reflete a rede do remoto + caminho ate aqui. RTT por
 * participante nao existe no WebRTC — fica `null`.
 */
export async function collectRemoteMetrics(participant: Participant): Promise<ConnectionMetrics> {
  const metrics: ConnectionMetrics = { ...EMPTY_METRICS };
  try {
    const videoTrack = [...participant.videoTrackPublications.values()][0]?.track;
    const audioTrack = [...participant.audioTrackPublications.values()][0]?.track;

    if (videoTrack && 'getReceiverStats' in videoTrack) {
      const s = (await (videoTrack as { getReceiverStats(): Promise<unknown> }).getReceiverStats()) as
        | Record<string, number | undefined>
        | undefined;
      if (s) {
        metrics.jitterMs = n(s.jitter != null ? s.jitter * 1000 : undefined);
        metrics.packetLossPercent = lossPercent(n(s.packetsLost), n(s.packetsReceived));
        metrics.videoWidth = n(s.frameWidth);
        metrics.videoHeight = n(s.frameHeight);
      }
      metrics.videoBitrate = n(videoTrack.currentBitrate) || null;
    }

    if (audioTrack && 'getReceiverStats' in audioTrack) {
      const a = (await (audioTrack as { getReceiverStats(): Promise<unknown> }).getReceiverStats()) as
        | Record<string, number | undefined>
        | undefined;
      if (a) {
        if (metrics.jitterMs == null && a.jitter != null) metrics.jitterMs = n(a.jitter * 1000);
        if (metrics.packetLossPercent == null) {
          metrics.packetLossPercent = lossPercent(n(a.packetsLost), n(a.packetsReceived));
        }
      }
      metrics.audioBitrate = n(audioTrack.currentBitrate) || null;
    }
  } catch {
    // sem stats de recepcao — mantem os campos como null
  }
  return metrics;
}
