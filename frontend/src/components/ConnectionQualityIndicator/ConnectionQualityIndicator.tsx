import { useId, useState } from 'react';
import {
  QUALITY_BARS,
  QUALITY_LABEL,
  type ConnectionMetrics,
  type QualityLevel,
} from '../../types/connectionQuality';

interface Props {
  level: QualityLevel | null;
  metrics?: ConnectionMetrics;
  reconnectCount?: number;
  participantName?: string;
  /** "recepcao" = metricas medidas no stream recebido (sem RTT por participante). */
  metricsSource?: 'own' | 'recepcao';
}

const EMPTY_COLOR = '#475569';

const BAR_COLOR: Record<QualityLevel, string> = {
  EXCELLENT: '#22c55e',
  GOOD: '#22c55e',
  UNSTABLE: '#eab308',
  POOR: '#ef4444',
  UNKNOWN: EMPTY_COLOR,
};

function fmt(value: number | null | undefined, unit: string): string {
  return value == null ? '—' : `${value}${unit}`;
}

export function ConnectionQualityIndicator({
  level,
  metrics,
  reconnectCount,
  participantName,
  metricsSource = 'own',
}: Props) {
  const [open, setOpen] = useState(false);
  const tooltipId = useId();

  const filled = level ? QUALITY_BARS[level] : 0;
  const color = level ? BAR_COLOR[level] : EMPTY_COLOR;
  const label = level
    ? `Qualidade da conexao: ${QUALITY_LABEL[level]}`
    : 'Qualidade da conexao: medindo';

  return (
    <span
      className="relative inline-flex items-center"
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
      onFocus={() => setOpen(true)}
      onBlur={() => setOpen(false)}
      onClick={() => setOpen((v) => !v)}
    >
      <svg
        role="img"
        aria-label={label}
        aria-describedby={open ? tooltipId : undefined}
        tabIndex={0}
        width="22"
        height="16"
        viewBox="0 0 22 16"
        className="outline-none focus-visible:ring-2 focus-visible:ring-indigo-400 rounded"
      >
        {[0, 1, 2, 3, 4].map((i) => {
          const h = 3 + i * 3;
          return (
            <rect
              key={i}
              x={i * 4.5}
              y={16 - h}
              width="3"
              height={h}
              rx="0.6"
              fill={i < filled ? color : EMPTY_COLOR}
            />
          );
        })}
      </svg>

      {open && (
        <span
          id={tooltipId}
          role="tooltip"
          className="absolute right-0 top-full z-20 mt-1 w-56 rounded-lg border border-slate-600 bg-slate-800 p-3 text-xs text-slate-200 shadow-lg"
        >
          <span className="mb-1 block font-medium text-slate-100">
            {participantName ? `${participantName} — ` : ''}
            {level ? QUALITY_LABEL[level] : 'medindo…'}
          </span>
          {metrics ? (
            <>
              <span className="grid grid-cols-2 gap-x-2 gap-y-0.5">
                <span className="text-slate-400">Latencia</span>
                <span>{fmt(metrics.rttMs, ' ms')}</span>
                <span className="text-slate-400">Packet loss</span>
                <span>{metrics.packetLossPercent == null ? '—' : `${metrics.packetLossPercent}%`}</span>
                <span className="text-slate-400">Jitter</span>
                <span>{fmt(metrics.jitterMs, ' ms')}</span>
                <span className="text-slate-400">Video</span>
                <span>
                  {metrics.videoWidth && metrics.videoHeight
                    ? `${metrics.videoWidth}x${metrics.videoHeight}`
                    : '—'}
                  {metrics.videoFps ? ` / ${metrics.videoFps}fps` : ''}
                </span>
                {metricsSource === 'own' && (
                  <>
                    <span className="text-slate-400">Reconexoes</span>
                    <span>{reconnectCount ?? 0}</span>
                  </>
                )}
              </span>
              {metricsSource === 'recepcao' && (
                <span className="mt-1 block text-[10px] text-slate-500">
                  medido na sua recepcao deste participante
                </span>
              )}
            </>
          ) : (
            <span className="text-slate-400">Sem metricas detalhadas.</span>
          )}
        </span>
      )}
    </span>
  );
}
