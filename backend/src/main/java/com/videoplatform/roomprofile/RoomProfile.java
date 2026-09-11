package com.videoplatform.roomprofile;

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

import java.time.Instant;
import java.util.UUID;

/**
 * Configuracao reutilizavel de um tipo de sala. Uma Room nasce de um Profile e
 * copia dele um snapshot ({@code name}, {@code durationMinutes}) — alterar o
 * Profile depois nunca altera Rooms ja criadas (Sprint 5 §60).
 *
 * <p>Exclusao e' <b>soft delete</b> ({@code deletedAt}): o Profile continua
 * ligado ao historico das Rooms, mas nao aparece em listagens nem gera novas
 * Rooms.
 */
@Entity
@Table(name = "room_profiles")
public class RoomProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    /** Organization dona do Profile (Sprint 7 §13). {@code ownerId} = quem criou. */
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private RoomType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected RoomProfile() {
    }

    /** {@code name} ja deve vir normalizado (trim). */
    public static RoomProfile create(UUID organizationId, UUID ownerId, String name,
                                     int durationMinutes, RoomType type) {
        RoomProfile profile = new RoomProfile();
        profile.organizationId = organizationId;
        profile.ownerId = ownerId;
        profile.name = name;
        profile.durationMinutes = durationMinutes;
        profile.type = type;
        return profile;
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

    /** {@code name} ja deve vir normalizado (trim). Nao altera Rooms existentes. */
    public void update(String name, int durationMinutes, RoomType type) {
        this.name = name;
        this.durationMinutes = durationMinutes;
        this.type = type;
    }

    public void softDelete(Instant at) {
        if (this.deletedAt == null) {
            this.deletedAt = at;
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }

    public boolean belongsToOrg(UUID orgId) {
        return organizationId.equals(orgId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public RoomType getType() {
        return type;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
