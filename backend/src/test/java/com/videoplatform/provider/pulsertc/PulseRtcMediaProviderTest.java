package com.videoplatform.provider.pulsertc;

import com.videoplatform.common.ApiException;
import com.videoplatform.provider.MediaTokenProvider;
import com.videoplatform.provider.ConnectionQuality;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.CreateTokenRequest;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.ParticipantQualityView;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.QualityVerdict;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.RoomQualityView;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.RoomView;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.TokenView;
import com.videoplatform.provider.pulsertc.PulseRtcMediaProvider.ParticipantQualitySnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PulseRtcMediaProviderTest {

    private static final PulseRtcProperties CONFIGURED = new PulseRtcProperties(
            "http://pulse.test", "key", null, null, Duration.ofHours(1), Duration.ofMinutes(5));

    private final PulseRtcClient client = mock(PulseRtcClient.class);

    private PulseRtcMediaProvider provider(PulseRtcProperties props) {
        return new PulseRtcMediaProvider(client, props);
    }

    @Test
    void createsRoomThenTokenWithParticipantPermissions() {
        when(client.createRoom("room-1")).thenReturn(new RoomView("room-1", "OPEN"));
        when(client.createToken(eq("room-1"), any()))
                .thenReturn(new TokenView("jwt", "user:1", "room-1", null, null));

        MediaTokenProvider.MediaToken token = provider(CONFIGURED).createRoomToken("room-1", "user:1", "Ana");

        assertThat(token.token()).isEqualTo("jwt");
        assertThat(token.participantRef()).isEqualTo("user:1");

        ArgumentCaptor<CreateTokenRequest> captor = ArgumentCaptor.forClass(CreateTokenRequest.class);
        verify(client).createToken(eq("room-1"), captor.capture());
        assertThat(captor.getValue().permissions().join()).isTrue();
        assertThat(captor.getValue().permissions().control()).isFalse();
        assertThat(captor.getValue().ttlSeconds()).isEqualTo(3600);
    }

    @Test
    void idempotencyConflictOnRoomIsSwallowed() {
        doThrow(new PulseRtcApiException(409, "IDEMPOTENCY_CONFLICT", null, "dup"))
                .when(client).createRoom("room-1");
        when(client.createToken(eq("room-1"), any()))
                .thenReturn(new TokenView("jwt", "user:1", "room-1", null, null));

        MediaTokenProvider.MediaToken token = provider(CONFIGURED).createRoomToken("room-1", "user:1", "Ana");

        assertThat(token.token()).isEqualTo("jwt");
    }

    @Test
    void roomClosedMapsToConflictApiException() {
        doThrow(new PulseRtcApiException(409, "ROOM_CLOSED", null, "closed"))
                .when(client).createRoom("room-1");

        ApiException ex = catchThrowableOfType(
                () -> provider(CONFIGURED).createRoomToken("room-1", "user:1", "Ana"), ApiException.class);

        assertThat(ex.getErrorCode()).isEqualTo("ROOM_CLOSED");
        assertThat(ex.getStatus().value()).isEqualTo(409);
    }

    @Test
    void roomQualityBreakdownTranslatesPulseStatusToPlatformScale() {
        when(client.getRoomQuality("room-1")).thenReturn(new RoomQualityView(List.of(
                new ParticipantQualityView("user:1.abcd1234", "WARNING", 62, "HIGH_JITTER", null,
                        new QualityVerdict("GOOD", 80, null, null),
                        new QualityVerdict("POOR", 40, "HIGH_PACKET_LOSS", java.util.Map.of("packetLossPct", 4.2)),
                        new QualityVerdict("WARNING", 55, null, null)))));

        List<ParticipantQualitySnapshot> breakdown = provider(CONFIGURED).roomQualityBreakdown("room-1");

        assertThat(breakdown).singleElement().satisfies(s -> {
            assertThat(s.participantRef()).isEqualTo("user:1.abcd1234");
            assertThat(s.level()).isEqualTo(ConnectionQuality.UNSTABLE);
            assertThat(s.score()).isEqualTo(62);
            assertThat(s.reason()).isEqualTo("HIGH_JITTER");
            assertThat(s.audio().level()).isEqualTo(ConnectionQuality.GOOD);
            assertThat(s.video().level()).isEqualTo(ConnectionQuality.POOR);
            assertThat(s.video().metrics()).containsEntry("packetLossPct", 4.2);
            assertThat(s.connection().level()).isEqualTo(ConnectionQuality.UNSTABLE);
        });
    }

    @Test
    void notConfiguredFailsFastWithoutCallingProvider() {
        PulseRtcProperties blank = new PulseRtcProperties(null, null, null, null, null, null);

        ApiException ex = catchThrowableOfType(
                () -> provider(blank).createRoomToken("room-1", "user:1", "Ana"), ApiException.class);

        assertThat(ex.getStatus().value()).isEqualTo(503);
        assertThat(ex.getErrorCode()).isEqualTo("MEDIA_PROVIDER_NOT_CONFIGURED");
    }
}
