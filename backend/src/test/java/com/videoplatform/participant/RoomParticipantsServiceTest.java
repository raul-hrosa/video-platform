package com.videoplatform.participant;

import com.videoplatform.common.ApiException;
import com.videoplatform.participant.dto.ParticipantAnalyticsResponse;
import com.videoplatform.participant.dto.RoomParticipantResponse;
import com.videoplatform.quality.ConnectionQualityMetric;
import com.videoplatform.quality.ConnectionQualityMetricRepository;
import com.videoplatform.quality.QualityLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomParticipantsServiceTest {

    private static final UUID USER = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final String REF = "user:" + USER;
    private static final Instant T0 = Instant.parse("2026-08-31T14:00:00Z");

    @Mock
    private ParticipantService participantService;
    @Mock
    private ParticipantSessionService participantSessionService;
    @Mock
    private ConnectionQualityMetricRepository metricRepository;
    @InjectMocks
    private RoomParticipantsService service;

    private ParticipantSession session(Instant joined, Instant left, int reconnects) {
        ParticipantSession s = ParticipantSession.start("room-1", REF, USER, "Joao", joined);
        if (left != null) {
            s.end(left);
        }
        for (int i = 0; i < reconnects; i++) {
            s.registerReconnect();
        }
        return s;
    }

    private ConnectionQualityMetric metric(QualityLevel level, Instant at) {
        ConnectionQualityMetric m = org.mockito.Mockito.mock(ConnectionQualityMetric.class);
        lenient().when(m.getParticipantId()).thenReturn(REF);
        lenient().when(m.getQualityLevel()).thenReturn(level);
        lenient().when(m.getRecordedAt()).thenReturn(at);
        return m;
    }

    @Test
    void aggregatesSessionsPerIdentity() {
        Participant p = Participant.create("room-1", REF, ParticipantKind.USER, USER, "Joao", T0);
        when(participantService.listByRoom("room-1")).thenReturn(List.of(p));
        List<ParticipantSession> sessions = List.of(
                session(T0, T0.plusSeconds(600), 0),
                session(T0.plusSeconds(1200), null, 2));
        ConnectionQualityMetric m1 = metric(QualityLevel.FAIR, T0.plusSeconds(1300));
        ConnectionQualityMetric m2 = metric(QualityLevel.GOOD, T0.plusSeconds(1400));
        when(participantSessionService.listByRoom("room-1")).thenReturn(sessions);
        when(metricRepository.findByRoomIdOrderByRecordedAtAsc("room-1")).thenReturn(List.of(m1, m2));

        List<RoomParticipantResponse> out = service.listByRoom("room-1");

        assertThat(out).hasSize(1);
        RoomParticipantResponse r = out.get(0);
        assertThat(r.totalSessions()).isEqualTo(2);
        assertThat(r.reconnections()).isEqualTo(2);
        assertThat(r.currentSessionOpen()).isTrue();
        assertThat(r.latestQualityLevel()).isEqualTo("GOOD");
        assertThat(r.totalConnectedSeconds()).isGreaterThanOrEqualTo(600);
    }

    @Test
    void detailReturnsQualityNullWhenNoMetrics() {
        Participant p = Participant.create("room-1", REF, ParticipantKind.USER, USER, "Joao", T0);
        when(participantService.find("room-1", REF)).thenReturn(java.util.Optional.of(p));
        when(participantSessionService.listByRoom("room-1")).thenReturn(List.of(
                session(T0, T0.plusSeconds(300), 0)));
        when(metricRepository.findByRoomIdOrderByRecordedAtAsc("room-1")).thenReturn(List.of());

        ParticipantAnalyticsResponse d = service.detail("room-1", REF);

        assertThat(d.quality()).isNull();
        assertThat(d.history().totalSessions()).isEqualTo(1);
        assertThat(d.currentSession()).isNull();
    }

    @Test
    void detailThrowsWhenParticipantUnknown() {
        when(participantService.find("room-1", "guest:none")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.detail("room-1", "guest:none"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo("PARTICIPANT_NOT_FOUND"));
    }

    @Test
    void detailAveragesQualityOrdinals() {
        Participant p = Participant.create("room-1", REF, ParticipantKind.USER, USER, "Joao", T0);
        when(participantService.find("room-1", REF)).thenReturn(java.util.Optional.of(p));
        List<ParticipantSession> sessions = List.of(session(T0, null, 0));
        ConnectionQualityMetric m1 = metric(QualityLevel.POOR, T0.plusSeconds(10));
        ConnectionQualityMetric m2 = metric(QualityLevel.EXCELLENT, T0.plusSeconds(20));
        when(participantSessionService.listByRoom("room-1")).thenReturn(sessions);
        when(metricRepository.findByRoomIdOrderByRecordedAtAsc("room-1")).thenReturn(List.of(m1, m2));

        ParticipantAnalyticsResponse d = service.detail("room-1", REF);

        assertThat(d.quality().current()).isEqualTo("EXCELLENT");
        // ordinais POOR=1, EXCELLENT=4 -> media 2.5 -> Math.round = 3 -> GOOD
        assertThat(d.quality().average()).isEqualTo("GOOD");
        assertThat(d.currentSession()).isNotNull();
    }
}
