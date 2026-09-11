package com.videoplatform.quality;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.videoplatform.auth.security.AuthenticatedUser;
import com.videoplatform.common.ApiException;
import com.videoplatform.participant.ParticipantSession;
import com.videoplatform.participant.ParticipantSessionService;
import com.videoplatform.quality.dto.ConnectionMetricRequest;
import com.videoplatform.room.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectionMetricsServiceTest {

    private static final UUID USER = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final AuthenticatedUser AUTH = new AuthenticatedUser(USER, "u@x.com", "U");
    private static final com.videoplatform.organization.OrganizationContext CTX =
            new com.videoplatform.organization.OrganizationContext(
                    UUID.fromString("a0000000-0000-0000-0000-0000000000aa"), USER,
                    com.videoplatform.organization.OrgRole.OWNER);

    @Mock
    private RoomService roomService;
    @Mock
    private ParticipantSessionService participantSessionService;
    @Mock
    private ConnectionQualityMetricRepository repository;

    private ConnectionMetricsService service;

    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ConnectionMetricsService(
                roomService, participantSessionService, new ConnectionQualityService(), repository);
        lenient().when(repository.save(any(ConnectionQualityMetric.class)))
                .thenAnswer(i -> i.getArgument(0));
        lenient().when(repository.findBySessionIdOrderByRecordedAtAsc(sessionId)).thenReturn(List.of());
    }

    private ParticipantSession session(String roomId, UUID userId) {
        return ParticipantSession.start(roomId, "user:" + userId, userId, "U", Instant.now());
    }

    private ConnectionMetricRequest req() {
        return new ConnectionMetricRequest(null, 90, 1.2, 15,
                48000L, 1_800_000L, 1280, 720, 30, "connected", "GOOD", null);
    }

    @Test
    void persistsMetricWithBackendClassification() {
        when(roomService.getInOrg(org.mockito.ArgumentMatchers.eq("room-1"), any())).thenReturn(null);
        when(participantSessionService.getById(sessionId)).thenReturn(session("room-1", USER));

        ConnectionQualityMetric saved = service.record("room-1", sessionId, CTX, AUTH, req());

        assertThat(saved.getQualityLevel()).isEqualTo(QualityLevel.GOOD);
        assertThat(saved.getRttMs()).isEqualTo(90);
        assertThat(saved.getParticipantId()).isEqualTo("user:" + USER);
        verify(repository).save(any(ConnectionQualityMetric.class));
    }

    @Test
    void rejectsWhenRoomMissing() {
        when(roomService.getInOrg(org.mockito.ArgumentMatchers.eq("ghost"), any()))
                .thenThrow(new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "ROOM_NOT_FOUND", "Room not found."));

        assertThatThrownBy(() -> service.record("ghost", sessionId, CTX, AUTH, req()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo("ROOM_NOT_FOUND"));
    }

    @Test
    void rejectsWhenSessionBelongsToAnotherRoom() {
        when(roomService.getInOrg(org.mockito.ArgumentMatchers.eq("room-1"), any())).thenReturn(null);
        when(participantSessionService.getById(sessionId)).thenReturn(session("room-2", USER));

        assertThatThrownBy(() -> service.record("room-1", sessionId, CTX, AUTH, req()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo("SESSION_ROOM_MISMATCH"));
    }

    @Test
    void forbiddenWhenSessionIsNotYours() {
        when(roomService.getInOrg(org.mockito.ArgumentMatchers.eq("room-1"), any())).thenReturn(null);
        when(participantSessionService.getById(sessionId)).thenReturn(session("room-1", OTHER));

        assertThatThrownBy(() -> service.record("room-1", sessionId, CTX, AUTH, req()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getErrorCode()).isEqualTo("FORBIDDEN");
                    assertThat(api.getStatus().value()).isEqualTo(403);
                });
    }

    @Test
    void bumpsReconnectCountFromClientHint() {
        ParticipantSession s = session("room-1", USER);
        when(roomService.getInOrg(org.mockito.ArgumentMatchers.eq("room-1"), any())).thenReturn(null);
        when(participantSessionService.getById(sessionId)).thenReturn(s);

        ConnectionMetricRequest withReconnects = new ConnectionMetricRequest(null, 90, 1.0, 10,
                null, null, null, null, null, "connected", "GOOD", 3);
        service.record("room-1", sessionId, CTX, AUTH, withReconnects);

        assertThat(s.getReconnectCount()).isEqualTo(3);
        verify(participantSessionService).save(s);
    }

    @Test
    void logsConnectionQualityChangedWhenLevelChanges() {
        when(roomService.getInOrg(org.mockito.ArgumentMatchers.eq("room-1"), any())).thenReturn(null);
        when(participantSessionService.getById(sessionId)).thenReturn(session("room-1", USER));

        Logger serviceLogger = (Logger) LoggerFactory.getLogger(ConnectionMetricsService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        serviceLogger.addAppender(appender);
        try {
            ConnectionQualityMetric previous = ConnectionQualityMetric.record("room-1", sessionId,
                    "user:" + USER, Instant.now(), QualityLevel.GOOD, 100, 1.0, 10,
                    null, null, null, null, null, "connected");
            when(repository.findBySessionIdOrderByRecordedAtAsc(sessionId))
                    .thenReturn(List.of(previous, previous));

            ConnectionMetricRequest poor = new ConnectionMetricRequest(null, 320, 8.0, 60,
                    null, null, null, null, null, "connected", null, null);
            service.record("room-1", sessionId, CTX, AUTH, poor);

            boolean changed = appender.list.stream()
                    .flatMap(e -> e.getKeyValuePairs() == null ? java.util.stream.Stream.empty()
                            : e.getKeyValuePairs().stream())
                    .anyMatch(kv -> kv.key.equals("event") && kv.value.equals("CONNECTION_QUALITY_CHANGED"));
            assertThat(changed).isTrue();
        } finally {
            serviceLogger.detachAppender(appender);
        }
    }
}
