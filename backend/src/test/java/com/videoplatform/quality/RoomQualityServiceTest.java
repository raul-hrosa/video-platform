package com.videoplatform.quality;

import com.videoplatform.participant.ParticipantSession;
import com.videoplatform.quality.dto.RoomQualityResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoomQualityServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-28T13:00:00Z");

    private static ParticipantSession session(String participantId, String name) {
        return ParticipantSession.start("room-1", participantId, UUID.randomUUID(), name, T0);
    }

    private static ConnectionQualityMetric metric(String participantId, QualityLevel level,
                                                  Integer rtt, Double loss, int offsetSec) {
        return ConnectionQualityMetric.record("room-1", UUID.randomUUID(), participantId,
                T0.plusSeconds(offsetSec), level, rtt, loss, 8,
                null, null, null, null, null, "connected");
    }

    @Test
    void noMetricsMeansNoData() {
        RoomQualityResponse r = RoomQualityService.build(
                List.of(session("user:joao", "Joao")), List.of());

        assertThat(r.hasData()).isFalse();
        assertThat(r.timelineAvailable()).isFalse();
        assertThat(r.participants()).isEmpty();
    }

    @Test
    void takesLatestSnapshotPerParticipant() {
        RoomQualityResponse r = RoomQualityService.build(
                List.of(session("user:joao", "Joao"), session("guest:xyz", "Maria")),
                List.of(
                        metric("user:joao", QualityLevel.GOOD, 60, 0.1, 0),
                        metric("user:joao", QualityLevel.EXCELLENT, 40, 0.0, 30),
                        metric("guest:xyz", QualityLevel.FAIR, 130, 2.1, 10)));

        assertThat(r.hasData()).isTrue();
        assertThat(r.timelineAvailable()).isTrue(); // joao tem 2 snapshots

        var joao = r.participants().stream()
                .filter(p -> p.participantId().equals("user:joao")).findFirst().orElseThrow();
        assertThat(joao.latestLevel()).isEqualTo("EXCELLENT");
        assertThat(joao.rttMs()).isEqualTo(40);
        assertThat(joao.snapshots()).isEqualTo(2);
        assertThat(joao.isGuest()).isFalse();

        var maria = r.participants().stream()
                .filter(p -> p.participantId().equals("guest:xyz")).findFirst().orElseThrow();
        // Escala da plataforma (Sprint 10 §40.4): FAIR legado -> UNSTABLE.
        assertThat(maria.latestLevel()).isEqualTo("UNSTABLE");
        assertThat(maria.isGuest()).isTrue();
    }

    @Test
    void listsEveryParticipantEvenWithoutMetrics() {
        // Cenario tipico: dono envia metricas, convidado nao persiste nada (§22).
        RoomQualityResponse r = RoomQualityService.build(
                List.of(session("user:joao", "Joao"), session("guest:xyz", "Maria")),
                List.of(metric("user:joao", QualityLevel.GOOD, 60, 0.1, 0)));

        assertThat(r.hasData()).isTrue();
        assertThat(r.participants()).extracting(p -> p.participantId())
                .containsExactly("user:joao", "guest:xyz");

        var maria = r.participants().get(1);
        assertThat(maria.latestLevel()).isNull();
        assertThat(maria.rttMs()).isNull();
        assertThat(maria.snapshots()).isZero();
        assertThat(maria.isGuest()).isTrue();
    }

    @Test
    void singleSnapshotMeansNoTimeline() {
        RoomQualityResponse r = RoomQualityService.build(
                List.of(session("user:joao", "Joao")),
                List.of(metric("user:joao", QualityLevel.GOOD, 60, 0.1, 0)));

        assertThat(r.hasData()).isTrue();
        assertThat(r.timelineAvailable()).isFalse();
    }
}
