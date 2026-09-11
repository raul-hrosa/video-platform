package com.videoplatform.participant;

import com.videoplatform.common.logging.LogEvents;
import com.videoplatform.provider.PlatformParticipantIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Resolve a identidade {@link Participant} de uma sala a partir do identity do
 * LiveKit (Sprint 9 §10). Get-or-create idempotente, tolerante a corrida entre
 * o webhook {@code participant_joined} e reenvios.
 */
@Service
public class ParticipantService {

    private static final Logger log = LoggerFactory.getLogger(ParticipantService.class);

    private final ParticipantRepository repository;

    public ParticipantService(ParticipantRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Participant getOrCreate(String roomId, String identity, String displayName, Instant now) {
        return repository.findByRoomIdAndParticipantRef(roomId, identity)
                .map(existing -> {
                    existing.touch(displayName, now);
                    return existing;
                })
                .orElseGet(() -> create(roomId, identity, displayName, now));
    }

    private Participant create(String roomId, String identity, String displayName, Instant now) {
        ParticipantKind kind = PlatformParticipantIdentity.isGuest(identity) ? ParticipantKind.GUEST : ParticipantKind.USER;
        Participant participant = Participant.create(
                roomId, identity, kind, PlatformParticipantIdentity.parseUserId(identity), displayName, now);
        try {
            Participant saved = repository.save(participant);
            log.atInfo()
                    .addKeyValue("event", LogEvents.PARTICIPANT_IDENTIFIED)
                    .addKeyValue("roomId", roomId)
                    .addKeyValue("participantRef", identity)
                    .addKeyValue("participantId", saved.getId())
                    .addKeyValue("kind", kind)
                    .setMessage("participant identity created")
                    .log();
            return saved;
        } catch (DataIntegrityViolationException race) {
            // Outra transacao criou a identidade em paralelo — reaproveita.
            return repository.findByRoomIdAndParticipantRef(roomId, identity)
                    .map(existing -> {
                        existing.touch(displayName, now);
                        return existing;
                    })
                    .orElseThrow(() -> race);
        }
    }

    @Transactional(readOnly = true)
    public List<Participant> listByRoom(String roomId) {
        return repository.findByRoomIdOrderByFirstSeenAtAsc(roomId);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Participant> find(String roomId, String participantRef) {
        return repository.findByRoomIdAndParticipantRef(roomId, participantRef);
    }
}
