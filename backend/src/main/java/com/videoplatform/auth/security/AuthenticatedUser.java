package com.videoplatform.auth.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Identidade do usuario autenticado, extraida do Platform JWT. E' o principal
 * de seguranca — os controllers a recebem via {@code @AuthenticationPrincipal}.
 */
public record AuthenticatedUser(UUID id, String email, String name) {

    public static AuthenticatedUser fromJwt(Jwt jwt) {
        return new AuthenticatedUser(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"));
    }
}
