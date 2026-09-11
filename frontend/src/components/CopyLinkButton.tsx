import { useEffect, useRef, useState } from 'react';
import { LogEvent, logger } from '../services/logger';
import { roomLink } from '../services/roomLink';

interface Props {
  /** Sala: gera `${origin}/room/${roomId}`. */
  roomId?: string;
  /** Link literal — tem prioridade sobre {@link roomId} (Sprint 8: link de atendimento). */
  link?: string;
  className?: string;
}

const BASE =
  'rounded-lg bg-slate-700 px-3 py-2 text-sm font-medium text-slate-100 transition hover:bg-slate-600';

/** Botao "Copiar link" com feedback "Link copiado!" por 2s (Sprint 5 §24, §47). */
export function CopyLinkButton({ roomId, link, className }: Props) {
  const [copied, setCopied] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout>>();

  useEffect(() => () => clearTimeout(timer.current), []);

  async function copy() {
    const target = link ?? (roomId ? roomLink(roomId) : '');
    if (!target) return;
    try {
      await navigator.clipboard.writeText(target);
      logger.info({ event: LogEvent.ROOM_LINK_COPIED, roomId });
      setCopied(true);
      clearTimeout(timer.current);
      timer.current = setTimeout(() => setCopied(false), 2000);
    } catch {
      // area de transferencia bloqueada pelo browser — o usuario copia manualmente
    }
  }

  return (
    <button type="button" onClick={copy} className={className ?? BASE}>
      {copied ? 'Link copiado!' : '🔗 Copiar link'}
    </button>
  );
}
