package com.videoplatform.appointment;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Calcula as ocorrencias de um {@link Appointment} (§18, §45). Puro e
 * deterministico — nao toca no banco. A recorrencia semanal e' interpretada na
 * timezone do Appointment: "toda terca 15:00" continua sendo 15:00 local mesmo
 * atravessando horario de verao.
 */
@Service
public class RecurrenceService {

    /** Teto de seguranca: ~10 anos de ocorrencias semanais. */
    private static final int MAX_ITERATIONS = 520;

    /** O n-esimo inicio agendado (0 = {@link Appointment#getStartsAt()}), respeitando o fim da recorrencia. */
    private Optional<Instant> startAt(Appointment appt, int index) {
        ZoneId zone = appt.zoneId();
        ZonedDateTime base = appt.getStartsAt().atZone(zone);
        ZonedDateTime candidate = appt.getRecurrenceType() == RecurrenceType.WEEKLY
                ? base.plusWeeks(index)
                : (index == 0 ? base : null);
        if (candidate == null) {
            return Optional.empty();
        }
        if (appt.getRecurrenceUntil() != null
                && candidate.toLocalDate().isAfter(appt.getRecurrenceUntil())) {
            return Optional.empty();
        }
        return Optional.of(candidate.toInstant());
    }

    /** Primeiro inicio de ocorrencia em ou depois de {@code from}. */
    public Optional<Instant> nextStartOnOrAfter(Appointment appt, Instant from) {
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            Optional<Instant> start = startAt(appt, i);
            if (start.isEmpty()) {
                return Optional.empty();
            }
            if (!start.get().isBefore(from)) {
                return start;
            }
        }
        return Optional.empty();
    }

    /**
     * Inicio da ocorrencia cuja janela [inicio - joinEarly, inicio + duracao]
     * contem {@code now}. Vazio se {@code now} esta antes da janela da proxima ou
     * depois do fim da ultima.
     */
    public Optional<Instant> activeStartFor(Appointment appt, Instant now, Duration joinEarly) {
        Duration duration = Duration.ofMinutes(appt.getDurationMinutes());
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            Optional<Instant> maybeStart = startAt(appt, i);
            if (maybeStart.isEmpty()) {
                return Optional.empty();
            }
            Instant start = maybeStart.get();
            Instant windowOpen = start.minus(joinEarly);
            Instant windowClose = start.plus(duration);
            if (now.isBefore(windowOpen)) {
                return Optional.empty(); // a proxima ocorrencia ainda nem abriu janela
            }
            if (!now.isAfter(windowClose)) {
                return Optional.of(start);
            }
        }
        return Optional.empty();
    }
}
