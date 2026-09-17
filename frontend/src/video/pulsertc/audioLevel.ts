/**
 * Medidor de volume por stream (Sprint 16). Web Audio: um `AnalyserNode` por
 * stream, amostrado num `requestAnimationFrame`, devolve um nível 0..1.
 *
 * - Um único `AudioContext` compartilhado (browsers limitam quantos existem).
 * - O analyser **não** é ligado ao `destination` — não afeta a reprodução, que
 *   continua saindo pelo `<video>`/`<audio>` do tile.
 */
import { useEffect, useRef, useState } from 'react';

type AudioCtx = typeof AudioContext;

let ctx: AudioContext | null = null;

function getCtx(): AudioContext | null {
  if (typeof window === 'undefined') return null;
  const Ctor: AudioCtx | undefined =
    window.AudioContext ?? (window as unknown as { webkitAudioContext?: AudioCtx }).webkitAudioContext;
  if (!Ctor) return null;
  if (!ctx) {
    try {
      ctx = new Ctor();
    } catch {
      return null;
    }
  }
  if (ctx.state === 'suspended') void ctx.resume().catch(() => undefined);
  return ctx;
}

// O AudioContext pode nascer suspenso; retoma na primeira interação do usuário.
if (typeof window !== 'undefined') {
  const resume = () => ctx?.resume().catch(() => undefined);
  window.addEventListener('pointerdown', resume, { passive: true });
  window.addEventListener('keydown', resume, { passive: true });
}

interface Reader {
  read(): number;
  dispose(): void;
}

function createReader(stream: MediaStream): Reader | null {
  if (stream.getAudioTracks().length === 0) return null;
  const c = getCtx();
  if (!c) return null;
  let source: MediaStreamAudioSourceNode;
  let analyser: AnalyserNode;
  try {
    source = c.createMediaStreamSource(stream);
    analyser = c.createAnalyser();
    analyser.fftSize = 512;
    analyser.smoothingTimeConstant = 0.6;
    source.connect(analyser);
  } catch {
    return null;
  }
  const buf = new Uint8Array(analyser.fftSize);
  let disposed = false;
  return {
    read() {
      if (disposed) return 0;
      analyser.getByteTimeDomainData(buf);
      let sum = 0;
      for (let i = 0; i < buf.length; i += 1) {
        const v = (buf[i] - 128) / 128;
        sum += v * v;
      }
      // RMS ~0..0.4 na fala normal — escala pra usar a faixa 0..1.
      return Math.min(1, Math.sqrt(sum / buf.length) * 2.8);
    },
    dispose() {
      disposed = true;
      try {
        source.disconnect();
      } catch {
        /* ctx já fechado */
      }
    },
  };
}

/**
 * Nível de áudio 0..1 do stream, atualizado ~a cada frame (com histerese leve
 * para não spammar re-render). `active = false` desliga o medidor.
 */
export function useAudioLevel(stream: MediaStream | null, active = true): number {
  const [level, setLevel] = useState(0);
  const audioTrackId = stream?.getAudioTracks()[0]?.id;

  useEffect(() => {
    if (!stream || !active) {
      setLevel(0);
      return;
    }
    const reader = createReader(stream);
    if (!reader) return;
    let raf = 0;
    let shown = 0;
    const loop = () => {
      const next = reader.read();
      // decaimento suave + só re-renderiza em mudança perceptível
      const smoothed = Math.max(next, shown * 0.82);
      if (Math.abs(smoothed - shown) > 0.03 || (smoothed === 0 && shown !== 0)) {
        shown = smoothed;
        setLevel(smoothed);
      }
      raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);
    return () => {
      cancelAnimationFrame(raf);
      reader.dispose();
    };
  }, [stream, audioTrackId, active]);

  return level;
}

/**
 * Roteia o áudio do `stream` por um `GainNode` (0..∞) ligado ao
 * `AudioContext.destination`. O gain padrão 1 = volume original; 2 = dobro.
 * Quem chama deve silenciar o elemento <video>/<audio> para evitar eco.
 */
export function useAudioGain(stream: MediaStream | null, gain: number, enabled = true): void {
  const gainNodeRef = useRef<GainNode | null>(null);
  const audioTrackId = stream?.getAudioTracks()[0]?.id ?? null;

  useEffect(() => {
    if (!stream || !enabled || stream.getAudioTracks().length === 0) {
      gainNodeRef.current = null;
      return;
    }
    const c = getCtx();
    if (!c) return;
    let source: MediaStreamAudioSourceNode;
    let gainNode: GainNode;
    try {
      source = c.createMediaStreamSource(stream);
      gainNode = c.createGain();
      gainNode.gain.value = Math.max(0, gain);
      source.connect(gainNode);
      gainNode.connect(c.destination);
      gainNodeRef.current = gainNode;
    } catch {
      gainNodeRef.current = null;
      return;
    }
    return () => {
      try {
        source.disconnect();
        gainNode.disconnect();
      } catch { /* ctx já fechado */ }
      gainNodeRef.current = null;
    };
  // audioTrackId rastreia troca de track sem recriar desnecessariamente
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [audioTrackId, enabled]);

  useEffect(() => {
    if (gainNodeRef.current) {
      gainNodeRef.current.gain.value = Math.max(0, gain);
    }
  }, [gain]);
}
