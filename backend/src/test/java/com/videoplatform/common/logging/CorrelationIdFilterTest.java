package com.videoplatform.common.logging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CorrelationIdFilterTest {

    private static final String UUID_REGEX =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @RestController
    static class ProbeController {
        @GetMapping("/probe")
        String probe() {
            // devolve o correlationId que esta visivel no MDC durante o handler
            return String.valueOf(MDC.get(CorrelationId.MDC_KEY));
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void usesCorrelationIdSentByClient() throws Exception {
        mockMvc.perform(get("/probe").header(CorrelationId.HEADER, "abc-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationId.HEADER, "abc-123"))
                .andExpect(content().string("abc-123"));
    }

    @Test
    void generatesUuidWhenHeaderAbsent() throws Exception {
        mockMvc.perform(get("/probe").accept(MediaType.ALL))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationId.HEADER, matchesPattern(UUID_REGEX)))
                .andExpect(content().string(matchesPattern(UUID_REGEX)));
    }

    @Test
    void generatesUuidWhenHeaderBlank() throws Exception {
        mockMvc.perform(get("/probe").header(CorrelationId.HEADER, "  "))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationId.HEADER, matchesPattern(UUID_REGEX)));
    }

    @Test
    void clearsMdcAfterRequest() throws Exception {
        mockMvc.perform(get("/probe").header(CorrelationId.HEADER, "abc-123"))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }
}
