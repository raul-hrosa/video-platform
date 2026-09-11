package com.videoplatform.analytics;

import com.videoplatform.analytics.dto.AnalyticsResponse;
import com.videoplatform.organization.OrganizationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rooms")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/{roomId}/analytics")
    public AnalyticsResponse analytics(OrganizationContext ctx, @PathVariable String roomId) {
        return analyticsService.forRoom(roomId, ctx);
    }
}
