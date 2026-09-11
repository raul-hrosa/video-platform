-- Sprint 4: toda Room passa a ter um dono (usuario autenticado).
-- Sem dados de producao -> limpa as tabelas dependentes e adiciona a coluna
-- NOT NULL. Em um banco com dados reais isso seria um backfill.

DELETE FROM connection_quality_metrics;
DELETE FROM participant_sessions;
DELETE FROM webhook_events;
DELETE FROM rooms;

ALTER TABLE rooms
    ADD COLUMN owner_id UUID NOT NULL REFERENCES users (id);

CREATE INDEX idx_rooms_owner ON rooms (owner_id);
