-- Sprint 9 (§6, §16): a Room passa a ser a entidade central de infraestrutura de
-- video e nao precisa mais de um Room Profile / Appointment para existir. As
-- colunas herdadas do fluxo anterior ficam opcionais. Nenhuma linha e' alterada:
-- toda Room existente ja tem esses campos preenchidos (veio de Profile ou de
-- ocorrencia de Appointment).

ALTER TABLE rooms ALTER COLUMN room_profile_id  DROP NOT NULL;
ALTER TABLE rooms ALTER COLUMN name             DROP NOT NULL;
ALTER TABLE rooms ALTER COLUMN duration_minutes DROP NOT NULL;
ALTER TABLE rooms ALTER COLUMN expires_at       DROP NOT NULL;
