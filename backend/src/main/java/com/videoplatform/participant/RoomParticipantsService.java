package com.videoplatform.participant;

import com.videoplatform.common.ApiException;
import com.videoplatform.participant.dto.ParticipantAnalyticsResponse;
import com.videoplatform.participant.dto.ParticipantAnalyticsResponse.CurrentSession;
import com.videoplatform.participant.dto.ParticipantAnalyticsResponse.History;
import com.videoplatform.participant.dto.ParticipantAnalyticsResponse.Quality;
import com.videoplatform.participant.dto.ParticipantAnalyticsResponse.SessionEntry;
import com.videoplatform.participant.dto.RoomParticipantResponse;
import com.videoplatform.provider.PlatformParticipantIdentity;
import com.videoplatform.provider.QualityMapper;
import com.videoplatform.quality.ConnectionQualityMetric;
import com.videoplatform.quality.ConnectionQualityMetricRepository;
import com.videoplatform.quality.QualityLevel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Visao Room-centric de participantes (Sprint 9 §10-11): identidades
 * {@link Participant} com os totais das suas {@link ParticipantSession}s e a
 * ultima leitura de qualidade. So dados persistidos — nada e' inventado (§22).
 */
@Service
public class RoomParticipantsService {

    private final ParticipantService participantService;
    private final ParticipantSessionService participantSessionService;
    private final ConnectionQualityMetricRepository metricRepository;

    public RoomParticipantsService(ParticipantService participantService,
                                   ParticipantSessionService participantSessionService,
                                   ConnectionQualityMetricRepository metricRepository) {
        this.participantService = participantService;
        this.participantSessionService = participantSessionService;
        this.metricRepository = metricRepository;
    }

    @Transactional(readOnly = true)
    public List<RoomParticipantResponse> listByRoom(String roomId) {
        Instant now = Instant.now();
        Map<String, List<ParticipantSession>> sessionsByRef = groupSessions(
                participantSessionService.listByRoom(roomId));
        Map<String, List<ConnectionQualityMetric>> metricsByRef = groupMetrics(
                metricRepository.findByRoomIdOrderByRecordedAtAsc(roomId));

        List<RoomParticipantResponse> out = new ArrayList<>();
        Map<String, Participant> identities = new LinkedHashMap<>();
        for (Participant p : participantService.listByRoom(roomId)) {
            identities.put(p.getParticipantRef(), p);
        }
        // Identidades registradas (ordem de primeira aparicao).
        for (Participant p : identities.values()) {
            out.add(aggregate(p.getParticipantRef(), p.getKind().name(), p.getDisplayName(),
                    p.getFirstSeenAt(), p.getLastSeenAt(),
                    sessionsByRef.getOrDefault(p.getParticipantRef(), List.of()),
                    metricsByRef.getOrDefault(p.getParticipantRef(), List.of()), now));
        }
        // Defensivo: sessoes cujo ref nao tem linha em participants (dados pre-Sprint 9).
        for (Map.Entry<String, List<ParticipantSession>> e : sessionsByRef.entrySet()) {
            if (identities.containsKey(e.getKey())) {
                continue;
            }
            List<ParticipantSession> list = e.getValue();
            String name = list.get(list.size() - 1).getParticipantName();
            String kind = PlatformParticipantIdentity.isGuest(e.getKey())
                    ? ParticipantKind.GUEST.name() : ParticipantKind.USER.name();
            out.add(aggregate(e.getKey(), kind, name,
                    list.get(0).getJoinedAt(), list.get(list.size() - 1).getJoinedAt(),
                    list, metricsByRef.getOrDefault(e.getKey(), List.of()), now));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public ParticipantAnalyticsResponse detail(String roomId, String participantRef) {
        Participant identity = participantService.find(roomId, participantRef)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PARTICIPANT_NOT_FOUND",
                        "Participante nao encontrado nesta sala."));
        Instant now = Instant.now();
        List<ParticipantSession> sessions = new ArrayList<>(
                groupSessions(participantSessionService.listByRoom(roomId))
                        .getOrDefault(participantRef, List.of()));
        sessions.sort(Comparator.comparing(ParticipantSession::getJoinedAt));
        List<ConnectionQualityMetric> metrics = groupMetrics(
                metricRepository.findByRoomIdOrderByRecordedAtAsc(roomId))
                .getOrDefault(participantRef, List.of());

        long totalSeconds = 0;
        int reconnections = 0;
        CurrentSession current = null;
        List<SessionEntry> entries = new ArrayList<>();
        for (ParticipantSession s : sessions) {
            Instant end = s.getLeftAt() != null ? s.getLeftAt() : now;
            long seconds = Math.max(0, Duration.between(s.getJoinedAt(), end).getSeconds());
            totalSeconds += seconds;
            reconnections += s.getReconnectCount();
            entries.add(new SessionEntry(s.getId(), s.getJoinedAt(), s.getLeftAt(),
                    s.getDurationSeconds(), s.getReconnectCount()));
            if (s.getLeftAt() == null) {
                current = new CurrentSession(s.getId(), s.getJoinedAt(), seconds);
            }
        }

        return new ParticipantAnalyticsResponse(
                identity.getParticipantRef(), identity.getKind().name(), identity.getDisplayName(),
                current,
                new History(sessions.size(), totalSeconds, reconnections),
                quality(metrics),
                entries);
    }

