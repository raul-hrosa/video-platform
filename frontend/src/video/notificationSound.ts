/**
 * Sinais sonoros curtos de entrada/saída de participante (sintetizados via Web
 * Audio — sem depender de arquivo de mídia externo). Best-effort: falha
 * silenciosamente se o navegador bloquear áudio automático ou não suportar
 * `AudioContext`.
 */

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

/** Toca uma sequência de tons (frequência em Hz, duração em segundos) a partir de `startAt`. */
function playTones(tones: { freq: number; duration: number }[]): void {
  const c = getCtx();
  if (!c) return;
  try {
    let t = c.currentTime;
    for (const { freq, duration } of tones) {
      const osc = c.createOscillator();
      const gain = c.createGain();
      osc.type = 'sine';
      osc.frequency.value = freq;
      // envelope curto (attack/release) para evitar clique no início/fim do tom
      gain.gain.setValueAtTime(0, t);
      gain.gain.linearRampToValueAtTime(0.2, t + 0.015);
      gain.gain.linearRampToValueAtTime(0, t + duration);
      osc.connect(gain);
      gain.connect(c.destination);
      osc.start(t);
      osc.stop(t + duration);
      t += duration;
    }
  } catch {
    /* best-effort: sem som em caso de falha do Web Audio */
  }
}

/** Dois tons ascendentes — alguém entrou na sala. */
export function playParticipantJoinedSound(): void {
  playTones([
    { freq: 660, duration: 0.1 },
    { freq: 880, duration: 0.12 },
  ]);
}

/** Dois tons descendentes — alguém saiu da sala. */
export function playParticipantLeftSound(): void {
  playTones([
    { freq: 660, duration: 0.1 },
    { freq: 440, duration: 0.12 },
  ]);
}
