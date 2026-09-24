package com.resort.platform.users.dto;

public final class PhoneFormat {

    public static final String REGEX = "^$|^[0-9+()\\s-]{8,20}$";
    public static final String MESSAGE = "Telefone inválido.";

    private PhoneFormat() {}
}
