package com.videoplatform.webhook;

import com.videoplatform.common.ApiException;
import com.videoplatform.common.GlobalExceptionHandler;
import com.videoplatform.support.WebSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookController.class)
@Import({GlobalExceptionHandler.class, WebSecurityTestConfig.class})
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebhookService webhookService;

    @Test
    void acceptsValidWebhookAndReturns200() throws Exception {
        when(webhookService.process(any(), eq("Bearer abc")))
                .thenReturn(new WebhookService.WebhookResult(true, null));

        mockMvc.perform(post("/api/v1/webhooks/livekit")
                        .header("Authorization", "Bearer abc")
                        .contentType("application/webhook+json")
                        .content("{\"event\":\"room_started\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(true));
    }

    @Test
    void duplicateWebhookReturns200NotProcessed() throws Exception {
        when(webhookService.process(any(), any()))
                .thenReturn(new WebhookService.WebhookResult(false, "ALREADY_PROCESSED"));

        mockMvc.perform(post("/api/v1/webhooks/livekit")
                        .header("Authorization", "Bearer abc")
                        .contentType("application/webhook+json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(false))
                .andExpect(jsonPath("$.reason").value("ALREADY_PROCESSED"));
    }

    @Test
    void invalidSignatureReturns401() throws Exception {
        when(webhookService.process(any(), any()))
                .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "WEBHOOK_INVALID",
                        "Webhook signature is invalid."));

        mockMvc.perform(post("/api/v1/webhooks/livekit")
                        .header("Authorization", "bad")
                        .contentType("application/webhook+json")
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_INVALID"));
    }
}
