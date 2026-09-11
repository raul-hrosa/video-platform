package com.videoplatform.organization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {

    /** O membership do usuario. Nesta sprint cada usuario tem no maximo um. */
    Optional<OrganizationMember> findFirstByUserIdOrderByCreatedAtAsc(UUID userId);

    List<OrganizationMember> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);

    Optional<OrganizationMember> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    boolean existsByUserId(UUID userId);
}
