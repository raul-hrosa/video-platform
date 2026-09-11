-- Sprint 6: histórico de salas. A listagem ordena por created_at DESC e filtra
-- por owner/status/profile/intervalo de criação. Índices para sustentar isso
-- (§36). Só o necessário — os demais campos citados na sprint já têm índice
-- desde as migrations anteriores (owner_id, status, room_profile_id, expires_at;
-- participant_sessions.room_id / user_id).

CREATE INDEX idx_rooms_owner_created_at ON rooms (owner_id, created_at DESC);

CREATE INDEX idx_participant_sessions_room_joined
    ON participant_sessions (room_id, joined_at);
