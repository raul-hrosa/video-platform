import type { Room } from 'livekit-client';
import { useEffect, useRef, useState } from 'react';
import { postQualitySnapshot } from '../../services/api';
import { classify } from '../../services/connectionQualityService';
import { LogEvent, logger } from '../../services/logger';
import { EMPTY_METRICS, type ConnectionMetrics, type QualityLevel } from '../../types/connectionQuality';
import { collectLocalMetrics } from './livekitMetrics';

const SNAPSHOT_INTERVAL_MS = 10_000;
/** Nº de leituras consecutivas concordando antes de mudar o nivel exibido (histerese). */
const CONFIRMATIONS_TO_CHANGE = 2;

interface Params {
  room: Room | null;
  roomId: string;
  participantId: string | null;
  sessionId: string | null;
  /** true quando o Room esta 'connected' (fonte reativa, ex.: useConnectionState). */
  connected: boolean;
}

interface Result {
  level: QualityLevel | null;
  metrics: ConnectionMetrics;
  reconnectCount: number;
}

/**
 * Monitora a qualidade da conexao do participante local: coleta metricas a cada
 * 10s, classifica com histerese, loga mudancas e envia snapshots ao backend.
 */
export function useConnectionQuality({
  room,
  roomId,
  participantId,
  sessionId,
  connected,
}: Params): Result {
  const [level, setLevel] = useState<QualityLevel | null>(null);
  const [metrics, setMetrics] = useState<ConnectionMetrics>(EMPTY_METRICS);
  const [reconnectCount, setReconnectCount] = useState(0);

  const displayedLevel = useRef<QualityLevel | null>(null);
  const pending = useRef<{ level: QualityLevel; count: number } | null>(null);
  const reconnectRef = useRef(0);

  useEffect(() => {
    reconnectRef.current = reconnectCount;
  }, [reconnectCount]);

  // conta reconexoes "soft" (ICE restart, sem drop completo)
  useEffect(() => {
    if (!room) return;
    const onReconnected = () => setReconnectCount((c) => c + 1);
    room.on('reconnected', onReconnected);
    return () => {
      room.off('reconnected', onReconnected);
    };
  }, [room]);

  useEffect(() => {
    if (!room || !connected) return;

    logger.info({ event: LogEvent.CONNECTION_MONITOR_STARTED, roomId, sessionId });

    let stopped = false;

    async function tick() {
      if (stopped || !room) return;
      let collected: ConnectionMetrics;
      try {
        collected = await collectLocalMetrics(room);
      } catch {
        logger.warn({ event: LogEvent.CONNECTION_METRICS_ERROR, roomId, reason: 'COLLECT_FAILED' });
        return;
      }
      setMetrics(collected);

      // sem nenhum sinal medido ainda -> mantem "medindo" em vez de fingir FAIR
      const hasSignal =
        collected.rttMs != null ||
        collected.packetLossPercent != null ||
        collected.jitterMs != null;
      if (!hasSignal && displayedLevel.current === null) {
        return;
      }

      const measured = classify(collected);
      const confirmed = applyHysteresis(measured);
      if (confirmed && confirmed !== displayedLevel.current) {
        const previous = displayedLevel.current;
        displayedLevel.current = confirmed;
        setLevel(confirmed);
        logger.info({
          event: LogEvent.CONNECTION_QUALITY_CHANGED,
          roomId,
          sessionId,
          previousQuality: previous,
          quality: confirmed,
        });
      } else if (displayedLevel.current === null) {
        displayedLevel.current = measured;
        setLevel(measured);
      }

      // so persiste depois que a sessao foi resolvida (depende do webhook)
      if (!sessionId || !participantId) return;
      try {
        await postQualitySnapshot(roomId, sessionId, {
          ...collected,
          participantId,
          qualityLevel: displayedLevel.current ?? measured,
          reconnectCount: reconnectRef.current,
        });
      } catch {
        logger.warn({ event: LogEvent.CONNECTION_METRICS_ERROR, roomId, reason: 'POST_FAILED' });
      }
    }

    void tick();
    const id = window.setInterval(tick, SNAPSHOT_INTERVAL_MS);

    return () => {
      stopped = true;
      window.clearInterval(id);
      logger.info({ event: LogEvent.CONNECTION_MONITOR_STOPPED, roomId, sessionId });
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [room, roomId, participantId, sessionId, connected]);

  function applyHysteresis(measured: QualityLevel): QualityLevel | null {
    if (measured === displayedLevel.current) {
      pending.current = null;
      return null;
    }
    if (pending.current?.level === measured) {
      pending.current.count += 1;
    } else {
      pending.current = { level: measured, count: 1 };
    }
    return pending.current.count >= CONFIRMATIONS_TO_CHANGE ? measured : null;
  }

  return { level, metrics, reconnectCount };
}
