package com.videoplatform.participant;

import com.videoplatform.participant.dto.ParticipantSummaryResponse;
import com.videoplatform.participant.dto.ParticipantSummaryResponse.RoomParticipantsSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ParticipantSummaryServiceTest {

    private static final UUID JOAO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant T0 = Instant.parse("2026-08-28T13:00:00Z");

    private static ParticipantSession session(String participantId, UUID userId, String name,
                                              int startOffsetMin, Integer durationMin) {
        Instant joined = T0.plusSeconds(startOffsetMin * 60L);
        ParticipantSession s = ParticipantSession.start("room-1", participantId, userId, name, joined);
        if (durationMin != null) {
            s.end(joined.plusSeconds(durationMin * 60L));
        }
        return s;
    }

    @Test
    void sameUserWithTwoSessionsCountsAsOneDistinctParticipant() {
        List<ParticipantSession> sessions = List.of(
                session("user:" + JOAO, JOAO, "Joao", 0, 5),
                session("user:" + JOAO, JOAO, "Joao", 7, 12));

        RoomParticipantsSummary summary = ParticipantSummaryService.summarize(sessions, T0.plusSeconds(3600));

        assertThat(summary.distinctParticipants()).isEqualTo(1);
        assertThat(summary.totalSessions()).isEqualTo(2);
        assertThat(summary.totalEntries()).isEqualTo(2);
        assertThat(summary.totalParticipantSeconds()).isEqualTo((5 + 12) * 60L);

        ParticipantSummaryResponse joao = summary.participants().get(0);
        assertThat(joao.sessions()).isEqualTo(2);
        assertThat(joao.completedSessions()).isEqualTo(2);
        assertThat(joao.totalDurationSeconds()).isEqualTo(17 * 60L);
        assertThat(joao.firstJoinedAt()).isEqualTo(T0);
        assertThat(joao.lastLeftAt()).isEqualTo(T0.plusSeconds((7 + 12) * 60L));
        assertThat(joao.isGuest()).isFalse();
    }

    @Test
    void distinctParticipantsAndTotalSecondsAcrossPeople() {
        List<ParticipantSession> sessions = List.of(
                session("user:" + JOAO, JOAO, "Joao", 0, 10),
                session("guest:abc", null, "Maria", 1, 8));

        RoomParticipantsSummary summary = ParticipantSummaryService.summarize(sessions, T0.plusSeconds(3600));

        assertThat(summary.distinctParticipants()).isEqualTo(2);
        assertThat(summary.totalParticipantSeconds()).isEqualTo(18 * 60L);
        assertThat(summary.participants())
                .anySatisfy(p -> assertThat(p.isGuest()).isTrue());
    }

    @Test
    void openSessionCountsUntilNowAndLeavesLastLeftNull() {
        List<ParticipantSession> sessions = List.of(
                session("user:" + JOAO, JOAO, "Joao", 0, null));

        RoomParticipantsSummary summary = ParticipantSummaryService.summarize(sessions, T0.plusSeconds(600));

        ParticipantSummaryResponse joao = summary.participants().get(0);
        assertThat(joao.completedSessions()).isZero();
        assertThat(joao.lastLeftAt()).isNull();
        assertThat(joao.totalDurationSeconds()).isEqualTo(600L);
    }

    @Test
    void reconnectsAreSummedAcrossSessions() {
        ParticipantSession s1 = session("user:" + JOAO, JOAO, "Joao", 0, 5);
        s1.registerReconnect();
        s1.registerReconnect();
        ParticipantSession s2 = session("user:" + JOAO, JOAO, "Joao", 7, 12);
        s2.registerReconnect();

        RoomParticipantsSummary summary =
                ParticipantSummaryService.summarize(List.of(s1, s2), T0.plusSeconds(3600));

        assertThat(summary.participants().get(0).reconnects()).isEqualTo(3);
    }
}