    private static RoomParticipantResponse aggregate(String ref, String kind, String displayName,
                                                     Instant firstSeen, Instant lastSeen,
                                                     List<ParticipantSession> sessions,
                                                     List<ConnectionQualityMetric> metrics, Instant now) {
        long totalSeconds = 0;
        int reconnections = 0;
        boolean open = false;
        for (ParticipantSession s : sessions) {
            Instant end = s.getLeftAt() != null ? s.getLeftAt() : now;
            totalSeconds += Math.max(0, Duration.between(s.getJoinedAt(), end).getSeconds());
            reconnections += s.getReconnectCount();
            open |= s.getLeftAt() == null;
        }
        QualityLevel latestLevel = metrics.isEmpty() ? null
                : metrics.stream().max(Comparator.comparing(ConnectionQualityMetric::getRecordedAt))
                        .orElseThrow().getQualityLevel();
        String latestQuality = latestLevel == null ? null : QualityMapper.fromLegacy(latestLevel).name();
        return new RoomParticipantResponse(ref, kind, displayName, sessions.size(), totalSeconds,
                reconnections, open, firstSeen, lastSeen, latestQuality);
    }

    private static Quality quality(List<ConnectionQualityMetric> metrics) {
        if (metrics.isEmpty()) {
            return null;
        }
        double meanOrdinal = metrics.stream()
                .mapToInt(m -> m.getQualityLevel().ordinal()).average().orElseThrow();
        QualityLevel average = QualityLevel.values()[(int) Math.round(meanOrdinal)];
        QualityLevel current = metrics.stream()
                .max(Comparator.comparing(ConnectionQualityMetric::getRecordedAt))
                .orElseThrow().getQualityLevel();
        return new Quality(QualityMapper.fromLegacy(average).name(), QualityMapper.fromLegacy(current).name());
    }

    private static Map<String, List<ParticipantSession>> groupSessions(List<ParticipantSession> sessions) {
        Map<String, List<ParticipantSession>> map = new LinkedHashMap<>();
        for (ParticipantSession s : sessions) {
            map.computeIfAbsent(s.getParticipantId(), k -> new ArrayList<>()).add(s);
        }
        return map;
    }

    private static Map<String, List<ConnectionQualityMetric>> groupMetrics(List<ConnectionQualityMetric> metrics) {
        Map<String, List<ConnectionQualityMetric>> map = new LinkedHashMap<>();
        for (ConnectionQualityMetric m : metrics) {
            map.computeIfAbsent(m.getParticipantId(), k -> new ArrayList<>()).add(m);
        }
        return map;
    }
}
