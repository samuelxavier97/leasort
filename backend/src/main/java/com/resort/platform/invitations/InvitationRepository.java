package com.resort.platform.invitations;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvitationRepository extends JpaRepository<Invitation, UUID>, JpaSpecificationExecutor<Invitation> {

    @Override
    @EntityGraph(attributePaths = {"visit", "visit.lead", "visit.lead.prospector", "visit.prospector", "visit.prospector.user"})
    Page<Invitation> findAll(Specification<Invitation> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"visit", "visit.lead", "visit.lead.prospector", "visit.prospector", "visit.prospector.user"})
    Optional<Invitation> findWithDetailsById(UUID id);

    Optional<Invitation> findByVisitIdAndStatus(UUID visitId, InvitationStatus status);

    List<Invitation> findByVisitIdIn(Collection<UUID> visitIds);

    boolean existsByCode(String code);

    @Query("select i.visit.lead.id from Invitation i where i.id = :id")
    Optional<UUID> findLeadIdById(@Param("id") UUID id);
}
