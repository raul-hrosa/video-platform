package com.videoplatform.room;

import com.videoplatform.common.logging.LogEvents;
import com.videoplatform.participant.ParticipantSession;
import com.videoplatform.participant.ParticipantSessionService;
import com.videoplatform.quality.ConnectionQualityMetric;
import com.videoplatform.quality.ConnectionQualityMetricRepository;
import com.videoplatform.room.dto.RoomEventResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Linha do tempo de eventos de uma sala (Sprint 9 §14), montada por leitura dos
 * dados ja coletados. Webhooks e o log estruturado (Sprint 2.5) seguem intactos;
 * este servico so os apresenta de forma normalizada.
 */
@Service
public class RoomEventsService {

    private final ParticipantSessionService participantSessionService;
    private final ConnectionQualityMetricRepository metricRepository;

    public RoomEventsService(ParticipantSessionService participantSessionService,
                             ConnectionQualityMetricRepository metricRepository) {
        this.participantSessionService = participantSessionService;
        this.metricRepository = metricRepository;
    }

    @Transactional(readOnly = true)
    public List<RoomEventResponse> forRoom(Room room) {
        List<RoomEventResponse> events = new ArrayList<>();
        events.add(RoomEventResponse.room(LogEvents.ROOM_CREATED, room.getCreatedAt()));
        if (room.getStartedAt() != null) {
            events.add(RoomEventResponse.room(LogEvents.ROOM_STARTED, room.getStartedAt()));
        }
        if (room.getEndedAt() != null) {
            events.add(RoomEventResponse.room(LogEvents.ROOM_ENDED, room.getEndedAt()));
        }
        if (room.getStatus() == RoomStatus.EXPIRED && room.getExpiresAt() != null) {
            events.add(RoomEventResponse.room(LogEvents.ROOM_EXPIRED, room.getExpiresAt()));
        }

        List<ParticipantSession> sessions = participantSessionService.listByRoom(room.getRoomId());
        for (ParticipantSession s : sessions) {
            events.add(RoomEventResponse.participant(
                    LogEvents.PARTICIPANT_SESSION_STARTED, s.getJoinedAt(), s.getParticipantId(), null));
            if (s.getReconnectCount() > 0) {
                events.add(RoomEventResponse.participant(LogEvents.PARTICIPANT_RECONNECTED,
                        s.getJoinedAt(), s.getParticipantId(), s.getReconnectCount() + " reconnection(s)"));
            }
            if (s.getLeftAt() != null) {
                events.add(RoomEventResponse.participant(
                        LogEvents.PARTICIPANT_SESSION_ENDED, s.getLeftAt(), s.getParticipantId(), null));
            }
        }

        Map<String, ConnectionQualityMetric> lastByParticipant = new LinkedHashMap<>();
        for (ConnectionQualityMetric m : metricRepository.findByRoomIdOrderByRecordedAtAsc(room.getRoomId())) {
            ConnectionQualityMetric previous = lastByParticipant.put(m.getParticipantId(), m);
            if (previous == null || previous.getQualityLevel() != m.getQualityLevel()) {
                events.add(RoomEventResponse.participant(LogEvents.CONNECTION_QUALITY_CHANGED,
                        m.getRecordedAt(), m.getParticipantId(), m.getQualityLevel().name()));
            }
        }

        events.sort(Comparator.comparing(RoomEventResponse::at));
        return events;
    }
}
