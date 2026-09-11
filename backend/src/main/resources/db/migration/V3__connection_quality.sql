-- Sprint 3: metricas de qualidade de conexao por participante.

ALTER TABLE participant_sessions
    ADD COLUMN reconnect_count INT NOT NULL DEFAULT 0;

CREATE TABLE connection_quality_metrics (
    id                  UUID         PRIMARY KEY,
    room_id             VARCHAR(128) NOT NULL,
    session_id          UUID         NOT NULL REFERENCES participant_sessions (id),
    participant_id      VARCHAR(255) NOT NULL,
    recorded_at         TIMESTAMPTZ  NOT NULL,
    quality_level       VARCHAR(16)  NOT NULL,
    rtt_ms              INT,
    packet_loss_percent DOUBLE PRECISION,
    jitter_ms           INT,
    audio_bitrate       BIGINT,
    video_bitrate       BIGINT,
    video_width         INT,
    video_height        INT,
    video_fps           INT,
    connection_state    VARCHAR(32),
    CONSTRAINT chk_cqm_loss CHECK (
        packet_loss_percent IS NULL
        OR (packet_loss_percent >= 0 AND packet_loss_percent <= 100)),
    CONSTRAINT chk_cqm_rtt CHECK (rtt_ms IS NULL OR rtt_ms >= 0),
    CONSTRAINT chk_cqm_jitter CHECK (jitter_ms IS NULL OR jitter_ms >= 0)
);

CREATE INDEX idx_cqm_session_recorded ON connection_quality_metrics (session_id, recorded_at);
CREATE INDEX idx_cqm_room ON connection_quality_metrics (room_id);
