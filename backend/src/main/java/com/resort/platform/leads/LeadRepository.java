package com.resort.platform.leads;

import jakarta.persistence.LockModeType;
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

public interface LeadRepository extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {

    @Override
    @EntityGraph(attributePaths = {"prospector", "prospector.user"})
    Page<Lead> findAll(Specification<Lead> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"prospector", "prospector.user"})
    Optional<Lead> findWithProspectorById(UUID id);

    /** Agendamento, remarcação, cancelamento e mudança de status serializados por Lead (D-073). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lead l where l.id = :id")
    Optional<Lead> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByCpf(String cpf);

    boolean existsByCpfAndIdNot(String cpf, UUID id);

    @Query("select l.cpf from Lead l where l.cpf in :cpfs")
    List<String> findExistingCpfs(@Param("cpfs") Collection<String> cpfs);

    /** Atribuição em lote: bloqueia os Leads até o fim da transação (D-064). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lead l where l.id in :ids")
    List<Lead> findAllForUpdate(@Param("ids") Collection<UUID> ids);
}
