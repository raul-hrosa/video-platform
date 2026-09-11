package com.videoplatform.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID>, JpaSpecificationExecutor<Room> {

    Optional<Room> findByRoomId(String roomId);

    boolean existsByRoomId(String roomId);

    // ---- dashboard da Organization (Sprint 7 §22) ----

    long countByOrganizationIdAndCreatedAtGreaterThanEqual(UUID organizationId, Instant createdFrom);

    long countByOrganizationId(UUID organizationId);

    long countByRoomProfileId(UUID roomProfileId);

    /** Soma da duracao das salas da Organization que ja iniciaram e encerraram. */
    @Query("""
            SELECT COALESCE(SUM(timestampdiff(SECOND, r.startedAt, r.endedAt)), 0)
            FROM Room r
            WHERE r.organizationId = :organizationId
              AND r.startedAt IS NOT NULL AND r.endedAt IS NOT NULL
            """)
    long sumCallSecondsByOrganization(UUID organizationId);

    @Query("""
            SELECT COALESCE(SUM(timestampdiff(SECOND, r.startedAt, r.endedAt)), 0)
            FROM Room r
            WHERE r.roomProfileId = :roomProfileId
              AND r.startedAt IS NOT NULL AND r.endedAt IS NOT NULL
            """)
    long sumCallSecondsByProfile(UUID roomProfileId);

    /** Varredura do scheduler de expiracao: salas ativas com {@code expires_at} vencido. */
    List<Room> findByStatusInAndExpiresAtBefore(Collection<RoomStatus> statuses, Instant cutoff);
}
