package com.videoplatform.appointment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentOccurrenceRepository extends JpaRepository<AppointmentOccurrence, UUID> {

    Optional<AppointmentOccurrence> findByAppointmentIdAndScheduledStart(UUID appointmentId, Instant scheduledStart);

    List<AppointmentOccurrence> findByAppointmentIdOrderByScheduledStartDesc(UUID appointmentId);

    boolean existsByAppointmentIdAndScheduledStart(UUID appointmentId, Instant scheduledStart);

    List<AppointmentOccurrence> findByStatusIn(Collection<OccurrenceStatus> statuses);
}
