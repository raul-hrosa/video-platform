package com.videoplatform.provider.pulsertc;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuracao do PulseRTC lida de variaveis de ambiente (Sprint 11 §3/§17).
 * A {@code apiKey} e o {@code webhookSecret} sao credenciais de aplicacao e
 * nunca devem ser logados nem retornados ao frontend.
 *
 * @param apiUrl        base do control plane HTTP ({@code /v1})
 * @param apiKey        Bearer token de aplicacao
 * @param wsUrl         URL publica do signaling plane entregue ao browser
 *                      ({@code wss://.../ws}); se ausente, e derivada de apiUrl
 * @param webhookSecret segredo HMAC-SHA256 dos webhooks; default = apiKey
 * @param tokenTtl      validade dos tokens de participante
 * @param joinEarly     tolerancia de relogio para replay de webhook
 */
@ConfigurationProperties(prefix = "media.pulsertc")
public record PulseRtcProperties(
        String apiUrl,
        String apiKey,
        String wsUrl,
        String webhookSecret,
        Duration tokenTtl,
        Duration webhookTolerance
) {

    public PulseRtcProperties {
        if (tokenTtl == null) {
            tokenTtl = Duration.ofHours(1);
        }
        if (webhookTolerance == null) {
            webhookTolerance = Duration.ofMinutes(5);
        }
    }

    public boolean isConfigured() {
        return hasText(apiUrl) && hasText(apiKey);
    }

    public String resolvedWebhookSecret() {
        return hasText(webhookSecret) ? webhookSecret : apiKey;
    }

    /** URL do signaling plane entregue ao browser (nunca a apiKey). */
    public String resolvedServerUrl() {
        if (hasText(wsUrl)) {
            return wsUrl;
        }
        if (!hasText(apiUrl)) {
            return "";
        }
        String base = apiUrl.replaceFirst("^http://", "ws://").replaceFirst("^https://", "wss://");
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/ws";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
