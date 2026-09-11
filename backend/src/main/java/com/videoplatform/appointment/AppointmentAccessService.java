package com.videoplatform.appointment;

import com.videoplatform.common.ApiException;
import com.videoplatform.common.logging.LogEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Resolucao da ocorrencia pelo link publico (§28, §29, §48). O mesmo
 * {@code publicAccessId} serve todas as ocorrencias — este servico decide qual
 * esta em jogo agora e, quando dentro da janela, materializa ocorrencia + Room.
 *
 * <p>Estados: {@code BEFORE_WINDOW} (antes da janela de entrada) / {@code WAITING_ROOM}
 * (dentro da janela, antes do horario) / {@code JOINABLE} (do horario ate o fim)
 * / {@code ENDED} (ultima ocorrencia encerrada) / {@code CANCELLED}.
 */
@Service
public class AppointmentAccessService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentAccessService.class);

    public enum State {BEFORE_WINDOW, WAITING_ROOM, JOINABLE, ENDED, CANCELLED}

    private final AppointmentRepository appointmentRepository;
    private final RecurrenceService recurrenceService;
    private final OccurrenceMaterializer materializer;
    private final AppointmentProperties properties;
    private final Clock clock;

    public AppointmentAccessService(AppointmentRepository appointmentRepository,
                                    RecurrenceService recurrenceService,
                                    OccurrenceMaterializer materializer,
                                    AppointmentProperties properties,
                                    Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.recurrenceService = recurrenceService;
        this.materializer = materializer;
        this.properties = properties;
        this.clock = clock;
    }

    public record Resolution(
            Appointment appointment,
            State state,
            Instant scheduledStart,
            Instant scheduledEnd,
            Instant joinWindowOpensAt,
            String roomId,
            Instant nextOccurrenceStart
    ) {
        public boolean canJoin() {
            return state == State.WAITING_ROOM || state == State.JOINABLE;
        }
    }

    /**
     * Resolve o link, materializando ocorrencia/Room quando dentro da janela. Nao
     * e' {@code @Transactional}: a materializacao roda em transacoes proprias
     * ({@link OccurrenceMaterializer}) para o retry de concorrencia funcionar.
     */
    public Resolution resolve(String publicAccessId) {
        Appointment appointment = appointmentRepository.findByPublicAccessId(publicAccessId)
                .orElseThrow(() -> {
                    log.atWarn()
                            .addKeyValue("event", LogEvents.APPOINTMENT_NOT_FOUND)
                            .setMessage("public appointment link not found")
                            .log();
                    return new ApiException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND",
                            "Atendimento nao encontrado.");
                });

        Instant now = clock.instant();
        Duration joinEarly = properties.joinEarly();
        Duration duration = Duration.ofMinutes(appointment.getDurationMinutes());

        if (appointment.isCancelled()) {
            return new Resolution(appointment, State.CANCELLED, null, null, null, null, null);
        }

        Optional<Instant> active = recurrenceService.activeStartFor(appointment, now, joinEarly);
        if (active.isPresent()) {
            Instant start = active.get();
            Instant end = start.plus(duration);
            Instant opensAt = start.minus(joinEarly);
            State state = now.isBefore(start) ? State.WAITING_ROOM : State.JOINABLE;
            OccurrenceMaterializer.Materialized m = materializeWithRetry(appointment, start, end, now);
            log.atInfo()
                    .addKeyValue("event", LogEvents.APPOINTMENT_OCCURRENCE_RESOLVED)
                    .addKeyValue("appointmentId", appointment.getId())
                    .addKeyValue("occurrenceId", m.occurrence().getId())
                    .addKeyValue("roomId", m.room().getRoomId())
                    .addKeyValue("state", state.name())
                    .setMessage("appointment occurrence resolved")
                    .log();
            return new Resolution(appointment, state, start, end, opensAt, m.room().getRoomId(), null);
        }

        Optional<Instant> next = recurrenceService.nextStartOnOrAfter(appointment, now);
        if (next.isPresent()) {
            Instant start = next.get();
            return new Resolution(appointment, State.BEFORE_WINDOW, start, start.plus(duration),
                    start.minus(joinEarly), null, start);
        }
        return new Resolution(appointment, State.ENDED, null, null, null, null, null);
    }

    private OccurrenceMaterializer.Materialized materializeWithRetry(Appointment appointment,
                                                                    Instant start, Instant end, Instant now) {
        try {
            return materializer.getOrCreate(appointment, start, end, now);
        } catch (DataIntegrityViolationException race) {
            // Outra requisicao/scheduler ganhou a corrida: as linhas ja existem.
            return materializer.getOrCreate(appointment, start, end, now);
        }
    }

}
