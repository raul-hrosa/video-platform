package com.videoplatform.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Selecao central do provider de midia (Sprint 11 §17). {@code livekit} e o
 * padrao / fallback durante a validacao; {@code pulsertc} ativa o adapter novo.
 */
@ConfigurationProperties(prefix = "media")
public record MediaProperties(String provider) {

    public MediaProperties {
        if (provider == null || provider.isBlank()) {
            provider = "livekit";
        }
        provider = provider.trim().toLowerCase();
    }

    public boolean isPulseRtc() {
        return "pulsertc".equals(provider);
    }

    public boolean isLiveKit() {
        return "livekit".equals(provider);
    }
}
