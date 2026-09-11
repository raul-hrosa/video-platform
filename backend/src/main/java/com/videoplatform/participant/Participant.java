package com.videoplatform.participant;

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
 * Identidade de um participante dentro de uma Room (Sprint 9 §10). Diferente de
 * {@link ParticipantSession}, que representa cada permanencia: se a mesma pessoa
 * entra e sai varias vezes, ha um unico {@code Participant} e varias sessoes.
 *
 * <p>A identidade e' o {@code participant_ref} — o identity do LiveKit
 * ({@code user:{uuid}} ou {@code guest:{uuid}}), gerado sempre pelo backend.
 */
@Entity
@Table(name = "participants")
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "room_id", nullable = false, updatable = false)
    private String roomId;

    @Column(name = "participant_ref", nullable = false, updatable = false)
    private String participantRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16, updatable = false)
    private ParticipantKind kind;

    /** Preenchido quando {@code kind = USER}. */
    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected Participant() {
    }

    public static Participant create(String roomId, String participantRef, ParticipantKind kind,
                                     UUID userId, String displayName, Instant now) {
        Participant p = new Participant();
        p.roomId = roomId;
        p.participantRef = participantRef;
        p.kind = kind;
        p.userId = userId;
        p.displayName = displayName;
        p.firstSeenAt = now;
        p.lastSeenAt = now;
        return p;
    }

    /** Registra atividade: avanca {@code lastSeenAt} e atualiza o nome quando informado. */
    public void touch(String displayName, Instant now) {
        if (now.isAfter(this.lastSeenAt)) {
            this.lastSeenAt = now;
        }
        if (displayName != null && !displayName.isBlank()) {
            this.displayName = displayName;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getParticipantRef() {
        return participantRef;
    }

    public ParticipantKind getKind() {
        return kind;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
