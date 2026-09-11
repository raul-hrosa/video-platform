package com.videoplatform.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videoplatform.common.ErrorResponse;
import com.videoplatform.common.logging.LogEvents;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Usuario autenticado mas sem permissao (403). Formato {code,message}, loga
 * ACCESS_DENIED com o userId.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String userId = principal instanceof AuthenticatedUser u ? String.valueOf(u.id()) : null;

        log.atWarn()
                .addKeyValue("event", LogEvents.ACCESS_DENIED)
                .addKeyValue("userId", userId)
                .addKeyValue("httpPath", request.getRequestURI())
                .setMessage("access denied")
                .log();

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                new ErrorResponse("FORBIDDEN", "Voce nao tem permissao para acessar este recurso."));
    }
}
