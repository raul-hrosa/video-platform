package com.videoplatform.livekit;

import com.videoplatform.provider.MediaWebhookEvent;
import com.videoplatform.provider.MediaWebhookParser;
import com.videoplatform.provider.MediaWebhookVerificationException;
import io.livekit.server.WebhookReceiver;
import livekit.LivekitWebhook;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Adapter LiveKit -> plataforma para webhooks. Concentra todo o acoplamento com
 * o SDK LiveKit (validacao oficial de assinatura + parsing do payload) e entrega
 * ao dominio apenas um {@link MediaWebhookEvent} (Sprint 10 §20).
 */
@Component
@ConditionalOnProperty(prefix = "media", name = "provider", havingValue = "livekit", matchIfMissing = true)
public class LiveKitWebhookParser implements MediaWebhookParser {

    private final WebhookReceiver webhookReceiver;

    public LiveKitWebhookParser(WebhookReceiver webhookReceiver) {
        this.webhookReceiver = webhookReceiver;
    }

    @Override
    public MediaWebhookEvent parse(String body, String authHeader) {
        LivekitWebhook.WebhookEvent event;
        try {
            event = webhookReceiver.receive(body, authHeader);
        } catch (Exception ex) {
            throw new MediaWebhookVerificationException("invalid webhook signature", ex);
        }

        String rawType = event.getEvent();
        Instant occurredAt = epochOrNow(event.getCreatedAt());

        String roomId = event.hasRoom() ? event.getRoom().getName() : null;
        String participantId = null;
        String participantName = null;
        Instant participantJoinedAt = null;
        if (event.hasParticipant()) {
            participantId = event.getParticipant().getIdentity();
            String name = event.getParticipant().getName();
            participantName = (name == null || name.isBlank()) ? participantId : name;
            long joinedAt = event.getParticipant().getJoinedAt();
            participantJoinedAt = joinedAt > 0 ? Instant.ofEpochSecond(joinedAt) : occurredAt;
        }

        return new MediaWebhookEvent(
                mapType(rawType), rawType, resolveEventId(event.getId(), body), occurredAt,
                roomId, participantId, participantName, participantJoinedAt);
    }

    private static MediaWebhookEvent.Type mapType(String rawType) {
        return switch (rawType) {
            case "room_started" -> MediaWebhookEvent.Type.ROOM_STARTED;
            case "room_finished" -> MediaWebhookEvent.Type.ROOM_FINISHED;
            case "participant_joined" -> MediaWebhookEvent.Type.PARTICIPANT_JOINED;
            case "participant_left" -> MediaWebhookEvent.Type.PARTICIPANT_LEFT;
            default -> MediaWebhookEvent.Type.OTHER;
        };
    }

    private static Instant epochOrNow(long epochSeconds) {
        return epochSeconds > 0 ? Instant.ofEpochSecond(epochSeconds) : Instant.now();
    }

    /** LiveKit nem sempre envia id; nesse caso o hash do corpo garante idempotencia. */
    private static String resolveEventId(String id, String body) {
        if (id != null && !id.isBlank()) {
            return id;
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
