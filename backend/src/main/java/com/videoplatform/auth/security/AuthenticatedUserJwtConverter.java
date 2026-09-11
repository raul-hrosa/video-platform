package com.videoplatform.auth.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Converte o Jwt validado num token de autenticacao cujo principal e' um
 * {@link AuthenticatedUser} — permite {@code @AuthenticationPrincipal AuthenticatedUser}.
 */
public class AuthenticatedUserJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.fromJwt(jwt);
        PreAuthenticatedAuthenticationToken auth =
                new PreAuthenticatedAuthenticationToken(user, jwt, List.of());
        auth.setAuthenticated(true);
        return auth;
    }
}
