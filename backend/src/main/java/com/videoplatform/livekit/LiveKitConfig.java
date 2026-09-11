package com.videoplatform.livekit;

import io.livekit.server.WebhookReceiver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Beans de infraestrutura do LiveKit. Ativa quando {@code media.provider=livekit}
 * (padrao) — Sprint 11 §17.
 */
@Configuration
@ConditionalOnProperty(prefix = "media", name = "provider", havingValue = "livekit", matchIfMissing = true)
public class LiveKitConfig {

    /**
     * Valida a autenticidade dos webhooks do LiveKit (JWT HMAC256 + checksum
     * SHA-256 do corpo), conforme o SDK oficial.
     */
    @Bean
    public WebhookReceiver liveKitWebhookReceiver(LiveKitProperties properties) {
        return new WebhookReceiver(
                properties.apiKey() == null ? "" : properties.apiKey(),
                properties.apiSecret() == null ? "" : properties.apiSecret());
    }
}
