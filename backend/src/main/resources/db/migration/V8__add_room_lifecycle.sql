-- Sprint 5: toda Room passa a nascer de um RoomProfile e a ter expiracao.
-- Sem dados de producao -> limpa as tabelas dependentes (mesmo padrao da V5).

DELETE FROM connection_quality_metrics;
DELETE FROM participant_sessions;
DELETE FROM webhook_events;
DELETE FROM rooms;

ALTER TABLE rooms
    ADD COLUMN room_profile_id  UUID        NOT NULL REFERENCES room_profiles (id),
    ADD COLUMN duration_minutes INT         NOT NULL,
    ADD COLUMN expires_at       TIMESTAMPTZ NOT NULL,
    ADD COLUMN version          BIGINT      NOT NULL DEFAULT 0;

CREATE INDEX idx_rooms_profile ON rooms (room_profile_id);

-- Suporta a varredura do scheduler (status ativo + expires_at vencido).
CREATE INDEX idx_rooms_expiry
    ON rooms (expires_at)
    WHERE status IN ('WAITING', 'ACTIVE');
