package com.videoplatform.auth;

import com.videoplatform.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private com.videoplatform.organization.OrganizationService organizationService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, organizationService);
        lenient().when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void registersWithNormalizedEmailAndHashedPassword() {
        when(userRepository.existsByEmail("joao@example.com")).thenReturn(false);

        User user = authService.register("  Joao  ", "  Joao@Example.COM ", "supersecret");

        assertThat(user.getEmail()).isEqualTo("joao@example.com");
        assertThat(user.getName()).isEqualTo("Joao");
        assertThat(user.getPasswordHash()).isNotEqualTo("supersecret");
        assertThat(passwordEncoder.matches("supersecret", user.getPasswordHash())).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void rejectsDuplicateEmail() {
        when(userRepository.existsByEmail("joao@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("Joao", "joao@example.com", "supersecret"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void authenticatesWithCorrectCredentials() {
        User user = User.register("Joao", "joao@example.com", passwordEncoder.encode("supersecret"));
        when(userRepository.findByEmail("joao@example.com")).thenReturn(Optional.of(user));

        assertThat(authService.authenticate(" JOAO@example.com ", "supersecret")).isSameAs(user);
    }

    @Test
    void wrongPasswordGivesGenericInvalidCredentials() {
        User user = User.register("Joao", "joao@example.com", passwordEncoder.encode("supersecret"));
        when(userRepository.findByEmail("joao@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.authenticate("joao@example.com", "wrong"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getErrorCode()).isEqualTo("INVALID_CREDENTIALS");
                    assertThat(api.getStatus().value()).isEqualTo(401);
                });
    }

    @Test
    void unknownEmailGivesTheSameInvalidCredentials() {
        when(userRepository.findByEmail(eq("nobody@example.com"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.authenticate("nobody@example.com", "whatever"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo("INVALID_CREDENTIALS"));
    }
}
