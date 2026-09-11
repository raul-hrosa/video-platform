package com.videoplatform.analytics;

import com.videoplatform.analytics.dto.AnalyticsResponse;
import com.videoplatform.analytics.dto.AnalyticsResponse.QualityAnalytics;
import com.videoplatform.participant.ParticipantSession;
import com.videoplatform.participant.ParticipantSessionService;
import com.videoplatform.provider.ConnectionQuality;
import com.videoplatform.provider.QualityMapper;
import com.videoplatform.quality.ConnectionQualityMetric;
import com.videoplatform.quality.ConnectionQualityMetricRepository;
import com.videoplatform.common.logging.LogEvents;
import com.videoplatform.organization.OrganizationContext;
import com.videoplatform.room.Room;
import com.videoplatform.room.RoomService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Analytics de uma sala: sessoes de participacao + qualidade de conexao.
 */
@Service
public class AnalyticsService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(AnalyticsService.class);

    private final RoomService roomService;
    private final ParticipantSessionService participantSessionService;
    private final ConnectionQualityMetricRepository qualityMetricRepository;

    public AnalyticsService(RoomService roomService,
                            ParticipantSessionService participantSessionService,
                            ConnectionQualityMetricRepository qualityMetricRepository) {
        this.roomService = roomService;
        this.participantSessionService = participantSessionService;
        this.qualityMetricRepository = qualityMetricRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse forRoom(String roomId, OrganizationContext ctx) {
        Room room = roomService.getInOrg(roomId, ctx);
        log.atInfo()
                .addKeyValue("event", LogEvents.ROOM_ANALYTICS_VIEWED)
                .addKeyValue("roomId", roomId)
                .addKeyValue("organizationId", room.getOrganizationId())
                .addKeyValue("userId", ctx.userId())
                .setMessage("room analytics viewed")
                .log();
        List<ParticipantSession> sessions = participantSessionService.listByRoom(roomId);
        List<ConnectionQualityMetric> metrics = qualityMetricRepository.findByRoomId(roomId);
        Instant now = Instant.now();

        return new AnalyticsResponse(
                room.getRoomId(),
                roomDurationSeconds(room),
                distinctParticipants(sessions),
                peakParticipants(sessions, now),
                totalParticipantSeconds(sessions, now),
                qualityAnalytics(sessions, metrics));
    }

    private static Long roomDurationSeconds(Room room) {
        if (room.getStartedAt() == null || room.getEndedAt() == null) {
            return null;
        }
        return Duration.between(room.getStartedAt(), room.getEndedAt()).getSeconds();
    }

    private static long distinctParticipants(List<ParticipantSession> sessions) {
        return sessions.stream().map(ParticipantSession::getParticipantId).distinct().count();
    }

    private static long totalParticipantSeconds(List<ParticipantSession> sessions, Instant now) {
        long total = 0;
        for (ParticipantSession s : sessions) {
            Instant end = s.getLeftAt() != null ? s.getLeftAt() : now;
            total += Math.max(0, Duration.between(s.getJoinedAt(), end).getSeconds());
        }
        return total;
    }

    /**
     * Maior numero de participantes simultaneos, considerando a sobreposicao das
     * sessoes. Varre os eventos de entrada (+1) e saida (-1) em ordem de tempo;
     * em empate, processa saidas antes de entradas para nao contar sobreposicao
     * inexistente.
     */
    private static long peakParticipants(List<ParticipantSession> sessions, Instant now) {
        List<long[]> events = new ArrayList<>();
        for (ParticipantSession s : sessions) {
            Instant end = s.getLeftAt() != null ? s.getLeftAt() : now;
            events.add(new long[]{s.getJoinedAt().toEpochMilli(), 1});
            events.add(new long[]{end.toEpochMilli(), -1});
        }
        events.sort((a, b) -> a[0] != b[0]
                ? Long.compare(a[0], b[0])
                : Long.compare(a[1], b[1])); // -1 antes de +1

        long current = 0;
        long peak = 0;
        for (long[] e : events) {
            current += e[1];
            peak = Math.max(peak, current);
        }
        return peak;
    }

    private static QualityAnalytics qualityAnalytics(List<ParticipantSession> sessions,
                                                     List<ConnectionQualityMetric> metrics) {
        long totalReconnects = sessions.stream().mapToLong(ParticipantSession::getReconnectCount).sum();
        if (metrics.isEmpty()) {
            if (totalReconnects == 0) {
                return null;
            }
            return new QualityAnalytics(null, null, totalReconnects, emptyDistribution());
        }

        Integer avgRtt = averageInt(metrics.stream()
                .map(ConnectionQualityMetric::getRttMs).filter(java.util.Objects::nonNull).toList());
        Double avgLoss = averageDouble(metrics.stream()
                .map(ConnectionQualityMetric::getPacketLossPercent).filter(java.util.Objects::nonNull).toList());

        Map<String, Double> distribution = emptyDistribution();
        for (ConnectionQualityMetric m : metrics) {
            distribution.merge(QualityMapper.fromLegacy(m.getQualityLevel()).name(), 1.0, Double::sum);
        }
        distribution.replaceAll((k, v) -> round(v / metrics.size(), 4));

        return new QualityAnalytics(avgRtt, avgLoss, totalReconnects, distribution);
    }

    private static Map<String, Double> emptyDistribution() {
        Map<String, Double> d = new LinkedHashMap<>();
        for (ConnectionQuality level : ConnectionQuality.values()) {
            d.put(level.name(), 0.0);
        }
        return d;
    }

    private static Integer averageInt(List<Integer> values) {
        return values.isEmpty() ? null
                : (int) Math.round(values.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    private static Double averageDouble(List<Double> values) {
        return values.isEmpty() ? null
                : round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0), 2);
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
