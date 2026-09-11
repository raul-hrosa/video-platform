-- Sprint 2: gestao de salas, sessoes de participantes e eventos de webhook.
-- rooms e' recriada (o MVP nao tem dados de producao, apenas a sala de exemplo).

DROP TABLE IF EXISTS rooms;

CREATE TABLE rooms (
    id         UUID         PRIMARY KEY,
    room_id    VARCHAR(128) NOT NULL UNIQUE,
    name       VARCHAR(255) NOT NULL,
    status     VARCHAR(16)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    started_at TIMESTAMPTZ,
    ended_at   TIMESTAMPTZ
);

CREATE INDEX idx_rooms_status ON rooms (status);

CREATE TABLE participant_sessions (
    id               UUID         PRIMARY KEY,
    room_id          VARCHAR(128) NOT NULL,
    participant_id   VARCHAR(255) NOT NULL,
    participant_name VARCHAR(255) NOT NULL,
    joined_at        TIMESTAMPTZ  NOT NULL,
    left_at          TIMESTAMPTZ,
    duration_seconds BIGINT,
    CONSTRAINT chk_left_after_joined
        CHECK (left_at IS NULL OR left_at >= joined_at),
    CONSTRAINT chk_duration_non_negative
        CHECK (duration_seconds IS NULL OR duration_seconds >= 0)
);

CREATE INDEX idx_participant_sessions_room ON participant_sessions (room_id);
CREATE INDEX idx_participant_sessions_room_participant
    ON participant_sessions (room_id, participant_id);
CREATE INDEX idx_participant_sessions_open
    ON participant_sessions (room_id, participant_id)
    WHERE left_at IS NULL;

CREATE TABLE webhook_events (
    id           UUID         PRIMARY KEY,
    event_id     VARCHAR(255) NOT NULL UNIQUE,
    event_type   VARCHAR(64)  NOT NULL,
    received_at  TIMESTAMPTZ  NOT NULL,
    processed_at TIMESTAMPTZ,
    status       VARCHAR(16)  NOT NULL,
    payload      TEXT         NOT NULL
);

CREATE INDEX idx_webhook_events_type ON webhook_events (event_type);
