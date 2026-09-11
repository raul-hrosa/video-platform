package com.videoplatform.livekit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuracao do LiveKit lida de variaveis de ambiente.
 * O api-secret nunca deve ser logado nem exposto em respostas.
 */
@ConfigurationProperties(prefix = "livekit")
public record LiveKitProperties(
        String url,
        String apiKey,
        String apiSecret,
        Duration tokenTtl
) {

    public LiveKitProperties {
        if (tokenTtl == null) {
            tokenTtl = Duration.ofHours(1);
        }
    }

    public boolean isConfigured() {
        return hasText(url) && hasText(apiKey) && hasText(apiSecret);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
