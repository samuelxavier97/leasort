package com.resort.platform.users.dto;

/** Resposta da criação: a senha temporária aparece só aqui, uma única vez (D-046). */
public record CreatedUserResponse(UserResponse user, String temporaryPassword) {}
