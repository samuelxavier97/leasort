package com.resort.platform;

import com.resort.platform.invitations.InvitationCodeGenerator;
import java.security.SecureRandom;

/** Códigos de convite gerados na hora para os testes; nenhum código fixo no repositório. */
public final class TestCodes {

    private static final InvitationCodeGenerator GENERATOR = new InvitationCodeGenerator(new SecureRandom());

    private TestCodes() {}

    public static String unique() {
        return GENERATOR.next();
    }
}
