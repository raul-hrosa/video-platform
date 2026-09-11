package com.videoplatform.organization;

import com.videoplatform.auth.security.AuthenticatedUser;
import com.videoplatform.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolve o {@link OrganizationContext} do usuario autenticado (Sprint 7 §26,
 * §33) — o membership e' consultado no backend, nunca vem do frontend. Usado
 * pelo argument resolver registrado em {@code WebConfig}.
 *
 * <p>Nao implementa {@code HandlerMethodArgumentResolver} de proposito: assim o
 * {@code @WebMvcTest} nao o carrega automaticamente (os testes de controller
 * usam um stub).
 */
@Component
public class OrganizationContextResolver {

    private final OrganizationService organizationService;

    public OrganizationContextResolver(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    public OrganizationContext resolve() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Autenticacao necessaria.");
        }
        return organizationService.contextFor(user.id());
    }
}
