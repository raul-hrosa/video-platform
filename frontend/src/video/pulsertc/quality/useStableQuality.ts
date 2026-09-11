/**
 * Histerese **de apresentação** para o indicador de conexão (Sprint 13 §13).
 * Não altera a classificação oficial do PulseRTC — só evita o indicador piscar
 * (GOOD↔UNSTABLE↔GOOD em segundos). `UNKNOWN` passa direto nos dois sentidos:
 * durante reconnect queremos mostrar "Verificando" já, e sair dele rápido.
 */
import { useEffect, useRef, useState } from 'react';
import type { QualityLevel } from '../../../types/connectionQuality';

export function useStableQuality(input: QualityLevel, dwellMs = 5000): QualityLevel {
  const [shown, setShown] = useState<QualityLevel>(input);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (input === shown) {
      if (timer.current) {
        clearTimeout(timer.current);
        timer.current = null;
      }
      return;
    }
    if (input === 'UNKNOWN' || shown === 'UNKNOWN') {
      setShown(input);
      return;
    }
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => setShown(input), dwellMs);
    return () => {
      if (timer.current) clearTimeout(timer.current);
    };
  }, [input, shown, dwellMs]);

  return shown;
}
