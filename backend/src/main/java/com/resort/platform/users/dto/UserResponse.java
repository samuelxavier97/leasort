package com.resort.platform.users.dto;

import com.resort.platform.prospectors.Prospector;
import com.resort.platform.users.Role;
import com.resort.platform.users.User;
import java.time.Instant;
import java.util.UUID;

/** Dados do usuário para o ADMIN. Nunca inclui o hash da senha. */
public record UserResponse(
        UUID id,
        String name,
        String email,
        Role role,
        boolean active,
        boolean mustChangePassword,
        Instant createdAt,
        UUID prospectorId,
        String employeeCode,
        String phone) {

    public static UserResponse of(User user, Prospector prospector) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                user.isMustChangePassword(),
                user.getCreatedAt(),
                prospector == null ? null : prospector.getId(),
                prospector == null ? null : prospector.getEmployeeCode(),
                prospector == null ? null : prospector.getPhone());
    }
}
