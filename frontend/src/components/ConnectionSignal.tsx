/**
 * Indicador de qualidade de conexão estilo "sinal de celular" (Sprint 13).
 *
 * Só **apresenta** o estado oficial (`QualityLevel` — vem do PulseRTC via
 * `video-platform`). Zero lógica WebRTC aqui (§2). Cor nunca é o único sinal:
 * barras + texto + `aria-label` sempre presentes (§3, §10).
 */
import type { QualityLevel } from '../types/connectionQuality';
import {
  SIGNAL_EMOJI,
  SIGNAL_FILLED,
  SIGNAL_LABEL,
  SIGNAL_TOOLTIP,
} from '../services/qualityText';

const BAR_HEIGHTS = ['30%', '50%', '75%', '100%'];
const FILL_COLOR: Record<QualityLevel, string> = {
  EXCELLENT: '#22c55e',
  GOOD: '#22c55e',
  UNSTABLE: '#eab308',
  POOR: '#ef4444',
  UNKNOWN: '#94a3b8',
};

export interface ConnectionSignalProps {
  quality: QualityLevel;
  /** `compact` = só emoji + barras; `full` = + label textual. */
  variant?: 'compact' | 'full';
  /** Nome do participante, prefixado ao aria-label ("Conexão de João: instável"). */
  name?: string;
  /** Texto extra no tooltip (mensagem amigável do `reason`). */
  hint?: string | null;
  className?: string;
}

function Bars({ quality }: { quality: QualityLevel }) {
  const filled = SIGNAL_FILLED[quality];
  return (
    <span aria-hidden="true" className="inline-flex items-end gap-[2px]" style={{ height: '0.9em' }}>
      {BAR_HEIGHTS.map((h, i) => (
        <span
          key={i}
          style={{
            width: '3px',
            height: h,
            borderRadius: '1px',
            background: i < filled ? FILL_COLOR[quality] : 'rgba(148,163,184,0.35)',
          }}
        />
      ))}
    </span>
  );
}

export function ConnectionSignal({
  quality,
  variant = 'full',
  name,
  hint,
  className = '',
}: ConnectionSignalProps) {
  const label = SIGNAL_LABEL[quality];
  const ariaLabel = name ? `Conexão de ${name}: ${label}` : `Conexão: ${label}`;
  const title = [name ? `${name} — ${label}` : label, hint ?? SIGNAL_TOOLTIP[quality]]
    .filter(Boolean)
    .join('\n');

  return (
    <span
      role="status"
      aria-label={ariaLabel}
      title={title}
      className={`inline-flex items-center gap-1.5 whitespace-nowrap ${className}`}
    >
      <span aria-hidden="true">{SIGNAL_EMOJI[quality]}</span>
      <Bars quality={quality} />
      {variant === 'full' && <span>{label}</span>}
    </span>
  );
}
