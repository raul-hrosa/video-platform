/**
 * Modo diagnóstico (Sprint 13 §9) — área **opcional** para usuários técnicos.
 * Aqui, e só aqui, aparecem score / RTT / jitter / packet loss / bitrate / FPS
 * e o `reason` cru do PulseRTC. A interface normal da chamada não mostra nada
 * disso (§8, §9).
 */
import type { ParticipantQuality, StreamQuality } from './types';

function fmtMetric(key: string, value: unknown): string | null {
  if (typeof value !== 'number') return null;
  switch (key) {
    case 'rttMs':
    case 'jitterMs':
      return `${key === 'rttMs' ? 'RTT' : 'Jitter'}: ${Math.round(value)} ms`;
    case 'packetLossPct':
      return `Packet Loss: ${value.toFixed(1)}%`;
    case 'bitrateBps':
      return `Bitrate: ${Math.round(value / 1000)} kbps`;
    case 'fps':
      return `FPS: ${Math.round(value)}`;
    case 'frameDropPct':
      return `Frame drop: ${value.toFixed(1)}%`;
    default:
      return `${key}: ${value}`;
  }
}

function Section({ title, stream }: { title: string; stream?: StreamQuality }) {
  if (!stream) return null;
  const lines = Object.entries(stream.metrics ?? {})
    .map(([k, v]) => fmtMetric(k, v))
    .filter(Boolean) as string[];
  return (
    <div className="mb-2">
      <div className="font-semibold text-slate-200">{title}</div>
      <div className="text-slate-400">Status: {stream.level}</div>
      {stream.score != null && <div className="text-slate-400">Score: {stream.score}</div>}
      {lines.map((l) => (
        <div key={l} className="text-slate-400">
          {l}
        </div>
      ))}
    </div>
  );
}

export function DiagnosticPanel({
  self,
  onClose,
}: {
  self?: ParticipantQuality;
  onClose: () => void;
}) {
  return (
    <div className="absolute right-3 top-14 z-20 w-64 rounded-lg border border-slate-700 bg-slate-900/95 p-3 text-xs">
      <div className="mb-2 flex items-center justify-between">
        <span className="font-semibold text-slate-100">Diagnóstico</span>
        <button type="button" onClick={onClose} className="text-slate-400 hover:text-slate-200">
          fechar
        </button>
      </div>
      {!self && <div className="text-slate-400">Sem dados de qualidade ainda.</div>}
      {self && (
        <>
          <Section
            title="Connection"
            stream={self.connection ?? { level: self.level, score: self.score, metrics: self.metrics }}
          />
          <Section title="Audio" stream={self.audio} />
          <Section title="Video" stream={self.video} />
          {self.reason && <div className="mt-1 text-slate-500">Reason: {self.reason}</div>}
        </>
      )}
    </div>
  );
}
