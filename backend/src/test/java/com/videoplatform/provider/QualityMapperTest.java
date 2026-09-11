package com.videoplatform.provider;

import com.videoplatform.quality.QualityLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QualityMapperTest {

    @Test
    void mapsLegacyLevelsToPlatformQuality() {
        assertThat(QualityMapper.fromLegacy(QualityLevel.EXCELLENT)).isEqualTo(ConnectionQuality.EXCELLENT);
        assertThat(QualityMapper.fromLegacy(QualityLevel.GOOD)).isEqualTo(ConnectionQuality.GOOD);
        assertThat(QualityMapper.fromLegacy(QualityLevel.FAIR)).isEqualTo(ConnectionQuality.UNSTABLE);
        assertThat(QualityMapper.fromLegacy(QualityLevel.POOR)).isEqualTo(ConnectionQuality.POOR);
        assertThat(QualityMapper.fromLegacy(QualityLevel.VERY_POOR)).isEqualTo(ConnectionQuality.POOR);
        assertThat(QualityMapper.fromLegacy(null)).isEqualTo(ConnectionQuality.UNKNOWN);
    }
}
