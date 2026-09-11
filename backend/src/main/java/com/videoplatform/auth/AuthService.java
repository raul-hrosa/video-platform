package com.videoplatform.auth;

import com.videoplatform.common.ApiException;
import com.videoplatform.common.logging.LogEvents;
import com.videoplatform.organization.OrganizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Cadastro e autenticacao de usuarios. Nunca loga senha/hash; o login usa uma
 * mensagem generica que nao revela se o email existe.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationService organizationService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       OrganizationService organizationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.organizationService = organizationService;
    }

    @Transactional
    public User register(String name, String rawEmail, String rawPassword) {
        String email = Email.normalize(rawEmail);
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS",
                    "Email already registered.");
        }
        User user = userRepository.save(
                User.register(name.trim(), email, passwordEncoder.encode(rawPassword)));
        // Toda conta nova nasce com uma Organization pessoal (Sprint 7 §7).
        organizationService.provisionPersonalOrg(user);
        log.atInfo()
                .addKeyValue("event", LogEvents.USER_REGISTERED)
                .addKeyValue("userId", user.getId())
                .setMessage("user registered")
                .log();
        return user;
    }

    @Transactional(readOnly = true)
    public User authenticate(String rawEmail, String rawPassword) {
        String email = Email.normalize(rawEmail);
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null || !user.isActive()
                || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            log.atWarn()
                    .addKeyValue("event", LogEvents.LOGIN_FAILED)
                    .addKeyValue("reason", "INVALID_CREDENTIALS")
                    .setMessage("login failed")
                    .log();
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "Email or password is incorrect.");
        }

        log.atInfo()
                .addKeyValue("event", LogEvents.LOGIN_SUCCESS)
                .addKeyValue("userId", user.getId())
                .setMessage("login success")
                .log();
        return user;
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
                        "User not found."));
    }
}
