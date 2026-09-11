package com.videoplatform.participant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParticipantServiceTest {

    private static final UUID USER = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final Instant T0 = Instant.parse("2026-08-31T14:00:00Z");

    @Mock
    private ParticipantRepository repository;
    @InjectMocks
    private ParticipantService service;

    @Test
    void createsUserIdentityFromUserPrefix() {
        when(repository.findByRoomIdAndParticipantRef("room-1", "user:" + USER)).thenReturn(Optional.empty());
        when(repository.save(any(Participant.class))).thenAnswer(i -> i.getArgument(0));

        Participant p = service.getOrCreate("room-1", "user:" + USER, "Joao", T0);

        assertThat(p.getKind()).isEqualTo(ParticipantKind.USER);
        assertThat(p.getUserId()).isEqualTo(USER);
        assertThat(p.getDisplayName()).isEqualTo("Joao");
        assertThat(p.getFirstSeenAt()).isEqualTo(T0);
    }

    @Test
    void createsGuestIdentityFromGuestPrefix() {
        when(repository.findByRoomIdAndParticipantRef(any(), any())).thenReturn(Optional.empty());
        when(repository.save(any(Participant.class))).thenAnswer(i -> i.getArgument(0));

        Participant p = service.getOrCreate("room-1", "guest:" + UUID.randomUUID(), "Maria", T0);

        assertThat(p.getKind()).isEqualTo(ParticipantKind.GUEST);
        assertThat(p.getUserId()).isNull();
    }

    @Test
    void reusesExistingIdentityAndAdvancesLastSeen() {
        Participant existing = Participant.create("room-1", "user:" + USER, ParticipantKind.USER, USER, "Joao", T0);
        when(repository.findByRoomIdAndParticipantRef("room-1", "user:" + USER))
                .thenReturn(Optional.of(existing));

        Participant p = service.getOrCreate("room-1", "user:" + USER, "Joao", T0.plusSeconds(600));

        assertThat(p).isSameAs(existing);
        assertThat(p.getLastSeenAt()).isEqualTo(T0.plusSeconds(600));
        verify(repository, never()).save(any());
    }

    @Test
    void recoversFromConcurrentInsertRace() {
        Participant winner = Participant.create("room-1", "user:" + USER, ParticipantKind.USER, USER, "Joao", T0);
        when(repository.findByRoomIdAndParticipantRef("room-1", "user:" + USER))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(repository.save(any(Participant.class))).thenThrow(new DataIntegrityViolationException("dup"));

        Participant p = service.getOrCreate("room-1", "user:" + USER, "Joao", T0.plusSeconds(1));

        assertThat(p).isSameAs(winner);
    }

    @Test
    void identityRefIsTheLiveKitIdentityVerbatim() {
        when(repository.findByRoomIdAndParticipantRef(any(), any())).thenReturn(Optional.empty());
        when(repository.save(any(Participant.class))).thenAnswer(i -> i.getArgument(0));

        service.getOrCreate("room-1", "user:" + USER, "Joao", T0);

        ArgumentCaptor<Participant> captor = ArgumentCaptor.forClass(Participant.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getParticipantRef()).isEqualTo("user:" + USER);
    }
}
