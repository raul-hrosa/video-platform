package com.videoplatform.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config do Platform JWT. O secret existe somente no backend (env var).
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long expirationSeconds,
        String issuer
) {

    public JwtProperties {
        if (expirationSeconds <= 0) {
            expirationSeconds = 3600;
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "video-platform";
        }
    }

    public boolean isConfigured() {
        return secret != null && secret.getBytes().length >= 32;
    }
}
