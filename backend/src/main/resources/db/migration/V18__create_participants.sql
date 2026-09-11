-- Sprint 9 (§10-11): identidade do participante separada da sessao de participacao.
-- Um Participant por (room_id, participant_ref); varias ParticipantSession por
-- Participant (cada entrada/saida). Habilita "total de sessoes / tempo total /
-- reconexoes" por participante.

CREATE TABLE participants (
    id              UUID         PRIMARY KEY,
    room_id         VARCHAR(128) NOT NULL,
    participant_ref VARCHAR(255) NOT NULL,   -- identity LiveKit: user:{uuid} / guest:{uuid}
    kind            VARCHAR(16)  NOT NULL,   -- USER | GUEST
    user_id         UUID,                    -- preenchido quando kind = USER
    display_name    VARCHAR(255) NOT NULL,
    first_seen_at   TIMESTAMPTZ  NOT NULL,
    last_seen_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_participants_room_ref UNIQUE (room_id, participant_ref)
);

CREATE INDEX idx_participants_room ON participants (room_id);
CREATE INDEX idx_participants_user ON participants (user_id);

ALTER TABLE participant_sessions
    ADD COLUMN participant_ref_id UUID REFERENCES participants (id);

CREATE INDEX idx_participant_sessions_participant
    ON participant_sessions (participant_ref_id);

-- Backfill: uma identidade por (room_id, participant_id) ja registrado, com nome
-- da sessao mais recente e janela [min(joined_at), max(joined_at)].
INSERT INTO participants (id, room_id, participant_ref, kind, user_id, display_name, first_seen_at, last_seen_at)
SELECT gen_random_uuid(),
       s.room_id,
       s.participant_id,
       CASE WHEN s.participant_id LIKE 'guest:%' THEN 'GUEST' ELSE 'USER' END,
       s.user_id,
       COALESCE((SELECT s2.participant_name
                   FROM participant_sessions s2
                  WHERE s2.room_id = s.room_id AND s2.participant_id = s.participant_id
                  ORDER BY s2.joined_at DESC
                  LIMIT 1), s.participant_id),
       MIN(s.joined_at),
       MAX(s.joined_at)
FROM participant_sessions s
GROUP BY s.room_id, s.participant_id, s.user_id;

UPDATE participant_sessions ps
SET participant_ref_id = p.id
FROM participants p
WHERE p.room_id = ps.room_id AND p.participant_ref = ps.participant_id;
