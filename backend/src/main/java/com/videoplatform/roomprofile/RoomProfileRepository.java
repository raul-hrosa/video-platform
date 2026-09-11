package com.videoplatform.roomprofile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomProfileRepository extends JpaRepository<RoomProfile, UUID> {

    /** So perfis ativos (soft delete filtrado). */
    Optional<RoomProfile> findByIdAndDeletedAtIsNull(UUID id);

    /** Perfis ativos da Organization (Sprint 7 §21/§41) — nao so os do criador. */
    List<RoomProfile> findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID organizationId);
}
