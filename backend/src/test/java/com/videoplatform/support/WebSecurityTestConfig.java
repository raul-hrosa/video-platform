package com.videoplatform.support;

import com.videoplatform.auth.security.AuthenticatedUser;
import com.videoplatform.auth.security.RestAccessDeniedHandler;
import com.videoplatform.auth.security.RestAuthenticationEntryPoint;
import com.videoplatform.auth.security.SecurityConfig;
import com.videoplatform.organization.OrgRole;
import com.videoplatform.organization.OrganizationContext;
import com.videoplatform.organization.OrganizationContextResolver;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.UUID;

/**
 * Habilita o {@code SecurityConfig} real em fatias {@code @WebMvcTest} e fornece
 * um {@link OrganizationContextResolver} que resolve o contexto sem tocar no
 * banco: id do principal injetado por {@link TestAuth}, role {@link OrgRole#OWNER}
 * (as regras por role sao cobertas nos testes de service). O {@link JwtDecoder}
 * e' um stub.
 */
@TestConfiguration
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
public class WebSecurityTestConfig {

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            throw new BadJwtException("stub decoder (tests use authentication() post-processor)");
        };
    }

    @Bean
    OrganizationContextResolver organizationContextResolver() {
        return new OrganizationContextResolver(null) {
            public OrganizationContext resolve() { // sobrescreve o resolve() real
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                UUID userId = (auth != null && auth.getPrincipal() instanceof AuthenticatedUser u)
                        ? u.id() : TestAuth.USER_ID;
                return new OrganizationContext(TestAuth.ORG_ID, userId, OrgRole.OWNER);
            }
        };
    }
}
