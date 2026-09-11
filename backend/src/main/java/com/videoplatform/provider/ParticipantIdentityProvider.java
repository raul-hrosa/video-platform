package com.videoplatform.provider;

import java.util.UUID;

/** Gera identidades da plataforma para participantes dentro de uma Room. */
public interface ParticipantIdentityProvider {

    String forUser(UUID userId);

    String forGuest();
}
