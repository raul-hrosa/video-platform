package com.videoplatform.participant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

    Optional<Participant> findByRoomIdAndParticipantRef(String roomId, String participantRef);

    List<Participant> findByRoomIdOrderByFirstSeenAtAsc(String roomId);
}
