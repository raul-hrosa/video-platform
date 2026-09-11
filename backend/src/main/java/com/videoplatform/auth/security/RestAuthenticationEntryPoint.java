package com.videoplatform.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videoplatform.common.ErrorResponse;
import com.videoplatform.common.logging.LogEvents;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Falha de autenticacao no nivel do filtro (sem token / token invalido /
 * expirado). Responde 401 no formato {code,message}, sem stack trace, e loga
 * AUTHENTICATION_FAILED sem vazar o header Authorization.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String message = authException.getMessage() == null ? "" : authException.getMessage();
        String hasAuthHeader = request.getHeader("Authorization") == null ? "false" : "true";

        String code;
        String friendly;
        if (message.toLowerCase().contains("expired")) {
            code = "TOKEN_EXPIRED";
            friendly = "Sua sessao expirou. Faca login novamente.";
        } else if ("true".equals(hasAuthHeader)) {
            code = "INVALID_TOKEN";
            friendly = "Token de autenticacao invalido.";
        } else {
            code = "UNAUTHORIZED";
            friendly = "Autenticacao necessaria.";
        }

        log.atWarn()
                .addKeyValue("event", LogEvents.AUTHENTICATION_FAILED)
                .addKeyValue("reason", code)
                .addKeyValue("httpPath", request.getRequestURI())
                .setMessage("authentication failed")
                .log();

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(code, friendly));
    }
}
