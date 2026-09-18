/** Configuracao lida das variaveis de ambiente do Vite. */
export type MediaProvider = 'livekit';

export const config = {
  /** Provider de midia ativo (Sprint 11 §13/§17). Deve casar com MEDIA_PROVIDER do backend. */
  mediaProvider: 'livekit' as MediaProvider,
  livekitUrl: import.meta.env.VITE_LIVEKIT_URL ?? '',
  apiBaseUrl: import.meta.env.VITE_API_URL ?? '/api',
};

/** URL de conexao configurada localmente para o provider ativo. */
export function configuredServerUrl(): string {
  return config.livekitUrl;
}

export function assertLivekitConfigured(): void {
  if (config.mediaProvider === 'livekit' && !config.livekitUrl) {
    throw new Error('VITE_LIVEKIT_URL nao configurada');
  }
}
