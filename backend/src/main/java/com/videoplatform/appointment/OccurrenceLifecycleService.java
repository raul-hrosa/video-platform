package com.videoplatform.appointment;

import com.videoplatform.common.logging.LogEvents;
import com.videoplatform.room.RoomService;
import com.videoplatform.room.RoomStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mantem as ocorrencias em dia (§20): materializa preguicosamente a proxima
 * ocorrencia de cada Appointment ativo dentro de uma janela curta de preparo (sem
 * criar Room ainda — §29) e reflete nas ocorrencias o estado da Room vinculada.
 * A criacao pelo scheduler nao e' a unica forma (o acesso ao link tambem cria).
 */
@Service
public class OccurrenceLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(OccurrenceLifecycleService.class);

    private final AppointmentRepository appointmentRepository;
    private final AppointmentOccurrenceRepository occurrenceRepository;
    private final RecurrenceService recurrenceService;
    private final RoomService roomService;
    private final AppointmentProperties properties;
    private final Clock clock;

    public OccurrenceLifecycleService(AppointmentRepository appointmentRepository,
                                      AppointmentOccurrenceRepository occurrenceRepository,
                                      RecurrenceService recurrenceService,
                                      RoomService roomService,
                                      AppointmentProperties properties,
                                      Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.recurrenceService = recurrenceService;
        this.roomService = roomService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void prepareUpcoming() {
        Instant now = clock.instant();
        Instant horizon = now.plus(properties.prepareAhead());
        for (Appointment appt : appointmentRepository.findByStatus(AppointmentStatus.ACTIVE)) {
            recurrenceService.nextStartOnOrAfter(appt, now).ifPresent(start -> {
                if (start.isAfter(horizon)
                        || occurrenceRepository.existsByAppointmentIdAndScheduledStart(appt.getId(), start)) {
                    return;
                }
                Instant end = start.plus(Duration.ofMinutes(appt.getDurationMinutes()));
                try {
                    AppointmentOccurrence created = occurrenceRepository.saveAndFlush(
                            AppointmentOccurrence.schedule(appt.getId(), start, end, now));
                    log.atInfo()
                            .addKeyValue("event", LogEvents.APPOINTMENT_OCCURRENCE_CREATED)
                            .addKeyValue("appointmentId", appt.getId())
                            .addKeyValue("occurrenceId", created.getId())
                            .addKeyValue("scheduledStart", start.toString())
                            .setMessage("appointment occurrence prepared by scheduler")
                            .log();
                } catch (DataIntegrityViolationException raceLost) {
                    // acesso pelo link criou a mesma ocorrencia primeiro — ok
                }
            });
        }
    }

    @Transactional
    public void syncStatuses() {
        Instant now = clock.instant();
        List<AppointmentOccurrence> open = occurrenceRepository.findByStatusIn(
                EnumSet.of(OccurrenceStatus.SCHEDULED, OccurrenceStatus.WAITING, OccurrenceStatus.ACTIVE));
        Map<UUID, RoomStatus> roomStatuses = roomService.roomStatusByIds(
                open.stream().map(AppointmentOccurrence::getRoomId).filter(java.util.Objects::nonNull).toList());

        for (AppointmentOccurrence occ : open) {
            if (occ.getRoomId() == null) {
                if (now.isAfter(occ.getScheduledEnd())) {
                    occ.markStatus(OccurrenceStatus.EXPIRED);
                }
                continue;
            }
            RoomStatus rs = roomStatuses.get(occ.getRoomId());
            if (rs == null) {
                continue;
            }
            switch (rs) {
                case ACTIVE -> occ.markStatus(OccurrenceStatus.ACTIVE);
                case ENDED -> occ.markStatus(OccurrenceStatus.ENDED);
                case EXPIRED -> occ.markStatus(OccurrenceStatus.EXPIRED);
                case WAITING -> occ.markStatus(OccurrenceStatus.WAITING);
            }
        }
    }
}
