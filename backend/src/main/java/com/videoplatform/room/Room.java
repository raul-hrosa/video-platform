package com.videoplatform.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "room_id", nullable = false, unique = true, updatable = false)
    private String roomId;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    /** Organization dona do recurso (Sprint 7). Diferente de {@code ownerId} (quem criou). */
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    /**
     * Profile de origem (Sprint 5). {@code null} para Rooms criadas pelo fluxo
     * direto de infraestrutura (Sprint 9 §6) — sem Profile, sem nome, sem duracao.
     */
    @Column(name = "room_profile_id", updatable = false)
    private UUID roomProfileId;

    /**
     * Ocorrencia de Appointment que originou esta Room (Sprint 8 §4). {@code null}
     * para Rooms criadas pelo fluxo direto de Profile (Sprint 5).
     */
    @Column(name = "appointment_occurrence_id", updatable = false)
    private UUID appointmentOccurrenceId;

    /** Nome herdado do Profile/Appointment. {@code null} para Rooms diretas (Sprint 9 §4/§6). */
    @Column(name = "name")
    private String name;

    /**
     * Snapshot da duracao do Profile no momento da criacao (Sprint 5 §17).
     * {@code null} para Rooms diretas — que nao expiram automaticamente (Sprint 9 §6).
     */
    @Column(name = "duration_minutes", updatable = false)
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RoomStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    /** Fim da janela de uso. {@code null} para Rooms diretas — nunca expiram (Sprint 9 §6). */
    @Column(name = "expires_at", updatable = false)
    private Instant expiresAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Room() {
    }

    /**
     * Cria uma Room a partir de um RoomProfile. {@code name} e {@code durationMinutes}
     * sao um snapshot — alterar o Profile depois nunca altera esta Room (Sprint 5 §60).
     */
    public static Room createFromProfile(String roomId, UUID roomProfileId, UUID organizationId,
                                         String name, int durationMinutes, UUID ownerId, Instant now) {
        Room room = new Room();
        room.roomId = roomId;
        room.ownerId = ownerId;
        room.organizationId = organizationId;
        room.roomProfileId = roomProfileId;
        room.name = name;
        room.durationMinutes = durationMinutes;
        room.status = RoomStatus.WAITING;
        room.createdAt = now;
        room.expiresAt = now.plus(Duration.ofMinutes(durationMinutes));
        return room;
    }

    /**
     * Cria a Room de uma ocorrencia de Appointment (Sprint 8 §28.4). Diferente de
     * {@link #createFromProfile}, {@code expiresAt} e' o fim agendado da ocorrencia
     * (nao {@code now + duracao}) — a janela real da chamada. {@code name} e
     * {@code durationMinutes} sao snapshot do Appointment.
     */
    public static Room createForOccurrence(String roomId, UUID roomProfileId, UUID organizationId,
                                           String name, int durationMinutes, UUID ownerId,
                                           UUID appointmentOccurrenceId, Instant scheduledEnd, Instant now) {
        Room room = new Room();
        room.roomId = roomId;
        room.ownerId = ownerId;
        room.organizationId = organizationId;
        room.roomProfileId = roomProfileId;
        room.appointmentOccurrenceId = appointmentOccurrenceId;
        room.name = name;
        room.durationMinutes = durationMinutes;
        room.status = RoomStatus.WAITING;
        room.createdAt = now;
        room.expiresAt = scheduledEnd;
        return room;
    }

    /**
     * Cria uma Room de infraestrutura, sem Profile nem Appointment (Sprint 9 §6).
     * Sem {@code name}, sem {@code durationMinutes} e sem {@code expiresAt}: a sala
     * so termina por evento do LiveKit ({@code room_finished}), nunca por expiracao.
     */
    public static Room createDirect(String roomId, UUID organizationId, UUID ownerId, Instant now) {
        Room room = new Room();
        room.roomId = roomId;
        room.ownerId = ownerId;
        room.organizationId = organizationId;
        room.status = RoomStatus.WAITING;
        room.createdAt = now;
        return room;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }

    public boolean belongsToOrg(UUID orgId) {
        return organizationId.equals(orgId);
    }

    /** {@code true} quando {@code now >= expiresAt} e a sala ainda nao terminou. */
    public boolean isExpired(Instant now) {
        return expiresAt != null && status != RoomStatus.ENDED && !now.isBefore(expiresAt);
    }

    /** Origem da Room (Sprint 9): OCCURRENCE (agendamento) &gt; PROFILE (Sprint 5) &gt; DIRECT. */
    public CreationMode creationMode() {
        if (appointmentOccurrenceId != null) {
            return CreationMode.OCCURRENCE;
        }
        return roomProfileId != null ? CreationMode.PROFILE : CreationMode.DIRECT;
    }

    /** Status resumido exposto na API/UI (Sprint 9 §7). */
    public String displayStatus() {
        return switch (status) {
            case ACTIVE -> "LIVE";
            case WAITING -> "IDLE";
            case ENDED, EXPIRED -> "ENDED";
        };
    }

    public enum CreationMode {
        DIRECT, PROFILE, OCCURRENCE
    }

    /** Marca a sala como ACTIVE na primeira vez. No-op se ja saiu de WAITING. */
    public void markActive(Instant at) {
        if (status == RoomStatus.WAITING) {
            status = RoomStatus.ACTIVE;
            startedAt = at;
        }
    }

    /** Encerra a sala. No-op se ja e' terminal (ENDED ou EXPIRED — Sprint 5 §20). */
    public void markEnded(Instant at) {
        if (status == RoomStatus.WAITING || status == RoomStatus.ACTIVE) {
            status = RoomStatus.ENDED;
            endedAt = at;
        }
    }

    /**
     * WAITING|ACTIVE -> EXPIRED. No-op nos estados terminais — EXPIRED nunca volta
     * para ACTIVE (Sprint 5 §37). Participantes ja conectados nao sao afetados.
     */
    public void markExpired(Instant at) {
        if (status == RoomStatus.WAITING || status == RoomStatus.ACTIVE) {
            status = RoomStatus.EXPIRED;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getRoomId() {
        return roomId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getRoomProfileId() {
        return roomProfileId;
    }

    public UUID getAppointmentOccurrenceId() {
        return appointmentOccurrenceId;
    }

    public String getName() {
        return name;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
