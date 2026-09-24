package com.resort.platform.visits;

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

public interface VisitRepository extends JpaRepository<Visit, UUID>, JpaSpecificationExecutor<Visit> {

    @Override
    @EntityGraph(attributePaths = {"lead", "lead.prospector", "prospector", "prospector.user"})
    Page<Visit> findAll(Specification<Visit> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"lead", "lead.prospector", "prospector", "prospector.user"})
    Optional<Visit> findWithDetailsById(UUID id);

    Optional<Visit> findByLeadIdAndStatus(UUID leadId, VisitStatus status);

    @Query("select v.lead.id from Visit v where v.id = :id")
    Optional<UUID> findLeadIdById(@Param("id") UUID id);

    boolean existsByLeadIdAndStatus(UUID leadId, VisitStatus status);
}
