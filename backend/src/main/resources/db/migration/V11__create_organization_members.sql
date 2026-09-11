-- Sprint 7: relacao usuario <-> Organization. Tabela de juncao (nao
-- users.organization_id) para permitir, no futuro, um usuario em varias
-- Organizations. Nesta sprint cada usuario tem exatamente um membership.

CREATE TABLE organization_members (
    id              UUID        PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organizations (id),
    user_id         UUID        NOT NULL REFERENCES users (id),
    role            VARCHAR(16) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_org_members_org_user UNIQUE (organization_id, user_id)
);

CREATE INDEX idx_org_members_org  ON organization_members (organization_id);
CREATE INDEX idx_org_members_user ON organization_members (user_id);
