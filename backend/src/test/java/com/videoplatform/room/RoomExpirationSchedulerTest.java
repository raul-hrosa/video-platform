package com.videoplatform.room;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomExpirationSchedulerTest {

    @Mock
    private RoomService roomService;

    @InjectMocks
    private RoomExpirationScheduler scheduler;

    @Test
    void sweepDelegatesToService() {
        when(roomService.expireDueRooms(any(Instant.class))).thenReturn(2);

        scheduler.sweep();

        verify(roomService).expireDueRooms(any(Instant.class));
    }

    @Test
    void sweepSwallowsExceptionsSoTheSchedulerKeepsRunning() {
        when(roomService.expireDueRooms(any(Instant.class)))
                .thenThrow(new RuntimeException("db unavailable"));

        assertThatCode(() -> scheduler.sweep()).doesNotThrowAnyException();
    }
}
