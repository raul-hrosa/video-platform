-- Sprint 7: Organization e' a unidade de isolamento de dados da plataforma.
-- Um usuario pertence a uma Organization atraves de organization_members (V11);
-- os recursos (room_profiles, rooms) passam a ter organization_id (V13).

CREATE TABLE organizations (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    slug       VARCHAR(140) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_organizations_slug UNIQUE (slug)
);
