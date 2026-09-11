package com.videoplatform.webhook;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.videoplatform.participant.ParticipantSessionService;
import com.videoplatform.provider.MediaWebhookEvent;
import com.videoplatform.provider.MediaWebhookParser;
import com.videoplatform.provider.MediaWebhookVerificationException;
import com.videoplatform.room.RoomService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookLoggingTest {

    @Mock
    private MediaWebhookParser webhookParser;
    @Mock
    private WebhookEventRepository webhookEventRepository;
    @Mock
    private RoomService roomService;
    @Mock
    private ParticipantSessionService participantSessionService;

    private WebhookService service;
    private ListAppender<ILoggingEvent> appender;
    private Logger serviceLogger;

    @BeforeEach
    void setUp() {
        service = new WebhookService(webhookParser, webhookEventRepository,
                roomService, participantSessionService);
        serviceLogger = (Logger) LoggerFactory.getLogger(WebhookService.class);
        appender = new ListAppender<>();
        appender.start();
        serviceLogger.addAppender(appender);
        serviceLogger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(appender);
        MDC.clear();
    }

    private List<String> loggedEvents() {
        return appender.list.stream()
                .flatMap(e -> e.getKeyValuePairs() == null ? java.util.stream.Stream.empty()
                        : e.getKeyValuePairs().stream())
                .filter(kv -> kv.key.equals("event"))
                .map(kv -> String.valueOf(kv.value))
                .toList();
    }

    private MediaWebhookEvent event(MediaWebhookEvent.Type type, String rawType, String id) {
        Instant ts = Instant.ofEpochSecond(1_756_000_000L);
        return new MediaWebhookEvent(type, rawType, id, ts, "room-1", "user-1", "Joao", ts);
    }

    @Test
    void logsReceivedValidatedProcessedForValidEvent() {
        when(webhookParser.parse(anyString(), any()))
                .thenReturn(event(MediaWebhookEvent.Type.ROOM_STARTED, "room_started", "EV_1"));
        when(webhookEventRepository.existsByEventId("EV_1")).thenReturn(false);
        when(webhookEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.process("{}", "Bearer ok");

        assertThat(loggedEvents())
                .containsSubsequence("WEBHOOK_RECEIVED", "WEBHOOK_VALIDATED", "WEBHOOK_PROCESSED");
    }

    @Test
    void logsRejectedForInvalidSignatureWithoutAuthHeader() {
        when(webhookParser.parse(anyString(), any()))
                .thenThrow(new MediaWebhookVerificationException("invalid webhook signature", null));

        assertThatThrownBy(() -> service.process("{}", "Bearer secret-value"))
                .isInstanceOf(RuntimeException.class);

        assertThat(loggedEvents()).contains("WEBHOOK_REJECTED");
        // o valor do header nunca deve aparecer em nenhuma mensagem/kv
        boolean leaked = appender.list.stream().anyMatch(e ->
                e.getFormattedMessage().contains("secret-value")
                        || String.valueOf(e.getKeyValuePairs()).contains("secret-value"));
        assertThat(leaked).isFalse();
    }

    @Test
    void logsDuplicatedForAlreadyProcessedEvent() {
        when(webhookParser.parse(anyString(), any()))
                .thenReturn(event(MediaWebhookEvent.Type.ROOM_STARTED, "room_started", "EV_1"));
        when(webhookEventRepository.existsByEventId("EV_1")).thenReturn(true);

        service.process("{}", "Bearer ok");

        assertThat(loggedEvents()).contains("WEBHOOK_DUPLICATED");
    }

    @Test
    void unknownEventTypeStillLogsProcessed() {
        lenient().when(webhookEventRepository.existsByEventId(anyString())).thenReturn(false);
        when(webhookParser.parse(anyString(), any()))
                .thenReturn(event(MediaWebhookEvent.Type.OTHER, "track_published", "EV_9"));
        when(webhookEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.process("{}", "Bearer ok");

        assertThat(loggedEvents()).contains("WEBHOOK_PROCESSED");
    }
}
