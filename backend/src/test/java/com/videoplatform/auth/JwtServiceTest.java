package com.videoplatform.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.videoplatform.auth.security.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long!!";
    private static final JwtProperties PROPS = new JwtProperties(SECRET, 3600, "video-platform");

    private JwtService jwtService() {
        return new JwtService(new NimbusJwtEncoder(new ImmutableSecret<>(key())), PROPS);
    }

    private static SecretKeySpec key() {
        return new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private JwtDecoder decoder() {
        return NimbusJwtDecoder.withSecretKey(key()).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Test
    void issuesAVerifiableTokenWithUserClaims() {
        User user = User.register("Joao", "joao@example.com", "hash");
        // id is normally DB-generated; force one for the test
        setId(user);

        JwtService.IssuedToken issued = jwtService().issue(user);
        assertThat(issued.expiresInSeconds()).isEqualTo(3600);

        Jwt decoded = decoder().decode(issued.token());
        assertThat(decoded.getSubject()).isEqualTo(user.getId().toString());
        assertThat(decoded.getClaimAsString("email")).isEqualTo("joao@example.com");
        assertThat(decoded.getClaimAsString("name")).isEqualTo("Joao");
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("video-platform");
        assertThat(decoded.getExpiresAt()).isAfter(java.time.Instant.now());
    }

    private static void setId(User user) {
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(user, java.util.UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
