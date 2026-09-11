package com.videoplatform.appointment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma realizacao concreta de um {@link Appointment} (§2). Materializada de forma
 * preguicosa no acesso ao link ou pelo scheduler de preparo (§20). A Room e'
 * criada junto ({@code roomId}) e nunca reaproveitada entre ocorrencias (§33).
 */
@Entity
@Table(name = "appointment_occurrences")
public class AppointmentOccurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "appointment_id", nullable = false, updatable = false)
    private UUID appointmentId;

    @Column(name = "scheduled_start", nullable = false, updatable = false)
    private Instant scheduledStart;

    @Column(name = "scheduled_end", nullable = false, updatable = false)
    private Instant scheduledEnd;

    @Column(name = "room_id")
    private UUID roomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OccurrenceStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AppointmentOccurrence() {
    }

    public static AppointmentOccurrence schedule(UUID appointmentId, Instant scheduledStart,
                                                 Instant scheduledEnd, Instant now) {
        AppointmentOccurrence o = new AppointmentOccurrence();
        o.appointmentId = appointmentId;
        o.scheduledStart = scheduledStart;
        o.scheduledEnd = scheduledEnd;
        o.status = OccurrenceStatus.SCHEDULED;
        o.createdAt = now;
        return o;
    }

    public void attachRoom(UUID roomId) {
        this.roomId = roomId;
    }

    public void markStatus(OccurrenceStatus status) {
        this.status = status;
    }

    public void cancel() {
        this.status = OccurrenceStatus.CANCELLED;
    }

    public boolean hasRoom() {
        return roomId != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public Instant getScheduledStart() {
        return scheduledStart;
    }

    public Instant getScheduledEnd() {
        return scheduledEnd;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public OccurrenceStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
