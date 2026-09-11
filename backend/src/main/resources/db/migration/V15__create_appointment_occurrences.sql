-- Sprint 8: cada ocorrencia concreta de um Appointment (§2, §4). Uma ocorrencia
-- ganha sua propria Room (§33, §49). A constraint UNIQUE(appointment_id,
-- scheduled_start) garante idempotencia e trata concorrencia (§21, §55): nunca
-- duas ocorrencias/Rooms para o mesmo instante agendado.

CREATE TABLE appointment_occurrences (
    id              UUID PRIMARY KEY,
    appointment_id  UUID        NOT NULL REFERENCES appointments (id),
    scheduled_start TIMESTAMPTZ NOT NULL,
    scheduled_end   TIMESTAMPTZ NOT NULL,
    room_id         UUID        REFERENCES rooms (id),
    status          VARCHAR(16) NOT NULL DEFAULT 'SCHEDULED',
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_occurrence_slot UNIQUE (appointment_id, scheduled_start)
);

CREATE INDEX idx_occurrences_appointment     ON appointment_occurrences (appointment_id);
CREATE INDEX idx_occurrences_scheduled_start ON appointment_occurrences (scheduled_start);
