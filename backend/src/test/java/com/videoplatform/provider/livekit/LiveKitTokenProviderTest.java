package com.videoplatform.provider.livekit;

import com.videoplatform.common.ApiException;
import com.videoplatform.livekit.LiveKitProperties;
import com.videoplatform.provider.MediaTokenProvider;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class LiveKitTokenProviderTest {

    private static final String API_KEY = "APItestkey123";
    private static final String API_SECRET = "supersecretvalueusedonlyfortests_padding";
    private static final String IDENTITY = "user:11111111-1111-1111-1111-111111111111";

    private LiveKitTokenProvider configuredProvider() {
        return new LiveKitTokenProvider(new LiveKitProperties(
                "wss://example.livekit.cloud", API_KEY, API_SECRET, Duration.ofHours(1)));
    }

    private String decodePayload(String jwt) {
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);
        return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    }

    @Test
    void generatesTokenWithGivenIdentityAndDisplayName() {
        MediaTokenProvider.MediaToken result = configuredProvider().createRoomToken("room-demo", IDENTITY, "Joao");

        String payload = decodePayload(result.token());
        assertThat(payload).contains("\"room\":\"room-demo\"");
        assertThat(payload).contains("\"roomJoin\":true");
        assertThat(payload).contains("\"name\":\"Joao\"");
        assertThat(payload).contains("\"sub\":\"" + IDENTITY + "\"");
        assertThat(result.participantRef()).isEqualTo(IDENTITY);
    }

    @Test
    void failsWhenLiveKitNotConfigured() {
        LiveKitTokenProvider provider = new LiveKitTokenProvider(
                new LiveKitProperties("", "", "", Duration.ofHours(1)));

        ApiException ex = catchThrowableOfType(
                () -> provider.createRoomToken("room-demo", IDENTITY, "Joao"), ApiException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().value()).isEqualTo(503);
        assertThat(ex.getErrorCode()).isEqualTo("LIVEKIT_NOT_CONFIGURED");
    }
}
