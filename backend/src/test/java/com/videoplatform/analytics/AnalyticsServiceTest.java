package com.videoplatform.analytics;

import com.videoplatform.analytics.dto.AnalyticsResponse;
import com.videoplatform.participant.ParticipantSession;
import com.videoplatform.participant.ParticipantSessionService;
import com.videoplatform.quality.ConnectionQualityMetric;
import com.videoplatform.quality.ConnectionQualityMetricRepository;
import com.videoplatform.quality.QualityLevel;
import com.videoplatform.room.Room;
import com.videoplatform.room.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    private static final UUID OWNER = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID ORG = UUID.fromString("a0000000-0000-0000-0000-0000000000aa");
    private static final UUID PROFILE = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final com.videoplatform.organization.OrganizationContext CTX =
            new com.videoplatform.organization.OrganizationContext(
                    ORG, OWNER, com.videoplatform.organization.OrgRole.OWNER);

    @Mock
    private RoomService roomService;
    @Mock
    private ParticipantSessionService participantSessionService;
    @Mock
    private ConnectionQualityMetricRepository qualityMetricRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    @BeforeEach
    void noMetricsByDefault() {
        lenient().when(qualityMetricRepository.findByRoomId("room-1")).thenReturn(List.of());
    }

    private AnalyticsResponse analytics() {
        return analyticsService.forRoom("room-1", CTX);
    }

    private void roomIsEnded(String started, String ended) {
        Room room = Room.createFromProfile("room-1", PROFILE, ORG, "Sala", 60, OWNER, Instant.parse(started));
        room.markActive(Instant.parse(started));
        room.markEnded(Instant.parse(ended));
        when(roomService.getInOrg(eq("room-1"), any())).thenReturn(room);
    }

    private static ConnectionQualityMetric metric(UUID sessionId, QualityLevel level,
                                                  Integer rtt, Double loss) {
        return ConnectionQualityMetric.record("room-1", sessionId, "joao",
                Instant.parse("2026-08-28T10:00:00Z"), level, rtt, loss, null,
                null, null, null, null, null, "connected");
    }

    private static ParticipantSession session(String id, String from, String to) {
        ParticipantSession s = ParticipantSession.start(
                "room-1", id, UUID.randomUUID(), id, Instant.parse(from));
        if (to != null) {
            s.end(Instant.parse(to));
        }
        return s;
    }

    @Test
    void computesDurationParticipantsAndTotals() {
        roomIsEnded("2026-08-28T10:00:00Z", "2026-08-28T11:00:00Z");
        when(participantSessionService.listByRoom("room-1")).thenReturn(List.of(
                session("joao", "2026-08-28T10:00:00Z", "2026-08-28T10:58:20Z"),
                session("maria", "2026-08-28T10:02:00Z", "2026-08-28T10:59:00Z")));

        AnalyticsResponse a = analytics();

        assertThat(a.durationSeconds()).isEqualTo(3600L);
        assertThat(a.participants()).isEqualTo(2);
        assertThat(a.totalParticipantSeconds()).isEqualTo(3500L + 3420L);
        assertThat(a.peakParticipants()).isEqualTo(2);
    }

    @Test
    void peakConsidersOverlapNotHeadcount() {
        roomIsEnded("2026-08-28T10:00:00Z", "2026-08-28T11:00:00Z");
        when(participantSessionService.listByRoom("room-1")).thenReturn(List.of(
                session("joao", "2026-08-28T10:00:00Z", "2026-08-28T10:30:00Z"),
                session("maria", "2026-08-28T10:05:00Z", "2026-08-28T10:40:00Z")));

        assertThat(analytics().peakParticipants()).isEqualTo(2);
    }

    @Test
    void backToBackSessionsDoNotOverlap() {
        roomIsEnded("2026-08-28T10:00:00Z", "2026-08-28T11:00:00Z");
        when(participantSessionService.listByRoom("room-1")).thenReturn(List.of(
                session("a", "2026-08-28T10:00:00Z", "2026-08-28T10:30:00Z"),
                session("b", "2026-08-28T10:30:00Z", "2026-08-28T11:00:00Z")));

        assertThat(analytics().peakParticipants()).isEqualTo(1);
    }

    @Test
    void roomWithoutParticipants() {
        roomIsEnded("2026-08-28T10:00:00Z", "2026-08-28T10:00:00Z");
        when(participantSessionService.listByRoom("room-1")).thenReturn(List.of());

        AnalyticsResponse a = analytics();

        assertThat(a.participants()).isZero();
        assertThat(a.peakParticipants()).isZero();
        assertThat(a.totalParticipantSeconds()).isZero();
    }

    @Test
    void activeRoomHasNullDuration() {
        Room active = Room.createFromProfile("room-1", PROFILE, ORG, "Sala", 60, OWNER,
                Instant.parse("2026-08-28T10:00:00Z"));
        active.markActive(Instant.parse("2026-08-28T10:00:00Z"));
        when(roomService.getInOrg(eq("room-1"), any())).thenReturn(active);
        when(participantSessionService.listByRoom("room-1")).thenReturn(List.of());

        assertThat(analytics().durationSeconds()).isNull();
    }

    @Test
    void expiredRoomThatNeverStartedHasNullDuration() {
        Room room = Room.createFromProfile("room-1", PROFILE, ORG, "Sala", 1, OWNER,
                Instant.parse("2020-01-01T00:00:00Z"));
        room.markExpired(Instant.parse("2020-01-01T00:01:00Z"));
        when(roomService.getInOrg(eq("room-1"), any())).thenReturn(room);
        when(participantSessionService.listByRoom("room-1")).thenReturn(List.of());

        assertThat(analytics().durationSeconds()).isNull();
    }

    @Test
    void qualityIsNullWhenNoMetricsAndNoReconnects() {
        roomIsEnded("2026-08-28T10:00:00Z", "2026-08-28T11:00:00Z");
        when(participantSessionService.listByRoom("room-1")).thenReturn(List.of(
                session("joao", "2026-08-28T10:00:00Z", "2026-08-28T10:30:00Z")));

        assertThat(analytics().quality()).isNull();
    }

    @Test
    void aggregatesQualityMetrics() {
        UUID sid = UUID.randomUUID();
        roomIsEnded("2026-08-28T10:00:00Z", "2026-08-28T11:00:00Z");
        when(participantSessionService.listByRoom("room-1")).thenReturn(List.of(
                session("joao", "2026-08-28T10:00:00Z", "2026-08-28T10:30:00Z")));
        when(qualityMetricRepository.findByRoomId("room-1")).thenReturn(List.of(
                metric(sid, QualityLevel.GOOD, 100, 1.0),
                metric(sid, QualityLevel.GOOD, 120, 2.0),
                metric(sid, QualityLevel.POOR, 320, 6.0),
                metric(sid, QualityLevel.EXCELLENT, 60, 0.2)));

        AnalyticsResponse.QualityAnalytics q = analytics().quality();

        assertThat(q).isNotNull();
        assertThat(q.avgRttMs()).isEqualTo(150);
        assertThat(q.avgPacketLossPercent()).isEqualTo(2.3);
        assertThat(q.levelDistribution().get("GOOD")).isEqualTo(0.5);
        assertThat(q.levelDistribution().get("POOR")).isEqualTo(0.25);
        assertThat(q.levelDistribution().get("EXCELLENT")).isEqualTo(0.25);
        assertThat(q.levelDistribution().get("UNKNOWN")).isEqualTo(0.0);
    }
}
