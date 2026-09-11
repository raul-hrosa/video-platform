package com.videoplatform.provider.pulsertc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videoplatform.provider.MediaWebhookEvent;
import com.videoplatform.provider.MediaWebhookVerificationException;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PulseRtcWebhookParserTest {

    private static final String SECRET = "whsec-test";
    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    private final ObjectMapper mapper = new ObjectMapper();

    private PulseRtcWebhookParser parser(Instant clockNow) {
        PulseRtcProperties props = new PulseRtcProperties(
                "http://p", "apikey", null, SECRET, Duration.ofHours(1), Duration.ofMinutes(5));
        return new PulseRtcWebhookParser(mapper, props, Clock.fixed(clockNow, ZoneOffset.UTC));
    }

    private String sign(String body, long ts) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String hex = HexFormat.of().formatHex(
                    mac.doFinal((ts + "." + body).getBytes(StandardCharsets.UTF_8)));
            return "t=" + ts + ",v1=" + hex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    void parsesParticipantJoinedWithValidSignature() {
        String body = "{\"id\":\"ev-1\",\"type\":\"participant.joined\",\"room\":{\"id\":\"room-1\"},"
                + "\"participant\":{\"identity\":\"guest:abc\",\"name\":\"Ana\"}}";
        MediaWebhookEvent event = parser(NOW).parse(body, sign(body, NOW.getEpochSecond()));

        assertThat(event.type()).isEqualTo(MediaWebhookEvent.Type.PARTICIPANT_JOINED);
        assertThat(event.eventId()).isEqualTo("ev-1");
        assertThat(event.roomId()).isEqualTo("room-1");
        assertThat(event.participantId()).isEqualTo("guest:abc");
        assertThat(event.participantName()).isEqualTo("Ana");
    }

    @Test
    void roomClosedMapsToRoomFinished() {
        String body = "{\"id\":\"ev-2\",\"type\":\"room.closed\",\"roomId\":\"room-9\"}";
        MediaWebhookEvent event = parser(NOW).parse(body, sign(body, NOW.getEpochSecond()));
        assertThat(event.type()).isEqualTo(MediaWebhookEvent.Type.ROOM_FINISHED);
    }

    @Test
    void unknownTypeBecomesOther() {
        String body = "{\"id\":\"ev-3\",\"type\":\"track.published\"}";
        MediaWebhookEvent event = parser(NOW).parse(body, sign(body, NOW.getEpochSecond()));
        assertThat(event.type()).isEqualTo(MediaWebhookEvent.Type.OTHER);
    }

    @Test
    void rejectsTamperedBody() {
        String body = "{\"id\":\"ev-1\",\"type\":\"participant.joined\"}";
        String header = sign(body, NOW.getEpochSecond());

        assertThatThrownBy(() -> parser(NOW).parse(body + " ", header))
                .isInstanceOf(MediaWebhookVerificationException.class);
    }

    @Test
    void rejectsReplayOutsideTolerance() {
        String body = "{\"id\":\"ev-1\",\"type\":\"participant.joined\"}";
        long oldTs = NOW.minus(Duration.ofMinutes(30)).getEpochSecond();

        assertThatThrownBy(() -> parser(NOW).parse(body, sign(body, oldTs)))
                .isInstanceOf(MediaWebhookVerificationException.class);
    }

    @Test
    void rejectsMissingSignatureHeader() {
        assertThatThrownBy(() -> parser(NOW).parse("{}", null))
                .isInstanceOf(MediaWebhookVerificationException.class);
    }
}
