package com.videoplatform.participant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParticipantNameRegistryTest {

    @Test
    void recordsAndReturnsNamesPerRoom() {
        ParticipantNameRegistry registry = new ParticipantNameRegistry();
        registry.record("room-1", "user:aaa", "Raul");
        registry.record("room-1", "guest:bbb", "  Maria  ");
        registry.record("room-2", "user:ccc", "Outro");

        assertThat(registry.namesForRoom("room-1"))
                .containsEntry("user:aaa", "Raul")
                .containsEntry("guest:bbb", "Maria")
                .hasSize(2);
        assertThat(registry.namesForRoom("room-2")).containsEntry("user:ccc", "Outro");
        assertThat(registry.namesForRoom("desconhecida")).isEmpty();
    }

    @Test
    void ignoresBlankOrNullInput() {
        ParticipantNameRegistry registry = new ParticipantNameRegistry();
        registry.record("room-1", "user:aaa", "  ");
        registry.record("room-1", null, "Raul");
        registry.record(null, "user:aaa", "Raul");

        assertThat(registry.namesForRoom("room-1")).isEmpty();
    }

    @Test
    void latestNameWins() {
        ParticipantNameRegistry registry = new ParticipantNameRegistry();
        registry.record("room-1", "guest:bbb", "Maria");
        registry.record("room-1", "guest:bbb", "Maria Silva");

        assertThat(registry.namesForRoom("room-1")).containsEntry("guest:bbb", "Maria Silva");
    }
}
