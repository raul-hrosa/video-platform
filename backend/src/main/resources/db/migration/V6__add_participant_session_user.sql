-- Sprint 4: liga a participacao ao usuario autenticado (extraido do identity
-- LiveKit "user:{uuid}"). Nullable — defensivo caso o identity nao case.

ALTER TABLE participant_sessions
    ADD COLUMN user_id UUID;

CREATE INDEX idx_participant_sessions_user ON participant_sessions (user_id);
