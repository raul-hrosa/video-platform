/**
 * Tradução visual dos códigos de razão do PulseRTC (Sprint 12 §18). Centralizada
 * aqui — não espalhar traduções pelos componentes. Código desconhecido cai num
 * "humanize" genérico em vez de quebrar.
 */
const LABELS: Record<string, string> = {
  HIGH_PACKET_LOSS: 'Alta perda de pacotes',
  HIGH_JITTER: 'Jitter elevado',
  HIGH_RTT: 'Latência elevada',
  HIGH_LATENCY: 'Latência elevada',
  LOW_BITRATE: 'Bitrate baixo',
  BANDWIDTH_LIMITED: 'Banda limitada',
  FRAME_DROPS: 'Perda de quadros',
  HIGH_FRAME_DROP: 'Perda de quadros',
  ICE_DISCONNECTED: 'Conexão de rede instável',
  CONNECTION_LOST: 'Conexão perdida',
  NO_MEDIA: 'Sem mídia recebida',
  RECOVERING: 'Reconectando',
};

export function reasonLabel(code: string | null | undefined): string | null {
  if (!code) return null;
  const known = LABELS[code.toUpperCase()];
  if (known) return known;
  return code
    .toLowerCase()
    .replace(/[_-]+/g, ' ')
    .replace(/^\w/, (c) => c.toUpperCase());
}
