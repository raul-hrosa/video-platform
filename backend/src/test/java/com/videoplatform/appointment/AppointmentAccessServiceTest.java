package com.videoplatform.appointment;

import com.videoplatform.appointment.AppointmentAccessService.State;
import com.videoplatform.common.ApiException;
import com.videoplatform.room.Room;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentAccessServiceTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private static final String PUB = "abc123xyz000";
    private static final Instant FIRST_START = ZonedDateTime.of(
            LocalDate.of(2026, 9, 1), LocalTime.of(15, 0), SP).toInstant();

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private OccurrenceMaterializer materializer;

    private final AppointmentProperties props = new AppointmentProperties(15, Duration.ofMinutes(20));

    private AppointmentAccessService serviceAt(Instant now) {
        return new AppointmentAccessService(appointmentRepository, new RecurrenceService(),
                materializer, props, Clock.fixed(now, ZoneId.of("UTC")));
    }

    private Appointment weekly() {
        return Appointment.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Aula de Ingles", PUB, "Raul", 60, SP, FIRST_START,
                RecurrenceType.WEEKLY, LocalDate.of(2026, 9, 15));
    }

    private void stubMaterializer() {
        AppointmentOccurrence occ = AppointmentOccurrence.schedule(UUID.randomUUID(), FIRST_START,
                FIRST_START.plus(Duration.ofMinutes(60)), FIRST_START);
        Room room = Room.createForOccurrence("room-appt01", UUID.randomUUID(), UUID.randomUUID(),
                "Aula de Ingles", 60, UUID.randomUUID(), occ.getId(),
                FIRST_START.plus(Duration.ofMinutes(60)), FIRST_START);
        occ.attachRoom(room.getId());
        when(materializer.getOrCreate(any(), any(), any(), any()))
                .thenReturn(new OccurrenceMaterializer.Materialized(occ, room));
    }

    @Test
    void notFoundLinkThrows404() {
        when(appointmentRepository.findByPublicAccessId("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> serviceAt(FIRST_START).resolve("ghost"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo("APPOINTMENT_NOT_FOUND"));
    }

    @Test
    void cancelledAppointmentReturnsCancelledWithoutMaterializing() {
        Appointment appt = weekly();
        appt.cancel();
        when(appointmentRepository.findByPublicAccessId(PUB)).thenReturn(Optional.of(appt));

        var r = serviceAt(FIRST_START).resolve(PUB);

        assertThat(r.state()).isEqualTo(State.CANCELLED);
        verify(materializer, never()).getOrCreate(any(), any(), any(), any());
    }

    @Test
    void beforeJoinWindowReturnsBeforeWindowWithNextStartAndNoRoom() {
        when(appointmentRepository.findByPublicAccessId(PUB)).thenReturn(Optional.of(weekly()));
        Instant now = FIRST_START.minus(Duration.ofMinutes(30)); // 14:30

        var r = serviceAt(now).resolve(PUB);

        assertThat(r.state()).isEqualTo(State.BEFORE_WINDOW);
        assertThat(r.nextOccurrenceStart()).isEqualTo(FIRST_START);
        assertThat(r.joinWindowOpensAt()).isEqualTo(FIRST_START.minus(Duration.ofMinutes(15)));
        assertThat(r.roomId()).isNull();
        verify(materializer, never()).getOrCreate(any(), any(), any(), any());
    }

    @Test
    void insideWindowBeforeStartReturnsWaitingRoomAndMaterializes() {
        when(appointmentRepository.findByPublicAccessId(PUB)).thenReturn(Optional.of(weekly()));
        stubMaterializer();
        Instant now = FIRST_START.minus(Duration.ofMinutes(8)); // 14:52

        var r = serviceAt(now).resolve(PUB);

        assertThat(r.state()).isEqualTo(State.WAITING_ROOM);
        assertThat(r.roomId()).isEqualTo("room-appt01");
        assertThat(r.canJoin()).isTrue();
        verify(materializer).getOrCreate(any(), eq(FIRST_START), any(), any());
    }

    @Test
    void atStartReturnsJoinable() {
        when(appointmentRepository.findByPublicAccessId(PUB)).thenReturn(Optional.of(weekly()));
        stubMaterializer();

        var r = serviceAt(FIRST_START.plus(Duration.ofMinutes(5))).resolve(PUB);

        assertThat(r.state()).isEqualTo(State.JOINABLE);
        assertThat(r.roomId()).isEqualTo("room-appt01");
    }

    @Test
    void afterLastOccurrenceReturnsEnded() {
        when(appointmentRepository.findByPublicAccessId(PUB)).thenReturn(Optional.of(weekly()));
        Instant now = ZonedDateTime.of(LocalDate.of(2026, 9, 20), LocalTime.NOON, SP).toInstant();

        var r = serviceAt(now).resolve(PUB);

        assertThat(r.state()).isEqualTo(State.ENDED);
        assertThat(r.nextOccurrenceStart()).isNull();
        verify(materializer, never()).getOrCreate(any(), any(), any(), any());
    }

    @Test
    void retriesOnceWhenMaterializationLosesConcurrencyRace() {
        when(appointmentRepository.findByPublicAccessId(PUB)).thenReturn(Optional.of(weekly()));
        AppointmentOccurrence occ = AppointmentOccurrence.schedule(UUID.randomUUID(), FIRST_START,
                FIRST_START.plus(Duration.ofMinutes(60)), FIRST_START);
        Room room = Room.createForOccurrence("room-appt01", UUID.randomUUID(), UUID.randomUUID(),
                "Aula", 60, UUID.randomUUID(), occ.getId(),
                FIRST_START.plus(Duration.ofMinutes(60)), FIRST_START);
        when(materializer.getOrCreate(any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("uq_occurrence_slot"))
                .thenReturn(new OccurrenceMaterializer.Materialized(occ, room));

        var r = serviceAt(FIRST_START).resolve(PUB);

        assertThat(r.roomId()).isEqualTo("room-appt01");
        verify(materializer, times(2)).getOrCreate(any(), any(), any(), any());
    }
}
