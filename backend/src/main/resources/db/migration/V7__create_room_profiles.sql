-- Sprint 5: RoomProfile — configuracao reutilizavel a partir da qual as Rooms
-- sao criadas. Um Profile pertence a um usuario e gera N Rooms.

CREATE TABLE room_profiles (
    id               UUID         PRIMARY KEY,
    owner_id         UUID         NOT NULL REFERENCES users (id),
    name             VARCHAR(120) NOT NULL,
    duration_minutes INT          NOT NULL,
    type             VARCHAR(32)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    deleted_at       TIMESTAMPTZ,
    CONSTRAINT chk_room_profiles_duration
        CHECK (duration_minutes BETWEEN 1 AND 480)
);

-- So os perfis ativos sao listados/consultados; o soft delete nao bloqueia
-- recriar um perfil com o mesmo nome.
CREATE INDEX idx_room_profiles_owner_active
    ON room_profiles (owner_id)
    WHERE deleted_at IS NULL;
