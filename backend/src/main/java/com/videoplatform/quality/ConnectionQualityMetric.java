package com.videoplatform.quality;

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
 * Um snapshot de qualidade de conexao de um participante, pertencente a uma
 * ParticipantSession. Campos de metrica sao nullable: ausente = null, nunca 0.
 */
@Entity
@Table(name = "connection_quality_metrics")
public class ConnectionQualityMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "room_id", nullable = false, updatable = false)
    private String roomId;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "participant_id", nullable = false, updatable = false)
    private String participantId;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_level", nullable = false, length = 16)
    private QualityLevel qualityLevel;

    @Column(name = "rtt_ms")
    private Integer rttMs;

    @Column(name = "packet_loss_percent")
    private Double packetLossPercent;

    @Column(name = "jitter_ms")
    private Integer jitterMs;

    @Column(name = "audio_bitrate")
    private Long audioBitrate;

    @Column(name = "video_bitrate")
    private Long videoBitrate;

    @Column(name = "video_width")
    private Integer videoWidth;

    @Column(name = "video_height")
    private Integer videoHeight;

    @Column(name = "video_fps")
    private Integer videoFps;

    @Column(name = "connection_state", length = 32)
    private String connectionState;

    protected ConnectionQualityMetric() {
    }

    @SuppressWarnings("java:S107") // snapshot com muitos campos opcionais, por natureza
    public static ConnectionQualityMetric record(
            String roomId, UUID sessionId, String participantId, Instant recordedAt,
            QualityLevel qualityLevel, Integer rttMs, Double packetLossPercent, Integer jitterMs,
            Long audioBitrate, Long videoBitrate, Integer videoWidth, Integer videoHeight,
            Integer videoFps, String connectionState) {
        ConnectionQualityMetric m = new ConnectionQualityMetric();
        m.roomId = roomId;
        m.sessionId = sessionId;
        m.participantId = participantId;
        m.recordedAt = recordedAt;
        m.qualityLevel = qualityLevel;
        m.rttMs = rttMs;
        m.packetLossPercent = packetLossPercent;
        m.jitterMs = jitterMs;
        m.audioBitrate = audioBitrate;
        m.videoBitrate = videoBitrate;
        m.videoWidth = videoWidth;
        m.videoHeight = videoHeight;
        m.videoFps = videoFps;
        m.connectionState = connectionState;
        return m;
    }

    public UUID getId() {
        return id;
    }

    public String getRoomId() {
        return roomId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getParticipantId() {
        return participantId;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public QualityLevel getQualityLevel() {
        return qualityLevel;
    }

    public Integer getRttMs() {
        return rttMs;
    }

    public Double getPacketLossPercent() {
        return packetLossPercent;
    }

    public Integer getJitterMs() {
        return jitterMs;
    }

    public Long getAudioBitrate() {
        return audioBitrate;
    }

    public Long getVideoBitrate() {
        return videoBitrate;
    }

    public Integer getVideoWidth() {
        return videoWidth;
    }

    public Integer getVideoHeight() {
        return videoHeight;
    }

    public Integer getVideoFps() {
        return videoFps;
    }

    public String getConnectionState() {
        return connectionState;
    }
}
