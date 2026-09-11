package com.videoplatform.participant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipantSessionRepository extends JpaRepository<ParticipantSession, UUID> {

    List<ParticipantSession> findByRoomIdOrderByJoinedAtAsc(String roomId);

    /** Participações distintas (por identidade) em todas as salas da Organization (Sprint 7 §22). */
    @Query("""
            SELECT COUNT(DISTINCT ps.participantId)
            FROM ParticipantSession ps, Room r
            WHERE r.roomId = ps.roomId AND r.organizationId = :organizationId
            """)
    long countDistinctParticipantsByOrganization(UUID organizationId);

    /** Participações (sessões) em todas as salas geradas por um Profile (Sprint 6 §39). */
    @Query("""
            SELECT COUNT(ps)
            FROM ParticipantSession ps, Room r
            WHERE r.roomId = ps.roomId AND r.roomProfileId = :roomProfileId
            """)
    long countSessionsByProfile(UUID roomProfileId);

    Optional<ParticipantSession> findFirstByRoomIdAndParticipantIdAndLeftAtIsNullOrderByJoinedAtAsc(
            String roomId, String participantId);

    Optional<ParticipantSession> findFirstByRoomIdAndUserIdAndLeftAtIsNullOrderByJoinedAtAsc(
            String roomId, UUID userId);
}
