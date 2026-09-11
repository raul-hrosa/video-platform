package com.videoplatform.provider.pulsertc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.CreateTokenRequest;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.RoomView;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.TokenPermissions;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.TokenView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.POST;

class PulseRtcClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private PulseRtcClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        PulseRtcProperties props = new PulseRtcProperties(
                "http://pulse.test", "secret-key", null, null, Duration.ofHours(1), Duration.ofMinutes(5));
        client = new PulseRtcClient(props, builder, MAPPER);
    }

    @Test
    void createRoomSendsBearerAndIdempotencyKey() {
        server.expect(requestTo("http://pulse.test/v1/rooms"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer secret-key"))
                .andExpect(header("Idempotency-Key", "room-1"))
                .andRespond(withSuccess("{\"roomId\":\"room-1\",\"status\":\"OPEN\"}", MediaType.APPLICATION_JSON));

        RoomView room = client.createRoom("room-1");

        assertThat(room.roomId()).isEqualTo("room-1");
        server.verify();
    }

    @Test
    void createTokenParsesResponse() {
        server.expect(requestTo("http://pulse.test/v1/rooms/room-1/tokens"))
                .andExpect(method(POST))
                .andRespond(withSuccess(
                        "{\"token\":\"jwt-abc\",\"identity\":\"user:1\",\"roomId\":\"room-1\"}",
                        MediaType.APPLICATION_JSON));

        TokenView token = client.createToken("room-1",
                new CreateTokenRequest("user:1", "Ana", TokenPermissions.participant(), 3600));

        assertThat(token.token()).isEqualTo("jwt-abc");
        assertThat(token.identity()).isEqualTo("user:1");
    }

    @Test
    void mapsRoomNotFoundBody() {
        server.expect(requestTo("http://pulse.test/v1/rooms/room-x"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"code\":\"ROOM_NOT_FOUND\",\"message\":\"no room\",\"requestId\":\"req-9\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        PulseRtcApiException ex = catchThrowableOfType(
                () -> client.getRoom("room-x"), PulseRtcApiException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.pulseCode()).isEqualTo("ROOM_NOT_FOUND");
        assertThat(ex.httpStatus()).isEqualTo(404);
        assertThat(ex.requestId()).isEqualTo("req-9");
    }

    @Test
    void fallsBackToStatusWhenBodyHasNoCode() {
        server.expect(requestTo("http://pulse.test/v1/rooms/room-x"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("upstream down"));

        PulseRtcApiException ex = catchThrowableOfType(
                () -> client.getRoom("room-x"), PulseRtcApiException.class);

        assertThat(ex.pulseCode()).isEqualTo("NODE_UNAVAILABLE");
    }

    @Test
    void idempotencyConflictIsRecognised() {
        server.expect(requestTo("http://pulse.test/v1/rooms"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .body("{\"code\":\"IDEMPOTENCY_CONFLICT\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        PulseRtcApiException ex = catchThrowableOfType(
                () -> client.createRoom("room-1"), PulseRtcApiException.class);

        assertThat(ex.isIdempotencyConflict()).isTrue();
    }

    @Test
    void deleteRoomIssuesDelete() {
        server.expect(requestTo("http://pulse.test/v1/rooms/room-1"))
                .andExpect(method(DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.deleteRoom("room-1");
        server.verify();
    }
}
