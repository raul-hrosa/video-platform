package com.videoplatform.appointment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    Optional<Appointment> findByPublicAccessId(String publicAccessId);

    boolean existsByPublicAccessId(String publicAccessId);

    List<Appointment> findByOrganizationIdOrderByStartsAtAsc(UUID organizationId);

    List<Appointment> findByStatus(AppointmentStatus status);
}
