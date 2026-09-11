package com.videoplatform.support;

import com.videoplatform.auth.security.AuthenticatedUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * Injeta um usuario autenticado (principal = {@link AuthenticatedUser}) nas
 * requisicoes de teste — o mesmo tipo de principal que o
 * {@code AuthenticatedUserJwtConverter} produz em producao.
 */
public final class TestAuth {

    public static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID ORG_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private TestAuth() {
    }

    /** Contexto da Organization padrao dos testes (usuario = {@link #USER_ID}, role OWNER). */
    public static com.videoplatform.organization.OrganizationContext orgContext() {
        return orgContext(com.videoplatform.organization.OrgRole.OWNER);
    }

    public static com.videoplatform.organization.OrganizationContext orgContext(
            com.videoplatform.organization.OrgRole role) {
        return new com.videoplatform.organization.OrganizationContext(ORG_ID, USER_ID, role);
    }

    public static RequestPostProcessor user() {
        return user(USER_ID, "joao@example.com", "Joao");
    }

    public static RequestPostProcessor user(UUID id) {
        return user(id, "user-" + id + "@example.com", "User");
    }

    public static RequestPostProcessor user(UUID id, String email, String name) {
        AuthenticatedUser principal = new AuthenticatedUser(id, email, name);
        var token = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        return authentication(token);
    }
}
