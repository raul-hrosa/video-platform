package com.videoplatform.participant;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Guarda o nome de exibição informado no momento em que o token de mídia é
 * emitido, indexado por {@code (roomId, identity)} (Sprint 11).
 *
 * <p>Alguns providers de mídia não propagam o nome dos outros participantes por
 * nenhum canal (signaling ou REST) — só devolvem a identidade
 * {@code <sub>.<8hex>}. O cliente na chamada resolve o nome consultando
 * {@code GET /api/v1/rooms/{id}/participant-names} e casando o prefixo
 * {@code <sub>} da identidade.
 *
 * <p>Armazenamento em memória, best-effort: um mapa por sala, com teto de salas
 * (LRU por inserção). Some num restart — aceitável para um dado puramente
 * cosmético e de curta duração.
 */
@Component
public class ParticipantNameRegistry {

    private static final int MAX_ROOMS = 1_000;
    private static final int MAX_NAMES_PER_ROOM = 100;

    private final Map<String, Map<String, String>> byRoom = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Map<String, String>> eldest) {
                    return size() > MAX_ROOMS;
                }
            });

    public void record(String roomId, String identity, String displayName) {
        if (roomId == null || identity == null || displayName == null || displayName.isBlank()) {
            return;
        }
        Map<String, String> names = byRoom.computeIfAbsent(roomId, k -> Collections.synchronizedMap(
                new LinkedHashMap<>(16, 0.75f, false) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                        return size() > MAX_NAMES_PER_ROOM;
                    }
                }));
        names.put(identity, displayName.trim());
    }

    public Map<String, String> namesForRoom(String roomId) {
        Map<String, String> names = byRoom.get(roomId);
        if (names == null) {
            return Map.of();
        }
        synchronized (names) {
            return Map.copyOf(names);
        }
    }
}
