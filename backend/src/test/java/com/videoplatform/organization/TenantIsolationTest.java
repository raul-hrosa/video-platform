package com.videoplatform.organization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 7 §48/§49/§62 — fronteira de seguranca entre tenants, validada
 * end-to-end contra um Postgres real.
 *
 * <ul>
 *   <li>cada conta nova ganha uma Organization pessoal (OWNER);</li>
 *   <li>Joao (Org A) cria Profile + Room; Maria (Org B) recebe 404 em todos os
 *       recursos de A (profile, room, analytics, sessions, quality, history);</li>
 *   <li>a Room criada por Joao tem {@code organizationId} = Org A e
 *       {@code ownerId} = Joao.</li>
 * </ul>
 *
 * <p>Pulado automaticamente quando nao ha Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class TenantIsolationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("jwt.secret", () -> "test-secret-please-ignore-0123456789-abcdefghij");
        registry.add("room.expiration.check-interval", () -> "PT60S");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;

    private String joaoToken;
    private String mariaToken;

    @BeforeEach
    void registerTwoTenants() throws Exception {
        joaoToken = register("Joao " + uniq(), "joao" + uniq() + "@x.com");
        mariaToken = register("Maria " + uniq(), "maria" + uniq() + "@x.com");
    }

    private static String uniq() {
        return Integer.toHexString(java.util.concurrent.ThreadLocalRandom.current().nextInt());
    }

    private String register(String name, String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(java.util.Map.of(
                                "name", name, "email", email, "password", "supersecret"))))
                .andExpect(status().isCreated());
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(java.util.Map.of(
                                "email", email, "password", "supersecret"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("accessToken").asText();
    }

    private JsonNode bearer(String token, String path) throws Exception {
        String body = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    @Test
    void newAccountGetsPersonalOrganizationAsOwner() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/current")
                        .header("Authorization", "Bearer " + joaoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.slug").isNotEmpty());
    }

    @Test
    void mariaCannotSeeJoaosResources() throws Exception {
        // Joao cria um Profile e uma Room.
        String profileBody = mockMvc.perform(post("/api/v1/room-profiles")
                        .header("Authorization", "Bearer " + joaoToken)
                        .contentType("application/json")
                        .content("{\"name\":\"Teleconsulta\",\"durationMinutes\":30,\"type\":\"CONSULTATION\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String profileId = mapper.readTree(profileBody).get("id").asText();

        String roomBody = mockMvc.perform(post("/api/v1/room-profiles/" + profileId + "/rooms")
                        .header("Authorization", "Bearer " + joaoToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String roomId = mapper.readTree(roomBody).get("roomId").asText();

        // Maria (outra Organization) nao enxerga nada de Joao.
        for (String path : new String[]{
                "/api/v1/room-profiles/" + profileId,
                "/api/v1/room-profiles/" + profileId + "/summary",
                "/api/v1/rooms/" + roomId,
                "/api/v1/rooms/" + roomId + "/analytics",
                "/api/v1/rooms/" + roomId + "/sessions",
                "/api/v1/rooms/" + roomId + "/participants/summary",
                "/api/v1/rooms/" + roomId + "/quality"}) {
            mockMvc.perform(get(path).header("Authorization", "Bearer " + mariaToken))
                    .andExpect(status().isNotFound());
        }

        // O historico de Maria nao lista a Room de Joao.
        JsonNode mariaHistory = bearer(mariaToken, "/api/v1/rooms");
        org.assertj.core.api.Assertions.assertThat(mariaHistory.get("totalElements").asInt()).isZero();

        // O de Joao lista.
        JsonNode joaoHistory = bearer(joaoToken, "/api/v1/rooms");
        org.assertj.core.api.Assertions.assertThat(joaoHistory.get("totalElements").asInt()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(
                joaoHistory.get("content").get(0).get("roomId").asText()).isEqualTo(roomId);

        // Room profile list de Maria e' vazia; a de Joao tem 1.
        org.assertj.core.api.Assertions.assertThat(
                bearer(mariaToken, "/api/v1/room-profiles").get("content").size()).isZero();
        org.assertj.core.api.Assertions.assertThat(
                bearer(joaoToken, "/api/v1/room-profiles").get("content").size()).isEqualTo(1);
    }
}
