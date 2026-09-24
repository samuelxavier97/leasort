package com.resort.platform.invitations;

import jakarta.persistence.LockModeType;
import java.time.Instant;
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
import org.springframework.data.jpa.repository.Lock;
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

    @EntityGraph(attributePaths = {"visit", "visit.lead", "visit.prospector", "visit.prospector.user"})
    Optional<Invitation> findWithDetailsByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invitation i where i.id = :id")
    Optional<Invitation> findByIdForUpdate(@Param("id") UUID id);

    @Query("select i.id from Invitation i where i.status = com.resort.platform.invitations.InvitationStatus.ACTIVE"
            + " and i.expiresAt <= :now order by i.expiresAt, i.id")
    List<UUID> findActiveIdsExpiredAt(@Param("now") Instant now);

    @Query("select i.visit.lead.id from Invitation i where i.id = :id")
    Optional<UUID> findLeadIdById(@Param("id") UUID id);
}
