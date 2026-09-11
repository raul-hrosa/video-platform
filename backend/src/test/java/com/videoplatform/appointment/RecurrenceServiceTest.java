package com.videoplatform.appointment;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecurrenceServiceTest {

    private final RecurrenceService service = new RecurrenceService();

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private static final Duration JOIN_EARLY = Duration.ofMinutes(15);

    /** Terca-feira, 2026-09-01, 15:00 em America/Sao_Paulo (== 18:00Z). */
    private static final Instant FIRST_START = ZonedDateTime.of(
            LocalDate.of(2026, 9, 1), LocalTime.of(15, 0), SP).toInstant();

    private Appointment weekly(LocalDate until) {
        return Appointment.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Aula de Ingles", "abc123xyz000", "Raul", 60, SP, FIRST_START,
                RecurrenceType.WEEKLY, until);
    }

    private Appointment once() {
        return Appointment.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Consulta", "def456uvw000", null, 60, SP, FIRST_START,
                RecurrenceType.NONE, null);
    }

    @Test
    void weeklyDerivesDayOfWeekFromStartInTimezone() {
        assertThat(weekly(null).getRecurrenceDayOfWeek()).isEqualTo(java.time.DayOfWeek.TUESDAY);
    }

    @Test
    void nextStartOnOrAfterReturnsSameInstantWhenExactlyAtStart() {
        assertThat(service.nextStartOnOrAfter(weekly(null), FIRST_START)).contains(FIRST_START);
    }

    @Test
    void nextStartOnOrAfterJumpsToFollowingWeek() {
        Instant dayAfter = FIRST_START.plus(Duration.ofDays(1));
        Instant expected = FIRST_START.plus(Duration.ofDays(7));
        assertThat(service.nextStartOnOrAfter(weekly(null), dayAfter)).contains(expected);
    }

    @Test
    void weeklyStopsAtRecurrenceUntil() {
        Appointment appt = weekly(LocalDate.of(2026, 9, 15)); // 01, 08, 15
        Instant afterLast = ZonedDateTime.of(LocalDate.of(2026, 9, 16), LocalTime.NOON, SP).toInstant();
        assertThat(service.nextStartOnOrAfter(appt, afterLast)).isEmpty();
        Instant beforeLast = ZonedDateTime.of(LocalDate.of(2026, 9, 10), LocalTime.NOON, SP).toInstant();
        assertThat(service.nextStartOnOrAfter(appt, beforeLast))
                .contains(ZonedDateTime.of(LocalDate.of(2026, 9, 15), LocalTime.of(15, 0), SP).toInstant());
    }

    @Test
    void nonRecurringHasNoOccurrenceAfterItsSlot() {
        Instant later = FIRST_START.plus(Duration.ofDays(1));
        assertThat(service.nextStartOnOrAfter(once(), later)).isEmpty();
    }

    // ---- janela de entrada (§48) ----

    @Test
    void beforeJoinWindowHasNoActiveOccurrence() {
        Instant t = FIRST_START.minus(Duration.ofMinutes(30)); // 14:30
        assertThat(service.activeStartFor(weekly(null), t, JOIN_EARLY)).isEmpty();
    }

    @Test
    void insideJoinWindowResolvesToOccurrence() {
        Instant t = FIRST_START.minus(Duration.ofMinutes(15)); // 14:45 exato
        assertThat(service.activeStartFor(weekly(null), t, JOIN_EARLY)).contains(FIRST_START);
        Instant t2 = FIRST_START.minus(Duration.ofMinutes(8)); // 14:52
        assertThat(service.activeStartFor(weekly(null), t2, JOIN_EARLY)).contains(FIRST_START);
    }

    @Test
    void atStartAndDuringCallResolvesToOccurrence() {
        assertThat(service.activeStartFor(weekly(null), FIRST_START, JOIN_EARLY)).contains(FIRST_START);
        Instant mid = FIRST_START.plus(Duration.ofMinutes(59));
        assertThat(service.activeStartFor(weekly(null), mid, JOIN_EARLY)).contains(FIRST_START);
    }

    @Test
    void afterEndHasNoActiveOccurrence() {
        Instant t = FIRST_START.plus(Duration.ofMinutes(61)); // 16:01
        assertThat(service.activeStartFor(weekly(null), t, JOIN_EARLY)).isEmpty();
    }

    // ---- timezone / horario de verao ----

    @Test
    void weeklyKeepsLocalTimeAcrossDaylightSavingChange() {
        ZoneId ny = ZoneId.of("America/New_York");
        // Terca 2026-10-27 15:00 EDT; DST termina 2026-11-01 -> 11-03 ainda 15:00 local (EST).
        Instant start = ZonedDateTime.of(LocalDate.of(2026, 10, 27), LocalTime.of(15, 0), ny).toInstant();
        Appointment appt = Appointment.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Aula", "nyc000000000", null, 60, ny, start, RecurrenceType.WEEKLY, null);

        Instant afterDst = ZonedDateTime.of(LocalDate.of(2026, 11, 3), LocalTime.of(6, 0), ny).toInstant();
        Instant next = service.nextStartOnOrAfter(appt, afterDst).orElseThrow();

        assertThat(next.atZone(ny).toLocalTime()).isEqualTo(LocalTime.of(15, 0));
        assertThat(next.atZone(ny).toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 3));
    }
}
