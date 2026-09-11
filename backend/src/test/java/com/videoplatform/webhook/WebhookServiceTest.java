package com.videoplatform.webhook;

import com.videoplatform.common.ApiException;
import com.videoplatform.participant.ParticipantSessionService;
import com.videoplatform.provider.MediaWebhookEvent;
import com.videoplatform.provider.MediaWebhookParser;
import com.videoplatform.provider.MediaWebhookVerificationException;
import com.videoplatform.room.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private MediaWebhookParser webhookParser;
    @Mock
    private WebhookEventRepository webhookEventRepository;
    @Mock
    private RoomService roomService;
    @Mock
    private ParticipantSessionService participantSessionService;

    private WebhookService service;

    private static final UUID UID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final Instant TS = Instant.parse("2026-08-28T14:00:00Z");

    @BeforeEach
    void setUp() {
        service = new WebhookService(webhookParser, webhookEventRepository,
                roomService, participantSessionService);
    }

    private MediaWebhookEvent event(MediaWebhookEvent.Type type, String rawType, String id) {
        return new MediaWebhookEvent(type, rawType, id, TS,
                "room-1", "user:" + UID, "Joao", TS);
    }

    private void stubParse(MediaWebhookEvent event) {
        when(webhookParser.parse(anyString(), any())).thenReturn(event);
        when(webhookEventRepository.existsByEventId(anyString())).thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void participantJoinedCreatesSessionAndActivatesRoom() {
        stubParse(event(MediaWebhookEvent.Type.PARTICIPANT_JOINED, "participant_joined", "EV_1"));

        WebhookService.WebhookResult result = service.process("{}", "Bearer token");

        assertThat(result.processed()).isTrue();
        verify(participantSessionService).startSession(eq("room-1"), eq("user:" + UID), eq(UID),
                eq("Joao"), eq(TS));
        verify(roomService).markActive(eq("room-1"), eq(TS));
    }

    @Test
    void participantLeftEndsSession() {
        stubParse(event(MediaWebhookEvent.Type.PARTICIPANT_LEFT, "participant_left", "EV_2"));

        service.process("{}", "Bearer token");

        verify(participantSessionService).endSession(eq("room-1"), eq("user:" + UID), eq(TS));
    }

    @Test
    void roomStartedMarksRoomActive() {
        stubParse(event(MediaWebhookEvent.Type.ROOM_STARTED, "room_started", "EV_3"));

        service.process("{}", "Bearer token");

        verify(roomService).markActive(eq("room-1"), eq(TS));
        verify(participantSessionService, never()).startSession(any(), any(), any(), any(), any());
    }

    @Test
    void roomFinishedMarksRoomEnded() {
        stubParse(event(MediaWebhookEvent.Type.ROOM_FINISHED, "room_finished", "EV_4"));

        service.process("{}", "Bearer token");

        verify(roomService).markEnded(eq("room-1"), eq(TS));
    }

    @Test
    void duplicateEventIsIgnored() {
        when(webhookParser.parse(anyString(), any()))
                .thenReturn(event(MediaWebhookEvent.Type.PARTICIPANT_JOINED, "participant_joined", "EV_1"));
        when(webhookEventRepository.existsByEventId("EV_1")).thenReturn(true);

        WebhookService.WebhookResult result = service.process("{}", "Bearer token");

        assertThat(result.processed()).isFalse();
        assertThat(result.reason()).isEqualTo("ALREADY_PROCESSED");
        verify(webhookEventRepository, never()).save(any());
        verify(participantSessionService, never()).startSession(any(), any(), any(), any(), any());
    }

    @Test
    void unknownEventTypeIsIgnoredButRecorded() {
        stubParse(event(MediaWebhookEvent.Type.OTHER, "track_published", "EV_9"));

        WebhookService.WebhookResult result = service.process("{}", "Bearer token");

        assertThat(result.processed()).isFalse();
        assertThat(result.reason()).isEqualTo("UNHANDLED_EVENT");
        verify(webhookEventRepository).save(any(WebhookEvent.class));
    }

    @Test
    void invalidSignatureThrowsWebhookInvalid() {
        when(webhookParser.parse(anyString(), any()))
                .thenThrow(new MediaWebhookVerificationException("invalid webhook signature", null));

        assertThatThrownBy(() -> service.process("{}", "bad"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getErrorCode()).isEqualTo("WEBHOOK_INVALID");
                    assertThat(api.getStatus().value()).isEqualTo(401);
                });
    }
}
