package com.resort.platform.users;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);

    boolean existsByRole(Role role);

    /** Bloqueia os ADMINs ativos para que duas alterações simultâneas não removam o último (D-048). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<User> findByRoleAndActiveTrue(Role role);
}
