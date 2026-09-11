package com.videoplatform.webhook;

import com.videoplatform.common.ApiException;
import com.videoplatform.common.logging.CorrelationId;
import com.videoplatform.common.logging.LogEvents;
import com.videoplatform.participant.ParticipantSessionService;
import com.videoplatform.provider.MediaWebhookEvent;
import com.videoplatform.provider.MediaWebhookParser;
import com.videoplatform.provider.MediaWebhookVerificationException;
import com.videoplatform.provider.PlatformParticipantIdentity;
import com.videoplatform.room.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Recebe e processa os webhooks do provider de midia: delega a validacao de
 * assinatura e o parsing ao {@link MediaWebhookParser} (adapter LiveKit),
 * garante idempotencia por event_id e aplica os efeitos no dominio a partir do
 * modelo {@link MediaWebhookEvent}. Registra o ciclo completo do evento
 * (RECEIVED / VALIDATED / REJECTED / DUPLICATED / PROCESSED / PROCESSING_FAILED).
 * Nunca loga o Authorization header nem a assinatura.
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final MediaWebhookParser webhookParser;
    private final WebhookEventRepository webhookEventRepository;
    private final RoomService roomService;
    private final ParticipantSessionService participantSessionService;

    public WebhookService(MediaWebhookParser webhookParser,
                          WebhookEventRepository webhookEventRepository,
                          RoomService roomService,
                          ParticipantSessionService participantSessionService) {
        this.webhookParser = webhookParser;
        this.webhookEventRepository = webhookEventRepository;
        this.roomService = roomService;
        this.participantSessionService = participantSessionService;
    }

    public record WebhookResult(boolean processed, String reason) {
    }

    @Transactional
    public WebhookResult process(String body, String authHeader) {
        long start = System.nanoTime();
        log.atInfo()
                .addKeyValue("event", LogEvents.WEBHOOK_RECEIVED)
                .setMessage("webhook received")
                .log();

        MediaWebhookEvent event = verify(body, authHeader);
        String eventType = event.rawType();
        String eventId = event.eventId();

        MDC.put(CorrelationId.MDC_WEBHOOK_EVENT_ID, eventId);
        MDC.put(CorrelationId.MDC_EVENT_TYPE, eventType);
        try {
            log.atInfo()
                    .addKeyValue("event", LogEvents.WEBHOOK_VALIDATED)
                    .setMessage("webhook validated")
                    .log();

            if (webhookEventRepository.existsByEventId(eventId)) {
                log.atInfo()
                        .addKeyValue("event", LogEvents.WEBHOOK_DUPLICATED)
                        .setMessage("webhook duplicated, skipping effect")
                        .log();
                return new WebhookResult(false, "ALREADY_PROCESSED");
            }

            WebhookEvent record = webhookEventRepository.save(
                    WebhookEvent.received(eventId, eventType, body, Instant.now()));

            // Se o dispatch lancar, a transacao inteira faz rollback (inclusive
            // este registro) e o provider reenvia o evento mais tarde.
            boolean handled;
            try {
                handled = dispatch(event);
            } catch (RuntimeException ex) {
                log.atError()
                        .addKeyValue("event", LogEvents.WEBHOOK_PROCESSING_FAILED)
                        .addKeyValue("errorCode", "WEBHOOK_PROCESSING_FAILED")
                        .setCause(ex)
                        .setMessage("webhook processing failed")
                        .log();
                throw ex;
            }

            long durationMs = (System.nanoTime() - start) / 1_000_000;
            if (handled) {
                record.markProcessed(Instant.now());
                log.atInfo()
                        .addKeyValue("event", LogEvents.WEBHOOK_PROCESSED)
                        .addKeyValue("durationMs", durationMs)
                        .setMessage("webhook processed")
                        .log();
                return new WebhookResult(true, null);
            }
            record.markIgnored(Instant.now());
            log.atInfo()
                    .addKeyValue("event", LogEvents.WEBHOOK_PROCESSED)
                    .addKeyValue("durationMs", durationMs)
                    .addKeyValue("reason", "UNHANDLED_EVENT")
                    .setMessage("webhook processed (unhandled type, recorded only)")
                    .log();
            return new WebhookResult(false, "UNHANDLED_EVENT");
        } finally {
            MDC.remove(CorrelationId.MDC_WEBHOOK_EVENT_ID);
            MDC.remove(CorrelationId.MDC_EVENT_TYPE);
        }
    }

    private MediaWebhookEvent verify(String body, String authHeader) {
        try {
            return webhookParser.parse(body, authHeader);
        } catch (MediaWebhookVerificationException ex) {
            log.atWarn()
                    .addKeyValue("event", LogEvents.WEBHOOK_REJECTED)
                    .addKeyValue("reason", "INVALID_SIGNATURE")
                    .setMessage("webhook rejected")
                    .log();
            throw new ApiException(HttpStatus.UNAUTHORIZED, "WEBHOOK_INVALID",
                    "Webhook signature is invalid.");
        }
    }

    private boolean dispatch(MediaWebhookEvent event) {
        Instant eventTime = event.occurredAt();
        return switch (event.type()) {
            case PARTICIPANT_JOINED -> {
                String roomId = event.roomId();
                String participantId = event.participantId();
                java.util.UUID userId = PlatformParticipantIdentity.parseUserId(participantId);
                log.atInfo()
                        .addKeyValue("event", LogEvents.PARTICIPANT_JOINED)
                        .addKeyValue("roomId", roomId)
                        .addKeyValue("participantId", participantId)
                        .addKeyValue("userId", userId)
                        .setMessage("participant joined")
                        .log();
                participantSessionService.startSession(
                        roomId, participantId, userId, event.participantName(),
                        event.participantJoinedAt() != null ? event.participantJoinedAt() : eventTime);
                roomService.markActive(roomId, eventTime);
                yield true;
            }
            case PARTICIPANT_LEFT -> {
                String roomId = event.roomId();
                String participantId = event.participantId();
                log.atInfo()
                        .addKeyValue("event", LogEvents.PARTICIPANT_LEFT)
                        .addKeyValue("roomId", roomId)
                        .addKeyValue("participantId", participantId)
                        .setMessage("participant left")
                        .log();
                participantSessionService.endSession(roomId, participantId, eventTime);
                yield true;
            }
            case ROOM_STARTED -> {
                roomService.markActive(event.roomId(), eventTime);
                yield true;
            }
            case ROOM_FINISHED -> {
                roomService.markEnded(event.roomId(), eventTime);
                yield true;
            }
            case OTHER -> false;
        };
    }
}
