package com.resort.platform.prospectors.dto;

import com.resort.platform.prospectors.Prospector;
import java.util.UUID;

/** Nome, e-mail e situação vêm de {@code users} (D-014). */
public record ProspectorResponse(
        UUID id, UUID userId, String name, String email, boolean active, String employeeCode, String phone) {

    public static ProspectorResponse of(Prospector prospector) {
        return new ProspectorResponse(
                prospector.getId(),
                prospector.getUser().getId(),
                prospector.getUser().getName(),
                prospector.getUser().getEmail(),
                prospector.getUser().isActive(),
                prospector.getEmployeeCode(),
                prospector.getPhone());
    }
}
