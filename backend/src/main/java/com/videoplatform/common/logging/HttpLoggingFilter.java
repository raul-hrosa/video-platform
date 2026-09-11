package com.videoplatform.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Log centralizado de HTTP: HTTP_REQUEST na entrada e HTTP_RESPONSE na saida,
 * com metodo, path, status e duracao. Nao registra body, query, Authorization
 * nem cookies. Erros 5xx sao logados pelo GlobalExceptionHandler (HTTP_ERROR).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class HttpLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String method = request.getMethod();
        String path = request.getRequestURI();
        long start = System.nanoTime();

        log.atInfo()
                .addKeyValue("event", LogEvents.HTTP_REQUEST)
                .addKeyValue("httpMethod", method)
                .addKeyValue("httpPath", path)
                .setMessage("http request")
                .log();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            int status = response.getStatus();
            log.atInfo()
                    .addKeyValue("event", LogEvents.HTTP_RESPONSE)
                    .addKeyValue("httpMethod", method)
                    .addKeyValue("httpPath", path)
                    .addKeyValue("httpStatus", status)
                    .addKeyValue("durationMs", durationMs)
                    .setMessage("http response")
                    .log();
        }
    }
}
