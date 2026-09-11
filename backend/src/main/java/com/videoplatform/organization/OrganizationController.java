package com.videoplatform.organization;

import com.videoplatform.organization.dto.MemberListResponse;
import com.videoplatform.organization.dto.OrganizationResponse;
import com.videoplatform.organization.dto.UpdateOrganizationRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API da Organization do usuario autenticado (Sprint 7 §28–§30). O tenant vem
 * sempre do {@link OrganizationContext} — nunca do corpo/params da requisicao.
 */
@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping("/current")
    public OrganizationResponse current(OrganizationContext ctx) {
        return organizationService.getCurrent(ctx);
    }

    @PutMapping("/current")
    public OrganizationResponse update(OrganizationContext ctx,
                                       @Valid @RequestBody UpdateOrganizationRequest request) {
        return organizationService.updateName(ctx, request.name());
    }

    @GetMapping("/current/members")
    public MemberListResponse members(OrganizationContext ctx) {
        return new MemberListResponse(organizationService.listMembers(ctx));
    }
}
