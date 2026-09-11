package com.videoplatform.provider;

import java.util.UUID;

/**
 * Formato canonico de identidade de participante da plataforma.
 * O formato atual e preservado para compatibilidade com sessoes existentes.
 */
public final class PlatformParticipantIdentity {

    private static final String USER_PREFIX = "user:";
    private static final String GUEST_PREFIX = "guest:";

    private PlatformParticipantIdentity() {
    }

    public static String forUser(UUID userId) {
        return USER_PREFIX + userId;
    }

    public static String forGuest() {
        return GUEST_PREFIX + UUID.randomUUID();
    }

    public static boolean isGuest(String participantRef) {
        return participantRef != null && participantRef.startsWith(GUEST_PREFIX);
    }

    public static UUID parseUserId(String participantRef) {
        if (participantRef == null || !participantRef.startsWith(USER_PREFIX)) {
            return null;
        }
        try {
            return UUID.fromString(participantRef.substring(USER_PREFIX.length()));
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }
}
