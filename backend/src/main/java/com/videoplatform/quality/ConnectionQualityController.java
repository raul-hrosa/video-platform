package com.videoplatform.quality;

import com.videoplatform.auth.security.AuthenticatedUser;
import com.videoplatform.common.PageResponse;
import com.videoplatform.organization.OrganizationContext;
import com.videoplatform.provider.QualityMapper;
import com.videoplatform.quality.dto.ConnectionMetricRequest;
import com.videoplatform.quality.dto.ConnectionMetricResponse;
import com.videoplatform.quality.dto.RecordMetricResult;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rooms/{roomId}/sessions/{sessionId}/quality")
public class ConnectionQualityController {

    private final ConnectionMetricsService metricsService;

    public ConnectionQualityController(ConnectionMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RecordMetricResult record(OrganizationContext ctx,
                                     @AuthenticationPrincipal AuthenticatedUser user,
                                     @PathVariable String roomId,
                                     @PathVariable UUID sessionId,
                                     @Valid @RequestBody ConnectionMetricRequest request) {
        ConnectionQualityMetric metric = metricsService.record(roomId, sessionId, ctx, user, request);
        return new RecordMetricResult(metric.getId(),
                QualityMapper.fromLegacy(metric.getQualityLevel()).name());
    }

    @GetMapping
    public PageResponse<ConnectionMetricResponse> history(OrganizationContext ctx,
                                                          @PathVariable String roomId,
                                                          @PathVariable UUID sessionId,
                                                          Pageable pageable) {
        return PageResponse.from(
                metricsService.history(roomId, sessionId, ctx, pageable),
                ConnectionMetricResponse::from);
    }
}
