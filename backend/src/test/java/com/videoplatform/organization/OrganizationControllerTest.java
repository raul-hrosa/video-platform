package com.videoplatform.organization;

import com.videoplatform.common.ApiException;
import com.videoplatform.common.GlobalExceptionHandler;
import com.videoplatform.organization.dto.MemberResponse;
import com.videoplatform.organization.dto.OrganizationResponse;
import com.videoplatform.support.TestAuth;
import com.videoplatform.support.WebSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrganizationController.class)
@Import({GlobalExceptionHandler.class, WebSecurityTestConfig.class})
class OrganizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrganizationService organizationService;

    @Test
    void currentReturns401WithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/current"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void currentReturnsOrganizationWithRole() throws Exception {
        when(organizationService.getCurrent(any(OrganizationContext.class)))
                .thenReturn(new OrganizationResponse(TestAuth.ORG_ID, "Clinica ABC", "clinica-abc",
                        OrgRole.OWNER));

        mockMvc.perform(get("/api/v1/organizations/current").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Clinica ABC"))
                .andExpect(jsonPath("$.slug").value("clinica-abc"))
                .andExpect(jsonPath("$.role").value("OWNER"));
    }

    @Test
    void updateReturnsNewName() throws Exception {
        when(organizationService.updateName(any(OrganizationContext.class), eq("Novo Nome")))
                .thenReturn(new OrganizationResponse(TestAuth.ORG_ID, "Novo Nome", "clinica-abc",
                        OrgRole.OWNER));

        mockMvc.perform(put("/api/v1/organizations/current").with(TestAuth.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Novo Nome\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Novo Nome"));
    }

    @Test
    void updateWithBlankNameReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/organizations/current").with(TestAuth.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void updateForbiddenForNonOwner() throws Exception {
        when(organizationService.updateName(any(OrganizationContext.class), any()))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "INSUFFICIENT_ROLE", "no"));

        mockMvc.perform(put("/api/v1/organizations/current").with(TestAuth.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));
    }

    @Test
    void membersReturnsContentEnvelope() throws Exception {
        when(organizationService.listMembers(any(OrganizationContext.class))).thenReturn(List.of(
                new MemberResponse(UUID.randomUUID(), "Joao", "joao@x.com", OrgRole.OWNER, Instant.now()),
                new MemberResponse(UUID.randomUUID(), "Maria", "maria@x.com", OrgRole.MEMBER, Instant.now())));

        mockMvc.perform(get("/api/v1/organizations/current/members").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Joao"))
                .andExpect(jsonPath("$.content[0].role").value("OWNER"))
                .andExpect(jsonPath("$.content[1].role").value("MEMBER"));
    }
}
