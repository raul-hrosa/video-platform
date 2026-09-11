package com.videoplatform.participant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParticipantSessionServiceTest {

    private static final UUID U = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000009");

    @Mock
    private ParticipantSessionRepository repository;
    @Mock
    private ParticipantService participantService;

    @InjectMocks
    private ParticipantSessionService service;

    @org.junit.jupiter.api.BeforeEach
    void stubIdentity() {
        org.mockito.Mockito.lenient().when(participantService.getOrCreate(
                        any(), any(), any(), any()))
                .thenReturn(Participant.create("room-1", "user-1", ParticipantKind.USER, U, "Joao",
                        Instant.parse("2026-08-28T10:00:00Z")));
    }

    private void noOpenSession() {
        when(repository.findFirstByRoomIdAndParticipantIdAndLeftAtIsNullOrderByJoinedAtAsc(any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void startSessionCreatesSessionWhenNoneOpen() {
        noOpenSession();
        when(repository.save(any(ParticipantSession.class))).thenAnswer(i -> i.getArgument(0));

        service.startSession("room-1", "user-1", U, "Joao", Instant.parse("2026-08-28T10:00:00Z"));

        ArgumentCaptor<ParticipantSession> captor = ArgumentCaptor.forClass(ParticipantSession.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getParticipantId()).isEqualTo("user-1");
        assertThat(captor.getValue().getJoinedAt()).isEqualTo(Instant.parse("2026-08-28T10:00:00Z"));
        assertThat(captor.getValue().isOpen()).isTrue();
    }

    @Test
    void startSessionSkipsWhenSessionAlreadyOpen() {
        ParticipantSession existing = ParticipantSession.start("room-1", "user-1", U, "Joao", Instant.now());
        when(repository.findFirstByRoomIdAndParticipantIdAndLeftAtIsNullOrderByJoinedAtAsc("room-1", "user-1"))
                .thenReturn(Optional.of(existing));

        ParticipantSession result = service.startSession("room-1", "user-1", U, "Joao", Instant.now());

        assertThat(result).isSameAs(existing);
        verify(repository, never()).save(any());
    }

    @Test
    void endSessionSetsLeftAtAndDuration() {
        ParticipantSession session = ParticipantSession.start(
                "room-1", "user-1", U, "Joao", Instant.parse("2026-08-28T10:00:00Z"));
        when(repository.findFirstByRoomIdAndParticipantIdAndLeftAtIsNullOrderByJoinedAtAsc("room-1", "user-1"))
                .thenReturn(Optional.of(session));

        Optional<ParticipantSession> ended = service.endSession(
                "room-1", "user-1", Instant.parse("2026-08-28T10:51:46Z"));

        assertThat(ended).containsSame(session);
        assertThat(session.getLeftAt()).isEqualTo(Instant.parse("2026-08-28T10:51:46Z"));
        assertThat(session.getDurationSeconds()).isEqualTo(3106L);
    }

    @Test
    void endSessionOnMissingOpenSessionIsNoOp() {
        when(repository.findFirstByRoomIdAndParticipantIdAndLeftAtIsNullOrderByJoinedAtAsc("room-1", "user-1"))
                .thenReturn(Optional.empty());

        assertThat(service.endSession("room-1", "user-1", Instant.now())).isEmpty();
    }

    @Test
    void endSessionNeverProducesNegativeDuration() {
        ParticipantSession session = ParticipantSession.start(
                "room-1", "user-1", U, "Joao", Instant.parse("2026-08-28T10:00:00Z"));
        when(repository.findFirstByRoomIdAndParticipantIdAndLeftAtIsNullOrderByJoinedAtAsc("room-1", "user-1"))
                .thenReturn(Optional.of(session));

        service.endSession("room-1", "user-1", Instant.parse("2026-08-28T09:00:00Z"));

        assertThat(session.getDurationSeconds()).isEqualTo(0L);
    }
}
