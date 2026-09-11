package com.videoplatform.provider;

/**
 * Fornece a URL publica do signaling/media plane que o backend entrega ao
 * browser junto do token (Sprint 11 §6). A credencial de aplicacao do provider
 * (API key / secret) nunca atravessa esta fronteira.
 */
public interface MediaConnectionInfoProvider {

    /** URL {@code wss://...} que o cliente usa para conectar ao provider. */
    String serverUrl();
}
