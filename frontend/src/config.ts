/** Configuracao lida das variaveis de ambiente do Vite. */
export type MediaProvider = 'livekit' | 'pulsertc';

const rawProvider = (import.meta.env.VITE_MEDIA_PROVIDER ?? 'livekit').toLowerCase();

export const config = {
  /** Provider de midia ativo (Sprint 11 §13/§17). Deve casar com MEDIA_PROVIDER do backend. */
  mediaProvider: (rawProvider === 'pulsertc' ? 'pulsertc' : 'livekit') as MediaProvider,
  livekitUrl: import.meta.env.VITE_LIVEKIT_URL ?? '',
  /** Fallback do signaling plane do PulseRTC; o backend tambem manda `serverUrl` no token (§6). */
  pulsertcUrl: import.meta.env.VITE_PULSERTC_URL ?? '',
  apiBaseUrl: import.meta.env.VITE_API_URL ?? '/api',
};

/** URL de conexao configurada localmente para o provider ativo. */
export function configuredServerUrl(): string {
  return config.mediaProvider === 'pulsertc' ? config.pulsertcUrl : config.livekitUrl;
}

export function assertLivekitConfigured(): void {
  if (config.mediaProvider === 'livekit' && !config.livekitUrl) {
    throw new Error('VITE_LIVEKIT_URL nao configurada');
  }
}
