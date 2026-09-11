package com.videoplatform.quality.dto;

import com.videoplatform.provider.QualityMapper;
import com.videoplatform.quality.ConnectionQualityMetric;

import java.time.Instant;

public record ConnectionMetricResponse(
        Instant recordedAt,
        String qualityLevel,
        Integer rttMs,
        Double packetLossPercent,
        Integer jitterMs,
        Long audioBitrate,
        Long videoBitrate,
        Integer videoWidth,
        Integer videoHeight,
        Integer videoFps,
        String connectionState
) {

    public static ConnectionMetricResponse from(ConnectionQualityMetric m) {
        return new ConnectionMetricResponse(
                m.getRecordedAt(),
                QualityMapper.fromLegacy(m.getQualityLevel()).name(),
                m.getRttMs(),
                m.getPacketLossPercent(),
                m.getJitterMs(),
                m.getAudioBitrate(),
                m.getVideoBitrate(),
                m.getVideoWidth(),
                m.getVideoHeight(),
                m.getVideoFps(),
                m.getConnectionState());
    }
}
