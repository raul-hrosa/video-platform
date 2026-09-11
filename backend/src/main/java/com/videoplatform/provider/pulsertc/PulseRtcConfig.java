package com.videoplatform.provider.pulsertc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videoplatform.provider.MediaConnectionInfoProvider;
import com.videoplatform.provider.MediaWebhookParser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/**
 * Beans de infraestrutura do PulseRTC. Ativa quando {@code media.provider=pulsertc}
 * (Sprint 11 §17). Enquanto inativo, o LiveKit segue como fallback.
 */
@Configuration
@ConditionalOnProperty(prefix = "media", name = "provider", havingValue = "pulsertc")
class PulseRtcConfig {

    @Bean
    PulseRtcClient pulseRtcClient(PulseRtcProperties properties, RestClient.Builder builder,
                                  ObjectMapper objectMapper) {
        return new PulseRtcClient(properties, builder, objectMapper);
    }

    @Bean
    PulseRtcMediaProvider pulseRtcMediaProvider(PulseRtcClient client, PulseRtcProperties properties) {
        return new PulseRtcMediaProvider(client, properties);
    }

    @Bean
    MediaWebhookParser pulseRtcWebhookParser(ObjectMapper objectMapper, PulseRtcProperties properties,
                                             Clock clock) {
        return new PulseRtcWebhookParser(objectMapper, properties, clock);
    }

    @Bean
    MediaConnectionInfoProvider pulseRtcConnectionInfoProvider(PulseRtcProperties properties) {
        return properties::resolvedServerUrl;
    }
}
