package com.videoplatform.provider.pulsertc;

import com.videoplatform.provider.ConnectionQuality;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PulseRtcQualityMapperTest {

    @Test
    void mapsKnownRatings() {
        assertThat(PulseRtcQualityMapper.fromRating("excellent")).isEqualTo(ConnectionQuality.EXCELLENT);
        assertThat(PulseRtcQualityMapper.fromRating("GOOD")).isEqualTo(ConnectionQuality.GOOD);
        assertThat(PulseRtcQualityMapper.fromRating("warning")).isEqualTo(ConnectionQuality.UNSTABLE);
        assertThat(PulseRtcQualityMapper.fromRating("poor")).isEqualTo(ConnectionQuality.POOR);
    }

    @Test
    void missingOrUnknownRatingIsUnknown() {
        assertThat(PulseRtcQualityMapper.fromRating(null)).isEqualTo(ConnectionQuality.UNKNOWN);
        assertThat(PulseRtcQualityMapper.fromRating("")).isEqualTo(ConnectionQuality.UNKNOWN);
        assertThat(PulseRtcQualityMapper.fromRating("purple")).isEqualTo(ConnectionQuality.UNKNOWN);
    }
}
