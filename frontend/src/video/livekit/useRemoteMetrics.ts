import type { Participant } from 'livekit-client';
import { useEffect, useState } from 'react';
import { EMPTY_METRICS, type ConnectionMetrics } from '../../types/connectionQuality';
import { collectRemoteMetrics } from './livekitMetrics';

const INTERVAL_MS = 10_000;

/**
 * Metricas do stream recebido de um participante remoto, amostradas a cada 10s.
 * Sao o que ESTE navegador consegue medir da recepcao (jitter, perda, resolucao,
 * bitrate) — nao ha RTT por participante no WebRTC.
 */
export function useRemoteMetrics(participant: Participant): ConnectionMetrics {
  const [metrics, setMetrics] = useState<ConnectionMetrics>(EMPTY_METRICS);

  useEffect(() => {
    let stopped = false;
    async function tick() {
      if (stopped) return;
      const m = await collectRemoteMetrics(participant);
      if (!stopped) setMetrics(m);
    }
    void tick();
    const id = window.setInterval(tick, INTERVAL_MS);
    return () => {
      stopped = true;
      window.clearInterval(id);
    };
  }, [participant]);

  return metrics;
}
