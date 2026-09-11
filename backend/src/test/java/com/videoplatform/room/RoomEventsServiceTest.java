package com.videoplatform.room;

import com.videoplatform.participant.ParticipantSession;
import com.videoplatform.participant.ParticipantSessionService;
import com.videoplatform.quality.ConnectionQualityMetric;
import com.videoplatform.quality.ConnectionQualityMetricRepository;
import com.videoplatform.quality.QualityLevel;
import com.videoplatform.room.dto.RoomEventResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomEventsServiceTest {

    private static final UUID ORG = UUID.fromString("a0000000-0000-0000-0000-0000000000aa");
    private static final UUID USER = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final Instant T0 = Instant.parse("2026-08-31T14:00:00Z");

    @Mock
    private ParticipantSessionService participantSessionService;
    @Mock
    private ConnectionQualityMetricRepository metricRepository;
    @InjectMocks
    private RoomEventsService service;

    private ConnectionQualityMetric metric(QualityLevel level, Instant at) {
        ConnectionQualityMetric m = org.mockito.Mockito.mock(ConnectionQualityMetric.class);
        lenient().when(m.getParticipantId()).thenReturn("guest:x");
        lenient().when(m.getQualityLevel()).thenReturn(level);
        lenient().when(m.getRecordedAt()).thenReturn(at);
        return m;
    }

    @Test
    void buildsOrderedTimelineFromRoomSessionsAndQuality() {
        Room room = Room.createDirect("room-1", ORG, USER, T0);
        room.markActive(T0.plusSeconds(60));
        ParticipantSession s = ParticipantSession.start("room-1", "guest:x", null, "Maria", T0.plusSeconds(60));
        s.registerReconnect();
        s.end(T0.plusSeconds(600));
        ConnectionQualityMetric m1 = metric(QualityLevel.GOOD, T0.plusSeconds(120));
        ConnectionQualityMetric m2 = metric(QualityLevel.POOR, T0.plusSeconds(300));

        when(participantSessionService.listByRoom("room-1")).thenReturn(List.of(s));
        when(metricRepository.findByRoomIdOrderByRecordedAtAsc("room-1")).thenReturn(List.of(m1, m2));

        List<RoomEventResponse> events = service.forRoom(room);

        assertThat(events).extracting(RoomEventResponse::type).containsExactly(
                "ROOM_CREATED",
                "ROOM_STARTED",
                "PARTICIPANT_SESSION_STARTED",
                "PARTICIPANT_RECONNECTED",
                "CONNECTION_QUALITY_CHANGED",
                "CONNECTION_QUALITY_CHANGED",
                "PARTICIPANT_SESSION_ENDED");
        assertThat(events).isSortedAccordingTo(java.util.Comparator.comparing(RoomEventResponse::at));
    }

    @Test
    void directRoomWithoutActivityHasOnlyCreatedEvent() {
        Room room = Room.createDirect("room-1", ORG, USER, T0);
        when(participantSessionService.listByRoom("room-1")).thenReturn(List.of());
        when(metricRepository.findByRoomIdOrderByRecordedAtAsc("room-1")).thenReturn(List.of());

        assertThat(service.forRoom(room)).extracting(RoomEventResponse::type).containsExactly("ROOM_CREATED");
    }
}
