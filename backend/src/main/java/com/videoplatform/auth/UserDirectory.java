package com.videoplatform.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resolucao em lote de nomes de usuario para exibicao (Sprint 7 §42 — "Criada
 * por João" no historico). Evita N+1 (§54).
 */
@Service
public class UserDirectory {

    private final UserRepository userRepository;

    public UserDirectory(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> namesByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getName));
    }
}
