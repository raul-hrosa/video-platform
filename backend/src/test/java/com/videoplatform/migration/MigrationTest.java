package com.videoplatform.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 7 §51: as migrations V1 -> V13 aplicam tanto em base vazia quanto em
 * base ja existente (schema V9 com dados). Nenhum User/Room/Profile existente
 * pode ficar sem Organization e nada pode desaparecer.
 *
 * <p>Pulado automaticamente quando nao ha Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
class MigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private Flyway flyway(String... targetVersion) {
        var cfg = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false);
        if (targetVersion.length > 0) {
            cfg.target(targetVersion[0]);
        }
        return cfg.load();
    }

    private Connection conn() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Test
    void migratesFromEmptyDatabase() throws Exception {
        flyway().clean();
        flyway().migrate();

        try (Connection c = conn(); Statement st = c.createStatement()) {
            assertThat(tableExists(c, "organizations")).isTrue();
            assertThat(tableExists(c, "organization_members")).isTrue();
            assertThat(columnIsNotNull(c, "rooms", "organization_id")).isTrue();
            assertThat(columnIsNotNull(c, "room_profiles", "organization_id")).isTrue();
            // participant_sessions NAO ganha a coluna (Sprint 7 §17).
            assertThat(columnExists(c, "participant_sessions", "organization_id")).isFalse();
            // Sprint 8: agendamento.
            assertThat(tableExists(c, "appointments")).isTrue();
            assertThat(tableExists(c, "appointment_occurrences")).isTrue();
            assertThat(columnExists(c, "rooms", "appointment_occurrence_id")).isTrue();
            st.execute("SELECT 1");
        }
    }

    @Test
    void occurrenceSlotIsUniquePerAppointment() throws Exception {
        flyway().clean();
        flyway().migrate();

        try (Connection c = conn(); Statement st = c.createStatement()) {
            UUID userId = UUID.randomUUID();
            UUID orgId = UUID.randomUUID();
            UUID profileId = UUID.randomUUID();
            UUID apptId = UUID.randomUUID();
            st.execute("""
                INSERT INTO users (id, name, email, password_hash, status, created_at, updated_at)
                VALUES ('%s','Ana','ana@x.com','h','ACTIVE',now(),now())""".formatted(userId));
            st.execute("""
                INSERT INTO organizations (id, name, slug, created_at, updated_at)
                VALUES ('%s','Org','org-%s',now(),now())""".formatted(orgId, orgId.toString().substring(0, 8)));
            st.execute("""
                INSERT INTO room_profiles (id, owner_id, organization_id, name, duration_minutes, type, created_at, updated_at)
                VALUES ('%s','%s','%s','Aula',60,'LESSON',now(),now())""".formatted(profileId, userId, orgId));
            st.execute("""
                INSERT INTO appointments (id, organization_id, room_profile_id, owner_id, title,
                    public_access_id, duration_minutes, timezone, starts_at, recurrence_type, status,
                    created_at, updated_at, version)
                VALUES ('%s','%s','%s','%s','Aula','pub12345abcd',60,'America/Sao_Paulo',
                    now(),'WEEKLY','ACTIVE',now(),now(),0)""".formatted(apptId, orgId, profileId, userId));
            st.execute("""
                INSERT INTO appointment_occurrences (id, appointment_id, scheduled_start, scheduled_end, status, created_at)
                VALUES (gen_random_uuid(),'%s','2026-09-01T18:00:00Z','2026-09-01T19:00:00Z','SCHEDULED',now())"""
                    .formatted(apptId));

            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> st.execute("""
                INSERT INTO appointment_occurrences (id, appointment_id, scheduled_start, scheduled_end, status, created_at)
                VALUES (gen_random_uuid(),'%s','2026-09-01T18:00:00Z','2026-09-01T19:00:00Z','SCHEDULED',now())"""
                    .formatted(apptId)));
        }
    }

    @Test
    void backfillsExistingUsersRoomsAndProfiles() throws Exception {
        flyway().clean();
        flyway("9").migrate();

        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        try (Connection c = conn(); Statement st = c.createStatement()) {
            st.execute("""
                INSERT INTO users (id, name, email, password_hash, status, created_at, updated_at)
                VALUES ('%s', 'Joao Existente', 'joao@old.com', 'hash', 'ACTIVE', now(), now())
                """.formatted(userId));
            st.execute("""
                INSERT INTO room_profiles (id, owner_id, name, duration_minutes, type, created_at, updated_at)
                VALUES ('%s', '%s', 'Aula', 20, 'LESSON', now(), now())
                """.formatted(profileId, userId));
            st.execute("""
                INSERT INTO rooms (id, room_id, owner_id, room_profile_id, name, duration_minutes,
                                   status, created_at, expires_at, version)
                VALUES (gen_random_uuid(), 'room-legacy1', '%s', '%s', 'Aula', 20,
                        'ENDED', now(), now() + interval '20 min', 0)
                """.formatted(userId, profileId));
        }

        flyway().migrate(); // V10 -> V13

        try (Connection c = conn(); Statement st = c.createStatement()) {
            ResultSet m = st.executeQuery(
                    "SELECT role FROM organization_members WHERE user_id = '" + userId + "'");
            assertThat(m.next()).isTrue();
            assertThat(m.getString("role")).isEqualTo("OWNER");

            ResultSet rp = st.executeQuery(
                    "SELECT organization_id FROM room_profiles WHERE id = '" + profileId + "'");
            assertThat(rp.next()).isTrue();
            String orgOfProfile = rp.getString("organization_id");
            assertThat(orgOfProfile).isNotNull();

            ResultSet r = st.executeQuery(
                    "SELECT organization_id FROM rooms WHERE room_id = 'room-legacy1'");
            assertThat(r.next()).isTrue();
            assertThat(r.getString("organization_id")).isEqualTo(orgOfProfile);

            // Nada sumiu.
            ResultSet count = st.executeQuery("SELECT count(*) FROM rooms");
            count.next();
            assertThat(count.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void backfillIsIdempotent() throws Exception {
        flyway().clean();
        flyway().migrate();
        // Reaplicar a V12 manualmente nao deve duplicar memberships.
        try (Connection c = conn(); Statement st = c.createStatement()) {
            UUID userId = UUID.randomUUID();
            st.execute("""
                INSERT INTO users (id, name, email, password_hash, status, created_at, updated_at)
                VALUES ('%s', 'Tardio', 'tardio@x.com', 'h', 'ACTIVE', now(), now())
                """.formatted(userId));
            String v12 = java.nio.file.Files.readString(java.nio.file.Path.of(
                    getClass().getResource("/db/migration/V12__backfill_organizations.sql").toURI()));
            st.execute(v12);
            st.execute(v12); // segunda passada
            ResultSet rs = st.executeQuery(
                    "SELECT count(*) FROM organization_members WHERE user_id = '" + userId + "'");
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    private static boolean tableExists(Connection c, String table) throws Exception {
        try (ResultSet rs = c.getMetaData().getTables(null, null, table, null)) {
            return rs.next();
        }
    }

    private static boolean columnExists(Connection c, String table, String column) throws Exception {
        try (ResultSet rs = c.getMetaData().getColumns(null, null, table, column)) {
            return rs.next();
        }
    }

    private static boolean columnIsNotNull(Connection c, String table, String column) throws Exception {
        try (ResultSet rs = c.getMetaData().getColumns(null, null, table, column)) {
            return rs.next() && "NO".equals(rs.getString("IS_NULLABLE"));
        }
    }
}
