-- Sprint 7: RoomProfile e Room passam a pertencer a uma Organization (Sprint 7
-- §10). owner_id continua existindo — representa quem criou o recurso, nao quem
-- o possui. participant_sessions / connection_quality_metrics NAO ganham a
-- coluna (§17/§18): o vinculo com a Organization vem via Room.

ALTER TABLE room_profiles ADD COLUMN organization_id UUID REFERENCES organizations (id);
ALTER TABLE rooms         ADD COLUMN organization_id UUID REFERENCES organizations (id);

-- Backfill a partir do dono do recurso (que ja tem membership OWNER pela V12).
UPDATE room_profiles p
SET organization_id = m.organization_id
FROM organization_members m
WHERE m.user_id = p.owner_id AND p.organization_id IS NULL;

UPDATE rooms r
SET organization_id = m.organization_id
FROM organization_members m
WHERE m.user_id = r.owner_id AND r.organization_id IS NULL;

ALTER TABLE room_profiles ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE rooms         ALTER COLUMN organization_id SET NOT NULL;

CREATE INDEX idx_room_profiles_org ON room_profiles (organization_id);
CREATE INDEX idx_rooms_org         ON rooms (organization_id);
