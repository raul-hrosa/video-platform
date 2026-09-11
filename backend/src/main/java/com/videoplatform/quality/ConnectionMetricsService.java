package com.videoplatform.quality;

import com.videoplatform.auth.security.AuthenticatedUser;
import com.videoplatform.common.ApiException;
import com.videoplatform.common.logging.LogEvents;
import com.videoplatform.organization.OrganizationContext;
import com.videoplatform.participant.ParticipantSession;
import com.videoplatform.participant.ParticipantSessionService;
import com.videoplatform.quality.dto.ConnectionMetricRequest;
import com.videoplatform.room.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Ingestao e consulta de metricas de qualidade de conexao.
 *
 * <p>A identidade vem do <b>usuario autenticado</b>: uma metrica so e' aceita
 * quando a sessao pertence a ele ({@code session.userId == user.id}). O
 * {@code participantId} do request e' apenas informativo. O nivel e' sempre
 * reclassificado no backend.
 */
@Service
public class ConnectionMetricsService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionMetricsService.class);

    private final RoomService roomService;
    private final ParticipantSessionService participantSessionService;
    private final ConnectionQualityService qualityService;
    private final ConnectionQualityMetricRepository repository;

    public ConnectionMetricsService(RoomService roomService,
                                    ParticipantSessionService participantSessionService,
                                    ConnectionQualityService qualityService,
                                    ConnectionQualityMetricRepository repository) {
        this.roomService = roomService;
        this.participantSessionService = participantSessionService;
        this.qualityService = qualityService;
        this.repository = repository;
    }

    @Transactional
    public ConnectionQualityMetric record(String roomId, UUID sessionId, OrganizationContext ctx,
                                          AuthenticatedUser user, ConnectionMetricRequest req) {
        ParticipantSession session = resolveRoomSession(roomId, sessionId, ctx);
        if (!session.belongsTo(user.id())) {
            fail("FORBIDDEN", HttpStatus.FORBIDDEN,
                    "Esta sessao nao pertence a voce.", roomId, sessionId, user.id());
        }

        QualityLevel level = qualityService.classify(req.rttMs(), req.packetLossPercent(), req.jitterMs());

        ConnectionQualityMetric metric = repository.save(ConnectionQualityMetric.record(
                roomId, sessionId, session.getParticipantId(), Instant.now(), level,
                req.rttMs(), req.packetLossPercent(), req.jitterMs(),
                req.audioBitrate(), req.videoBitrate(),
                req.videoWidth(), req.videoHeight(), req.videoFps(), req.connectionState()));

        if (req.reconnectCount() != null) {
            session.bumpReconnectCount(req.reconnectCount());
            participantSessionService.save(session);
        }

        detectQualityChange(roomId, sessionId, session.getParticipantId(), level, metric);

        log.atDebug()
                .addKeyValue("event", LogEvents.CONNECTION_METRICS_RECORDED)
                .addKeyValue("roomId", roomId)
                .addKeyValue("userId", user.id())
                .addKeyValue("sessionId", sessionId)
                .addKeyValue("quality", level.name())
                .setMessage("connection metrics recorded")
                .log();

        return metric;
    }

    /** Historico — visivel apenas para o dono da sala (endpoint de gestao). */
    @Transactional(readOnly = true)
    public Page<ConnectionQualityMetric> history(String roomId, UUID sessionId,
                                                 OrganizationContext ctx, Pageable pageable) {
        resolveRoomSession(roomId, sessionId, ctx);
        return repository.findBySessionIdOrderByRecordedAtAsc(sessionId, pageable);
    }

    private ParticipantSession resolveRoomSession(String roomId, UUID sessionId, OrganizationContext ctx) {
        roomService.getInOrg(roomId, ctx); // ROOM_NOT_FOUND (inclui isolamento por Organization)
        ParticipantSession session = participantSessionService.getById(sessionId); // SESSION_NOT_FOUND
        if (!session.getRoomId().equals(roomId)) {
            fail("SESSION_ROOM_MISMATCH", HttpStatus.NOT_FOUND,
                    "Session does not belong to this room.", roomId, sessionId, null);
        }
        return session;
    }

    private void detectQualityChange(String roomId, UUID sessionId, String participantId,
                                     QualityLevel current, ConnectionQualityMetric saved) {
        List<ConnectionQualityMetric> history = repository.findBySessionIdOrderByRecordedAtAsc(sessionId);
        if (history.size() < 2) {
            return;
        }
        QualityLevel previous = history.get(history.size() - 2).getQualityLevel();
        if (previous == current) {
            return;
        }
        boolean bad = current == QualityLevel.POOR || current == QualityLevel.VERY_POOR;
        var builder = bad ? log.atWarn() : log.atInfo();
        builder.addKeyValue("event", LogEvents.CONNECTION_QUALITY_CHANGED)
                .addKeyValue("roomId", roomId)
                .addKeyValue("participantId", participantId)
                .addKeyValue("sessionId", sessionId)
                .addKeyValue("previousQuality", previous.name())
                .addKeyValue("quality", current.name())
                .addKeyValue("rttMs", saved.getRttMs())
                .addKeyValue("packetLossPercent", saved.getPacketLossPercent())
                .setMessage("connection quality changed")
                .log();
    }

    private void fail(String code, HttpStatus status, String message,
                      String roomId, UUID sessionId, UUID userId) {
        log.atWarn()
                .addKeyValue("event", LogEvents.CONNECTION_METRICS_FAILED)
                .addKeyValue("errorCode", code)
                .addKeyValue("roomId", roomId)
                .addKeyValue("sessionId", sessionId)
                .addKeyValue("userId", userId)
                .setMessage("connection metrics rejected")
                .log();
        throw new ApiException(status, code, message);
    }
}
