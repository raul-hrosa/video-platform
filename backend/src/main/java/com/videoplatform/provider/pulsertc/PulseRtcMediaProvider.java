package com.videoplatform.provider.pulsertc;

import com.videoplatform.common.ApiException;
import com.videoplatform.common.logging.LogEvents;
import com.videoplatform.provider.ConnectionQuality;
import com.videoplatform.provider.MediaTokenProvider;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.CreateTokenRequest;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.ParticipantQualityView;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.ParticipantView;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.QualityVerdict;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.RoomQualityView;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.SessionView;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.TokenPermissions;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.TokenView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.function.Supplier;

/**
 * Adapter PulseRTC do contrato de midia da plataforma (Sprint 11 §7). So traduz
 * {@code modelo video-platform <-> modelo PulseRTC}; as regras de negocio
 * continuam no dominio. Nunca loga token, apiKey nem Authorization (§16).
 */
public class PulseRtcMediaProvider implements MediaTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(PulseRtcMediaProvider.class);

    private final PulseRtcClient client;
    private final PulseRtcProperties properties;

    public PulseRtcMediaProvider(PulseRtcClient client, PulseRtcProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public MediaToken createRoomToken(String roomId, String participantRef, String displayName) {
        if (!properties.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_PROVIDER_NOT_CONFIGURED",
                    "Servico de video indisponivel no momento.");
        }
        ensureRoom(roomId);

        long ttlSeconds = properties.tokenTtl().toSeconds();
        TokenView token = mapErrors(() -> client.createToken(roomId,
                new CreateTokenRequest(participantRef, displayName,
                        TokenPermissions.participant(), ttlSeconds)));

        log.atInfo()
                .addKeyValue("event", LogEvents.PULSERTC_TOKEN_CREATED)
                .addKeyValue("provider", "pulsertc")
                .addKeyValue("roomId", roomId)
                .addKeyValue("participantId", participantRef)
                .setMessage("pulsertc participant token created")
                .log();
        return new MediaToken(token.token(), participantRef);
    }

    /** POST /v1/rooms idempotente (§4): conflito de idempotencia = sala ja existe. */
    public void ensureRoom(String roomId) {
        try {
            client.createRoom(roomId);
            log.atInfo()
                    .addKeyValue("event", LogEvents.PULSERTC_ROOM_CREATED)
                    .addKeyValue("provider", "pulsertc")
                    .addKeyValue("roomId", roomId)
                    .setMessage("pulsertc room created")
                    .log();
        } catch (PulseRtcApiException ex) {
            if (ex.isIdempotencyConflict()) {
                // Sala ja existe (POST /v1/rooms e idempotente, §4) — segue para o token.
                return;
            }
            throw PulseRtcErrorMapper.toApiException(ex);
        }
    }

    public void closeRoom(String roomId) {
        mapErrors(() -> {
            client.deleteRoom(roomId);
            return null;
        });
        log.atInfo()
                .addKeyValue("event", LogEvents.PULSERTC_ROOM_CLOSED)
                .addKeyValue("provider", "pulsertc")
                .addKeyValue("roomId", roomId)
                .setMessage("pulsertc room closed")
                .log();
    }

    public List<ParticipantView> getParticipants(String roomId) {
        var list = mapErrors(() -> client.listParticipants(roomId));
        return list.participants() == null ? List.of() : list.participants();
    }

    public SessionView getParticipantSession(String roomId, String identity) {
        return mapErrors(() -> client.getParticipantSession(roomId, identity));
    }

    /**
     * Breakdown de qualidade por participante calculado pela Quality Engine do
     * PulseRTC (Sprint 12 §16). O backend só traduz a escala; não classifica.
     */
    public List<ParticipantQualitySnapshot> roomQualityBreakdown(String roomId) {
        RoomQualityView view = mapErrors(() -> client.getRoomQuality(roomId));
        List<ParticipantQualityView> rows = view.participants() == null ? List.of() : view.participants();
        return rows.stream().map(PulseRtcMediaProvider::toSnapshot).toList();
    }

    public ParticipantQualitySnapshot participantQuality(String roomId, String identity) {
        return toSnapshot(mapErrors(() -> client.getParticipantQuality(roomId, identity)));
    }

    private static ParticipantQualitySnapshot toSnapshot(ParticipantQualityView v) {
        return new ParticipantQualitySnapshot(
                v.identity(),
                PulseRtcQualityMapper.fromRating(v.status()),
                v.score(),
                v.reason(),
                verdict(v.audio()),
                verdict(v.video()),
                verdict(v.connection()),
                v.metrics());
    }

    private static StreamQuality verdict(QualityVerdict v) {
        return v == null ? null : new StreamQuality(
                PulseRtcQualityMapper.fromRating(v.status()), v.score(), v.reason(), v.metrics());
    }

    /** Snapshot de qualidade já na escala da plataforma. */
    public record ParticipantQualitySnapshot(
            String participantRef,
            ConnectionQuality level,
            Integer score,
            String reason,
            StreamQuality audio,
            StreamQuality video,
            StreamQuality connection,
            java.util.Map<String, Object> metrics) {
    }

    /** Veredito de um stream (audio/video/connection) + métricas para diagnóstico. */
    public record StreamQuality(
            ConnectionQuality level,
            Integer score,
            String reason,
            java.util.Map<String, Object> metrics) {
    }

    private <T> T mapErrors(Supplier<T> action) {
        try {
            return action.get();
        } catch (PulseRtcApiException ex) {
            throw PulseRtcErrorMapper.toApiException(ex);
        }
    }
}
