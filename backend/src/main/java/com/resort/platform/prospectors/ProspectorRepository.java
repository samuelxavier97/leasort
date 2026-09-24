package com.resort.platform.prospectors;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProspectorRepository extends JpaRepository<Prospector, UUID> {

    @Override
    @EntityGraph(attributePaths = "user")
    Page<Prospector> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Optional<Prospector> findWithUserById(UUID id);

    Optional<Prospector> findByUserId(UUID userId);

    List<Prospector> findByUserIdIn(Collection<UUID> userIds);

    boolean existsByEmployeeCode(String employeeCode);

    boolean existsByEmployeeCodeAndIdNot(String employeeCode, UUID id);
}
