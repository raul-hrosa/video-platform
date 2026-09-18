package com.videoplatform.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Selecao central do provider de midia (Sprint 11 §17). {@code livekit} e o
 * unico provider hoje; a chave existe para permitir plugar outro adapter no
 * futuro sem mexer nos consumidores (ver {@code MediaTokenProvider} e cia).
 */
@ConfigurationProperties(prefix = "media")
public record MediaProperties(String provider) {

    public MediaProperties {
        if (provider == null || provider.isBlank()) {
            provider = "livekit";
        }
        provider = provider.trim().toLowerCase();
    }

    public boolean isLiveKit() {
        return "livekit".equals(provider);
    }
}
