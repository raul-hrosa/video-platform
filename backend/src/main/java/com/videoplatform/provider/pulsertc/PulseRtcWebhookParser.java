package com.videoplatform.provider.pulsertc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.videoplatform.provider.MediaWebhookEvent;
import com.videoplatform.provider.MediaWebhookParser;
import com.videoplatform.provider.MediaWebhookVerificationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Adapter PulseRTC -> plataforma para webhooks (Sprint 11 §12). Concentra toda a
 * validacao de assinatura ({@code X-PulseRTC-Signature}: HMAC-SHA256 de
 * {@code <timestamp>.<body>}) + protecao contra replay, e entrega ao dominio
 * apenas um {@link MediaWebhookEvent}. A idempotencia por {@code id} do evento e
 * responsabilidade do {@code WebhookService}.
 *
 * <p>O valor cru do header {@code X-PulseRTC-Signature} chega no parametro
 * {@code authHeader} de {@link #parse(String, String)} (roteado pelo controller).
 */
public class PulseRtcWebhookParser implements MediaWebhookParser {

    private final ObjectMapper objectMapper;
    private final String secret;
    private final Duration tolerance;
    private final Clock clock;

    public PulseRtcWebhookParser(ObjectMapper objectMapper, PulseRtcProperties properties, Clock clock) {
        this.objectMapper = objectMapper;
        this.secret = properties.resolvedWebhookSecret();
        this.tolerance = properties.webhookTolerance();
        this.clock = clock;
    }

    @Override
    public MediaWebhookEvent parse(String body, String signatureHeader) {
        Signature sig = Signature.parse(signatureHeader);
        verify(body, sig);

        JsonNode root = readJson(body);
        String rawType = text(root, "type");
        String eventId = firstNonBlank(text(root, "id"), sha256(body));
        Instant occurredAt = parseInstant(text(root, "occurredAt"), text(root, "timestamp"), sig.timestamp());

        JsonNode room = root.path("room");
        String roomId = firstNonBlank(text(root, "roomId"), text(room, "id"), text(room, "roomId"));

        // PulseRTC coloca os dados do participante em `data.identity`;
        // formatos alternativos (participant.identity, root.identity) mantidos p/ compatibilidade.
        JsonNode data = root.path("data");
        JsonNode participant = root.path("participant");
        String participantId = firstNonBlank(
                text(data, "identity"),
                text(root, "identity"),
                text(participant, "identity"));
        String participantName = firstNonBlank(text(data, "name"), text(participant, "name"), participantId);
        Instant joinedAt = participant.isMissingNode()
                ? null
                : parseInstant(text(participant, "joinedAt"), null, occurredAt);

        return new MediaWebhookEvent(mapType(rawType), rawType == null ? "" : rawType, eventId,
                occurredAt, roomId, participantId,
                participantId == null ? null : participantName, joinedAt);
    }

    private void verify(String body, Signature sig) {
        if (secret == null || secret.isBlank()) {
            throw new MediaWebhookVerificationException("pulsertc webhook secret not configured", null);
        }
        if (sig == null || sig.v1() == null || sig.timestamp() == null) {
            throw new MediaWebhookVerificationException("missing pulsertc signature header", null);
        }
        Instant now = clock.instant();
        if (Duration.between(sig.timestamp(), now).abs().compareTo(tolerance) > 0) {
            throw new MediaWebhookVerificationException("pulsertc webhook timestamp outside tolerance", null);
        }
        String signedPayload = sig.timestamp().getEpochSecond() + "." + body;
        String expected = hmacSha256Hex(signedPayload);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), sig.v1().getBytes(StandardCharsets.UTF_8))) {
            throw new MediaWebhookVerificationException("pulsertc webhook signature mismatch", null);
        }
    }

    private String hmacSha256Hex(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new MediaWebhookVerificationException("hmac computation failed", ex);
        }
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new MediaWebhookVerificationException("pulsertc webhook body is not valid json", ex);
        }
    }

    private static MediaWebhookEvent.Type mapType(String rawType) {
        if (rawType == null) {
            return MediaWebhookEvent.Type.OTHER;
        }
        return switch (rawType) {
            case "room.closed" -> MediaWebhookEvent.Type.ROOM_FINISHED;
            case "participant.joined" -> MediaWebhookEvent.Type.PARTICIPANT_JOINED;
            case "participant.left" -> MediaWebhookEvent.Type.PARTICIPANT_LEFT;
            default -> MediaWebhookEvent.Type.OTHER;
        };
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isTextual() ? v.asText() : null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private Instant parseInstant(String iso, String epoch, Instant fallback) {
        if (iso != null && !iso.isBlank()) {
            try {
                return Instant.parse(iso);
            } catch (Exception ignored) {
                // tenta epoch
            }
        }
        if (epoch != null && !epoch.isBlank()) {
            try {
                return Instant.ofEpochSecond(Long.parseLong(epoch.trim()));
            } catch (NumberFormatException ignored) {
                // usa fallback
            }
        }
        return fallback != null ? fallback : clock.instant();
    }

    private static String sha256(String body) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    /** {@code X-PulseRTC-Signature: t=<unix>,v1=<hex>} */
    private record Signature(Instant timestamp, String v1) {

        static Signature parse(String header) {
            if (header == null || header.isBlank()) {
                return null;
            }
            Instant ts = null;
            String v1 = null;
            for (String part : header.split(",")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length != 2) {
                    continue;
                }
                switch (kv[0].trim()) {
                    case "t" -> {
                        try {
                            ts = Instant.ofEpochSecond(Long.parseLong(kv[1].trim()));
                        } catch (NumberFormatException ignored) {
                            // deixa null -> rejeitado em verify
                        }
                    }
                    case "v1" -> v1 = kv[1].trim();
                    default -> {
                    }
                }
            }
            return new Signature(ts, v1);
        }
    }
}
