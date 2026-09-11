-- Sprint 7: migra usuarios existentes para o modelo multi-tenant. Cada usuario
-- sem membership ganha uma Organization pessoal e vira OWNER dela (Sprint 7 §8).
-- Idempotente: reexecucao nao cria duplicatas; numa base vazia nao faz nada.

DO $$
DECLARE
    u        RECORD;
    new_org  UUID;
    base     TEXT;
BEGIN
    FOR u IN
        SELECT usr.id, usr.name
        FROM users usr
        WHERE NOT EXISTS (
            SELECT 1 FROM organization_members m WHERE m.user_id = usr.id
        )
    LOOP
        base := COALESCE(
            NULLIF(trim(BOTH '-' FROM lower(regexp_replace(u.name, '[^a-zA-Z0-9]+', '-', 'g'))), ''),
            'org'
        );

        new_org := gen_random_uuid();
        INSERT INTO organizations (id, name, slug, created_at, updated_at)
        VALUES (
            new_org,
            u.name,
            base || '-' || substr(replace(u.id::text, '-', ''), 1, 8),
            now(),
            now()
        );

        INSERT INTO organization_members (id, organization_id, user_id, role, created_at)
        VALUES (gen_random_uuid(), new_org, u.id, 'OWNER', now());
    END LOOP;
END $$;
