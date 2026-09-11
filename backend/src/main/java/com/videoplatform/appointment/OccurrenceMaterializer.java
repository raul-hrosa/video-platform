package com.videoplatform.appointment;

import com.videoplatform.common.logging.LogEvents;
import com.videoplatform.room.Room;
import com.videoplatform.room.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Materializa (get-or-create) a ocorrencia atual de um Appointment e a sua Room
 * (§20, §21, §55). Cada chamada roda na sua propria transacao: se a constraint
 * {@code UNIQUE(appointment_id, scheduled_start)} ou o indice unico de
 * {@code rooms.appointment_occurrence_id} disparar sob concorrencia, a excecao
 * propaga, a transacao faz rollback e o chamador tenta de novo — a segunda
 * passada encontra a linha que o vencedor commitou. Nunca duas Rooms.
 */
@Component
public class OccurrenceMaterializer {

    private static final Logger log = LoggerFactory.getLogger(OccurrenceMaterializer.class);

    private final AppointmentOccurrenceRepository occurrenceRepository;
    private final RoomService roomService;

    public OccurrenceMaterializer(AppointmentOccurrenceRepository occurrenceRepository,
                                  RoomService roomService) {
        this.occurrenceRepository = occurrenceRepository;
        this.roomService = roomService;
    }

    public record Materialized(AppointmentOccurrence occurrence, Room room) {
    }

    @Transactional
    public Materialized getOrCreate(Appointment appointment, Instant scheduledStart,
                                    Instant scheduledEnd, Instant now) {
        AppointmentOccurrence occurrence = occurrenceRepository
                .findByAppointmentIdAndScheduledStart(appointment.getId(), scheduledStart)
                .orElseGet(() -> {
                    AppointmentOccurrence created = occurrenceRepository.saveAndFlush(
                            AppointmentOccurrence.schedule(appointment.getId(), scheduledStart,
                                    scheduledEnd, now));
                    log.atInfo()
                            .addKeyValue("event", LogEvents.APPOINTMENT_OCCURRENCE_CREATED)
                            .addKeyValue("appointmentId", appointment.getId())
                            .addKeyValue("occurrenceId", created.getId())
                            .addKeyValue("organizationId", appointment.getOrganizationId())
                            .addKeyValue("scheduledStart", scheduledStart.toString())
                            .setMessage("appointment occurrence created")
                            .log();
                    return created;
                });

        if (occurrence.getRoomId() != null) {
            return new Materialized(occurrence, roomService.requireById(occurrence.getRoomId()));
        }

        Room room = roomService.createForOccurrence(
                appointment.getRoomProfileId(), appointment.getOrganizationId(), appointment.getOwnerId(),
                appointment.getTitle(), appointment.getDurationMinutes(), occurrence.getId(),
                scheduledEnd, now);
        occurrence.attachRoom(room.getId());
        occurrenceRepository.saveAndFlush(occurrence);
        return new Materialized(occurrence, room);
    }
}
