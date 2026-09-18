package com.videoplatform.participant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipantSessionRepository extends JpaRepository<ParticipantSession, UUID> {

    List<ParticipantSession> findByRoomIdOrderByJoinedAtAsc(String roomId);

    /** Quantas sessoes estao abertas (conectado agora) por sala, em lote — sem N+1 na listagem. */
    @Query("""
            SELECT ps.roomId, COUNT(ps)
            FROM ParticipantSession ps
            WHERE ps.roomId IN :roomIds AND ps.leftAt IS NULL
            GROUP BY ps.roomId
            """)
    List<Object[]> countOpenGroupedByRoomIds(Collection<String> roomIds);

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
