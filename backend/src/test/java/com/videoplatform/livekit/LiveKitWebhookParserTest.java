package com.videoplatform.livekit;

import com.videoplatform.provider.MediaWebhookEvent;
import com.videoplatform.provider.MediaWebhookVerificationException;
import io.livekit.server.WebhookReceiver;
import livekit.LivekitModels;
import livekit.LivekitWebhook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveKitWebhookParserTest {

    @Mock
    private WebhookReceiver webhookReceiver;

    private static final long TS = Instant.parse("2026-08-28T14:00:00Z").getEpochSecond();

    private LivekitWebhook.WebhookEvent event(String type, String id) {
        return LivekitWebhook.WebhookEvent.newBuilder()
                .setEvent(type).setId(id).setCreatedAt(TS)
                .setRoom(LivekitModels.Room.newBuilder().setName("room-1").build())
                .setParticipant(LivekitModels.ParticipantInfo.newBuilder()
                        .setIdentity("user:abc").setName("Joao").setJoinedAt(TS).build())
                .build();
    }

    @Test
    void translatesParticipantJoinedToPlatformModel() {
        when(webhookReceiver.receive(anyString(), any())).thenReturn(event("participant_joined", "EV_1"));

        MediaWebhookEvent result = new LiveKitWebhookParser(webhookReceiver).parse("{}", "Bearer ok");

        assertThat(result.type()).isEqualTo(MediaWebhookEvent.Type.PARTICIPANT_JOINED);
        assertThat(result.rawType()).isEqualTo("participant_joined");
        assertThat(result.eventId()).isEqualTo("EV_1");
        assertThat(result.roomId()).isEqualTo("room-1");
        assertThat(result.participantId()).isEqualTo("user:abc");
        assertThat(result.participantName()).isEqualTo("Joao");
        assertThat(result.occurredAt()).isEqualTo(Instant.ofEpochSecond(TS));
    }

    @Test
    void unknownEventTypeMapsToOther() {
        when(webhookReceiver.receive(anyString(), any())).thenReturn(event("track_published", "EV_2"));

        MediaWebhookEvent result = new LiveKitWebhookParser(webhookReceiver).parse("{}", "Bearer ok");

        assertThat(result.type()).isEqualTo(MediaWebhookEvent.Type.OTHER);
    }

    @Test
    void missingIdFallsBackToBodyHash() {
        when(webhookReceiver.receive(anyString(), any())).thenReturn(event("room_started", ""));

        MediaWebhookEvent result = new LiveKitWebhookParser(webhookReceiver).parse("{\"a\":1}", "Bearer ok");

        assertThat(result.eventId()).startsWith("sha256:");
    }

    @Test
    void invalidSignatureRaisesVerificationException() {
        when(webhookReceiver.receive(anyString(), any()))
                .thenThrow(new IllegalArgumentException("sha256 checksum of body does not match!"));

        assertThatThrownBy(() -> new LiveKitWebhookParser(webhookReceiver).parse("{}", "bad"))
                .isInstanceOf(MediaWebhookVerificationException.class);
    }
}
