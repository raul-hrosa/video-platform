-- Sprint 8: agendamento. Appointment representa o compromisso/link permanente
-- (§2, §63). Nao representa a chamada — isso e' a Room, via Occurrence.
-- public_access_id e' o identificador publico do link /r/{id}: aleatorio, nao
-- sequencial, nunca revela organization_id nem ids internos (§3).

CREATE TABLE appointments (
    id                     UUID PRIMARY KEY,
    organization_id        UUID        NOT NULL REFERENCES organizations (id),
    room_profile_id        UUID        NOT NULL REFERENCES room_profiles (id),
    owner_id               UUID        NOT NULL REFERENCES users (id),
    title                  VARCHAR(200) NOT NULL,
    public_access_id       VARCHAR(32) NOT NULL,
    participant_name       VARCHAR(120),
    -- Snapshot da duracao do Profile no momento da criacao/edicao (§8): alterar o
    -- Profile depois nao mexe em ocorrencias ja realizadas.
    duration_minutes       INT         NOT NULL,
    timezone               VARCHAR(64) NOT NULL,
    starts_at              TIMESTAMPTZ NOT NULL,
    recurrence_type        VARCHAR(16) NOT NULL DEFAULT 'NONE',
    recurrence_day_of_week VARCHAR(16),
    recurrence_until       DATE,
    status                 VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at             TIMESTAMPTZ NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL,
    version                BIGINT      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_appointments_public_access_id ON appointments (public_access_id);
CREATE INDEX idx_appointments_org   ON appointments (organization_id);
CREATE INDEX idx_appointments_owner ON appointments (owner_id);
