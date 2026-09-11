package com.videoplatform.participant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Representa a permanencia de um participante numa sala, de joined_at ate left_at.
 * Os horarios sao sempre determinados pelo backend a partir dos eventos do LiveKit.
 */
@Entity
@Table(name = "participant_sessions")
public class ParticipantSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "room_id", nullable = false, updatable = false)
    private String roomId;

    @Column(name = "participant_id", nullable = false, updatable = false)
    private String participantId;

    /** FK para a identidade {@link Participant} desta sala (Sprint 9 §10). */
    @Column(name = "participant_ref_id")
    private UUID participantRefId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "participant_name", nullable = false, updatable = false)
    private String participantName;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "reconnect_count", nullable = false)
    private int reconnectCount = 0;

    protected ParticipantSession() {
    }

    public static ParticipantSession start(String roomId, String participantId, UUID userId,
                                           String participantName, Instant joinedAt) {
        ParticipantSession session = new ParticipantSession();
        session.roomId = roomId;
        session.participantId = participantId;
        session.userId = userId;
        session.participantName = participantName;
        session.joinedAt = joinedAt;
        return session;
    }

    /** Liga esta sessao a' identidade {@link Participant} resolvida (Sprint 9 §10). */
    public void assignParticipant(UUID participantRefId) {
        this.participantRefId = participantRefId;
    }

    public UUID getParticipantRefId() {
        return participantRefId;
    }

    public boolean belongsTo(UUID userId) {
        return this.userId != null && this.userId.equals(userId);
    }

    /** Encerra a sessao. A duracao e' calculada aqui, no backend, e nunca negativa. */
    public void end(Instant leftAt) {
        Instant effectiveLeft = leftAt.isBefore(joinedAt) ? joinedAt : leftAt;
        this.leftAt = effectiveLeft;
        this.durationSeconds = Duration.between(joinedAt, effectiveLeft).getSeconds();
    }

    public boolean isOpen() {
        return leftAt == null;
    }

    /** Reconexao detectada pelo backend (novo participant_joined para sessao aberta). */
    public void registerReconnect() {
        this.reconnectCount++;
    }

    /** Ajusta a contagem com a informada pelo cliente (reconexoes "soft"), sem regredir. */
    public void bumpReconnectCount(int fromClient) {
        if (fromClient > this.reconnectCount) {
            this.reconnectCount = fromClient;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getParticipantId() {
        return participantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getParticipantName() {
        return participantName;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Instant getLeftAt() {
        return leftAt;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public int getReconnectCount() {
        return reconnectCount;
    }
}
