package com.videoplatform.appointment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * O compromisso agendado e seu link permanente (§2, §63). Nao e' a chamada:
 * cada realizacao vira uma {@link AppointmentOccurrence} e cada ocorrencia ganha
 * sua propria Room.
 *
 * <p>{@code organizationId} = tenant (nunca vem do frontend — §6); {@code ownerId}
 * = quem criou. {@code durationMinutes} e' um snapshot do Room Profile no momento
 * da criacao/edicao (§8).
 */
@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "room_profile_id", nullable = false, updatable = false)
    private UUID roomProfileId;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "public_access_id", nullable = false, unique = true, updatable = false)
    private String publicAccessId;

    @Column(name = "participant_name")
    private String participantName;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_type", nullable = false, length = 16)
    private RecurrenceType recurrenceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_day_of_week", length = 16)
    private DayOfWeek recurrenceDayOfWeek;

    @Column(name = "recurrence_until")
    private LocalDate recurrenceUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AppointmentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Appointment() {
    }

    /**
     * Cria um Appointment. {@code title}/{@code participantName} ja devem vir
     * normalizados. Para {@code WEEKLY} o {@code recurrenceDayOfWeek} e'
     * derivado de {@code startsAt} na timezone informada.
     */
    public static Appointment create(UUID organizationId, UUID roomProfileId, UUID ownerId,
                                     String title, String publicAccessId, String participantName,
                                     int durationMinutes, ZoneId timezone, Instant startsAt,
                                     RecurrenceType recurrenceType, LocalDate recurrenceUntil) {
        Appointment a = new Appointment();
        a.organizationId = organizationId;
        a.roomProfileId = roomProfileId;
        a.ownerId = ownerId;
        a.publicAccessId = publicAccessId;
        a.status = AppointmentStatus.ACTIVE;
        a.applySchedule(title, participantName, durationMinutes, timezone, startsAt,
                recurrenceType, recurrenceUntil);
        return a;
    }

    /**
     * Reaplica agenda/recorrencia (§26). Nao altera ocorrencias ja materializadas
     * — isso e' garantido no serviço, que so recalcula ocorrencias futuras.
     */
    public void applySchedule(String title, String participantName, int durationMinutes,
                              ZoneId timezone, Instant startsAt, RecurrenceType recurrenceType,
                              LocalDate recurrenceUntil) {
        this.title = title;
        this.participantName = participantName;
        this.durationMinutes = durationMinutes;
        this.timezone = timezone.getId();
        this.startsAt = startsAt;
        this.recurrenceType = recurrenceType;
        this.recurrenceUntil = recurrenceType == RecurrenceType.WEEKLY ? recurrenceUntil : null;
        this.recurrenceDayOfWeek = recurrenceType == RecurrenceType.WEEKLY
                ? startsAt.atZone(zoneId()).getDayOfWeek()
                : null;
    }

    public void cancel() {
        this.status = AppointmentStatus.CANCELLED;
    }

    public boolean isCancelled() {
        return status == AppointmentStatus.CANCELLED;
    }

    public boolean belongsToOrg(UUID orgId) {
        return organizationId.equals(orgId);
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getRoomProfileId() {
        return roomProfileId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getTitle() {
        return title;
    }

    public String getPublicAccessId() {
        return publicAccessId;
    }

    public String getParticipantName() {
        return participantName;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public String getTimezone() {
        return timezone;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public RecurrenceType getRecurrenceType() {
        return recurrenceType;
    }

    public DayOfWeek getRecurrenceDayOfWeek() {
        return recurrenceDayOfWeek;
    }

    public LocalDate getRecurrenceUntil() {
        return recurrenceUntil;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
