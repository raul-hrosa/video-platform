package com.videoplatform.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Vinculo entre um {@code User} e uma {@link Organization} com um {@link OrgRole}.
 * Tabela de juncao (nao {@code users.organization_id}) para permitir, no futuro,
 * um usuario em varias Organizations (Sprint 7 §5, §32). {@code UNIQUE
 * (organization_id, user_id)} garante um unico vinculo por par.
 */
@Entity
@Table(name = "organization_members")
public class OrganizationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private OrgRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrganizationMember() {
    }

    public static OrganizationMember of(UUID organizationId, UUID userId, OrgRole role) {
        OrganizationMember member = new OrganizationMember();
        member.organizationId = organizationId;
        member.userId = userId;
        member.role = role;
        return member;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public OrgRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
