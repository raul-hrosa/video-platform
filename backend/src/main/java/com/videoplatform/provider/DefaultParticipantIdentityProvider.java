package com.videoplatform.provider;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DefaultParticipantIdentityProvider implements ParticipantIdentityProvider {

    @Override
    public String forUser(UUID userId) {
        return PlatformParticipantIdentity.forUser(userId);
    }

    @Override
    public String forGuest() {
        return PlatformParticipantIdentity.forGuest();
    }
}
