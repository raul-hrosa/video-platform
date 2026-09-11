/**
 * Textos amigáveis do indicador de conexão (Sprint 13). Linguagem humana — nunca
 * códigos técnicos (`HIGH_PACKET_LOSS`) nem métricas (`score`, `bitrate`) na UI
 * principal. Centralizado aqui: não espalhar traduções pelos componentes.
 */
import type { QualityLevel } from '../types/connectionQuality';

/** Barras estilo sinal de celular por estado (§3). */
export const SIGNAL_BARS: Record<QualityLevel, string> = {
  EXCELLENT: '▂▄▆█',
  GOOD: '▂▄▆_',
  UNSTABLE: '▂▄__',
  POOR: '▂___',
  UNKNOWN: '____',
};

/** Nº de barras preenchidas (para render por elemento, acessível). */
export const SIGNAL_FILLED: Record<QualityLevel, number> = {
  EXCELLENT: 4,
  GOOD: 3,
  UNSTABLE: 2,
  POOR: 1,
  UNKNOWN: 0,
};

/** Emoji do estado — no Sprint 13 GOOD volta a ser 🟢 (§3). */
export const SIGNAL_EMOJI: Record<QualityLevel, string> = {
  EXCELLENT: '🟢',
  GOOD: '🟢',
  UNSTABLE: '🟡',
  POOR: '🔴',
  UNKNOWN: '⚪',
};

/** Label curto do estado (§3). */
export const SIGNAL_LABEL: Record<QualityLevel, string> = {
  EXCELLENT: 'Excelente',
  GOOD: 'Boa',
  UNSTABLE: 'Instável',
  POOR: 'Ruim',
  UNKNOWN: 'Verificando conexão...',
};

/** Tooltip / descrição por estado, do ponto de vista do próprio usuário (§4). */
export const SIGNAL_TOOLTIP: Record<QualityLevel, string> = {
  EXCELLENT: 'Sua conexão está excelente.',
  GOOD: 'Sua conexão está boa.',
  UNSTABLE:
    'Sua conexão está instável. Você pode perceber pequenos atrasos ou interrupções no áudio e vídeo.',
  POOR: 'Sua conexão está ruim. Áudio e vídeo podem apresentar interrupções.',
  UNKNOWN: 'Estamos verificando a qualidade da sua conexão...',
};

/** Descrição de um participante remoto com problema (§6). */
export function remoteQualityDescription(level: QualityLevel): string | null {
  switch (level) {
    case 'POOR':
      return 'Conexão ruim. O áudio ou vídeo deste participante pode apresentar interrupções.';
    case 'UNSTABLE':
      return 'Conexão instável. Pode haver pequenos atrasos no áudio ou vídeo deste participante.';
    default:
      return null;
  }
}

/**
 * Mensagem amigável a partir do `reason` do PulseRTC (§6). Código desconhecido
 * → mensagem genérica de instabilidade (nunca o código cru).
 */
export function friendlyReason(reason: string | null | undefined): string | null {
  if (!reason) return null;
  switch (reason.toUpperCase()) {
    case 'HIGH_PACKET_LOSS':
      return 'Sua conexão está apresentando instabilidade.';
    case 'HIGH_JITTER':
      return 'Sua conexão está instável e pode causar atrasos.';
    case 'HIGH_RTT':
    case 'HIGH_LATENCY':
      return 'Sua conexão está com atraso elevado.';
    case 'LOW_BITRATE':
    case 'BANDWIDTH_LIMITED':
      return 'Sua conexão está com pouca banda disponível.';
    case 'FRAME_DROPS':
    case 'HIGH_FRAME_DROP':
      return 'O vídeo pode apresentar travamentos.';
    case 'SESSION_RECOVERY':
    case 'RECOVERING':
      return 'Reconectando à chamada...';
    case 'ICE_DISCONNECTED':
    case 'CONNECTION_LOST':
      return 'A conexão de rede caiu momentaneamente.';
    default:
      return 'Sua conexão está apresentando instabilidade.';
  }
}
