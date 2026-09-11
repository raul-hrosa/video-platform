package com.videoplatform.provider.livekit;

import com.videoplatform.common.ApiException;
import com.videoplatform.common.logging.LogEvents;
import com.videoplatform.livekit.LiveKitProperties;
import com.videoplatform.provider.MediaTokenProvider;
import io.livekit.server.AccessToken;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Implementacao LiveKit do contrato de token da plataforma.
 * O JWT gerado nunca e logado. Ativa quando {@code media.provider=livekit}
 * (padrao) — ver Sprint 11 §17.
 */
@Service
@ConditionalOnProperty(prefix = "media", name = "provider", havingValue = "livekit", matchIfMissing = true)
public class LiveKitTokenProvider implements MediaTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(LiveKitTokenProvider.class);

    private final LiveKitProperties properties;

    public LiveKitTokenProvider(LiveKitProperties properties) {
        this.properties = properties;
    }

    @Override
    public MediaToken createRoomToken(String roomId, String participantRef, String displayName) {
        if (!properties.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "LIVEKIT_NOT_CONFIGURED",
                    "Servico de video indisponivel no momento.");
        }
        long start = System.nanoTime();
        log.atInfo()
                .addKeyValue("event", LogEvents.TOKEN_GENERATION_STARTED)
                .addKeyValue("roomId", roomId)
                .addKeyValue("participantId", participantRef)
                .setMessage("token generation started")
                .log();
        try {
            AccessToken token = new AccessToken(properties.apiKey(), properties.apiSecret());
            token.setIdentity(participantRef);
            token.setName(displayName);
            token.setTtl(properties.tokenTtl().toMillis());
            token.addGrants(new RoomJoin(true), new RoomName(roomId));

            String jwt = token.toJwt();
            log.atInfo()
                    .addKeyValue("event", LogEvents.TOKEN_GENERATED)
                    .addKeyValue("roomId", roomId)
                    .addKeyValue("participantId", participantRef)
                    .addKeyValue("durationMs", (System.nanoTime() - start) / 1_000_000)
                    .setMessage("token generated")
                    .log();
            return new MediaToken(jwt, participantRef);
        } catch (Exception ex) {
            log.atError()
                    .addKeyValue("event", LogEvents.TOKEN_GENERATION_FAILED)
                    .addKeyValue("roomId", roomId)
                    .addKeyValue("errorCode", "LIVEKIT_TOKEN_GENERATION_FAILED")
                    .setCause(ex)
                    .setMessage("token generation failed")
                    .log();
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "LIVEKIT_TOKEN_GENERATION_FAILED",
                    "Nao foi possivel entrar na sala.", ex);
        }
    }
}
