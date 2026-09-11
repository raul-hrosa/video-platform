/**
 * Indicador de estado do microfone de um participante (Sprint 14 §6, §7, §13-§16).
 *
 * Só apresenta — zero lógica WebRTC. Não depende só do ícone: `aria-label` +
 * `title` sempre. `muted === undefined` (estado ainda desconhecido, §16) → não
 * renderiza nada. Coexiste com o `ConnectionSignal` (informações diferentes: 🔇
 * nunca é qualidade).
 */
export interface ParticipantMuteIndicatorProps {
  muted: boolean | undefined;
  /** Nome do participante — muda o tooltip para "O microfone de João está mutado". */
  name?: string;
  /** `self` usa 1ª pessoa ("Você"). */
  self?: boolean;
  className?: string;
}

export function ParticipantMuteIndicator({
  muted,
  name,
  self = false,
  className = '',
}: ParticipantMuteIndicatorProps) {
  if (muted === undefined) return null;

  const label = muted ? 'Microfone mutado' : 'Microfone ativado';
  const title = name && !self ? `O microfone de ${name} está ${muted ? 'mutado' : 'ativado'}` : label;

  return (
    <span role="img" aria-label={label} title={title} className={className}>
      {muted ? '🔇' : '🎤'}
    </span>
  );
}
