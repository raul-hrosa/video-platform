package com.videoplatform.provider.livekit;

import com.videoplatform.livekit.LiveKitProperties;
import com.videoplatform.provider.MediaConnectionInfoProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** URL publica do LiveKit entregue ao browser (Sprint 11 §6). */
@Component
@ConditionalOnProperty(prefix = "media", name = "provider", havingValue = "livekit", matchIfMissing = true)
public class LiveKitConnectionInfoProvider implements MediaConnectionInfoProvider {

    private final LiveKitProperties properties;

    public LiveKitConnectionInfoProvider(LiveKitProperties properties) {
        this.properties = properties;
    }

    @Override
    public String serverUrl() {
        return properties.url() == null ? "" : properties.url();
    }
}
