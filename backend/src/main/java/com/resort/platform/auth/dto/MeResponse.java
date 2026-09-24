package com.resort.platform.auth.dto;

import com.resort.platform.users.Role;
import com.resort.platform.users.User;
import java.util.UUID;

public record MeResponse(UUID id, String name, String email, Role role, boolean mustChangePassword) {

    public static MeResponse of(User user) {
        return new MeResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isMustChangePassword());
    }
}
