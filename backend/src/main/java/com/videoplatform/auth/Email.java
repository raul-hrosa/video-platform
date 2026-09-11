package com.videoplatform.auth;

import java.util.Locale;

/**
 * Normalizacao de email — mesma regra em cadastro, login e consulta.
 */
public final class Email {

    private Email() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
