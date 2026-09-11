package com.videoplatform.participant;

import com.videoplatform.common.ApiException;
import com.videoplatform.common.logging.LogEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Gerencia as sessoes de participacao. Chamado a partir dos webhooks do LiveKit.
 */
@Service
public class ParticipantSessionService {

    private static final Logger log = LoggerFactory.getLogger(ParticipantSessionService.class);

    private final ParticipantSessionRepository repository;
    private final ParticipantService participantService;

    public ParticipantSessionService(ParticipantSessionRepository repository,
                                     ParticipantService participantService) {
        this.repository = repository;
        this.participantService = participantService;
    }

    /**
     * Abre uma sessao para o participante. Se ja existe uma sessao aberta para
     * o par (roomId, participantId) trata-se de reconexao/reenvio: nao cria
     * outra e devolve a sessao existente.
     */
    @Transactional
    public ParticipantSession startSession(String roomId, String participantId, UUID userId,
                                           String participantName, Instant joinedAt) {
        Participant identity = participantService.getOrCreate(roomId, participantId, participantName, joinedAt);
        Optional<ParticipantSession> open = repository
                .findFirstByRoomIdAndParticipantIdAndLeftAtIsNullOrderByJoinedAtAsc(roomId, participantId);
        if (open.isPresent()) {
            ParticipantSession session = open.get();
            session.assignParticipant(identity.getId());
            session.registerReconnect();
            log.atInfo()
                    .addKeyValue("event", LogEvents.PARTICIPANT_RECONNECTED)
                    .addKeyValue("roomId", roomId)
                    .addKeyValue("participantId", participantId)
                    .addKeyValue("sessionId", session.getId())
                    .addKeyValue("reconnectCount", session.getReconnectCount())
                    .setMessage("participant reconnected (session already open)")
                    .log();
            return session;
        }
        ParticipantSession toSave = ParticipantSession.start(
                roomId, participantId, userId, participantName, joinedAt);
        toSave.assignParticipant(identity.getId());
        ParticipantSession session = repository.save(toSave);
        log.atInfo()
                .addKeyValue("event", LogEvents.PARTICIPANT_SESSION_STARTED)
                .addKeyValue("roomId", roomId)
                .addKeyValue("participantId", participantId)
                .addKeyValue("participantName", participantName)
                .addKeyValue("sessionId", session.getId())
                .setMessage("participant session started")
                .log();
        return session;
    }

    /**
     * Encerra a sessao aberta do participante e calcula a duracao. Se nao houver
     * sessao aberta (reenvio ou evento fora de ordem), apenas registra e segue.
     */
    @Transactional
    public Optional<ParticipantSession> endSession(String roomId, String participantId, Instant leftAt) {
        Optional<ParticipantSession> open = repository
                .findFirstByRoomIdAndParticipantIdAndLeftAtIsNullOrderByJoinedAtAsc(roomId, participantId);
        if (open.isEmpty()) {
            log.atWarn()
                    .addKeyValue("event", LogEvents.PARTICIPANT_SESSION_ENDED)
                    .addKeyValue("roomId", roomId)
                    .addKeyValue("participantId", participantId)
                    .setMessage("no open session to end, ignoring")
                    .log();
            return Optional.empty();
        }
        ParticipantSession session = open.get();
        session.end(leftAt);
        log.atInfo()
                .addKeyValue("event", LogEvents.PARTICIPANT_SESSION_ENDED)
                .addKeyValue("roomId", roomId)
                .addKeyValue("participantId", participantId)
                .addKeyValue("sessionId", session.getId())
                .addKeyValue("durationSeconds", session.getDurationSeconds())
                .setMessage("participant session ended")
                .log();
        return open;
    }

    @Transactional(readOnly = true)
    public List<ParticipantSession> listByRoom(String roomId) {
        return repository.findByRoomIdOrderByJoinedAtAsc(roomId);
    }

    @Transactional(readOnly = true)
    public ParticipantSession getById(UUID sessionId) {
        return repository.findById(sessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND",
                        "Participant session not found."));
    }

    @Transactional(readOnly = true)
    public Optional<ParticipantSession> findOpenSession(String roomId, String participantId) {
        return repository.findFirstByRoomIdAndParticipantIdAndLeftAtIsNullOrderByJoinedAtAsc(
                roomId, participantId);
    }

    @Transactional(readOnly = true)
    public Optional<ParticipantSession> findOpenSessionByUser(String roomId, UUID userId) {
        return repository.findFirstByRoomIdAndUserIdAndLeftAtIsNullOrderByJoinedAtAsc(roomId, userId);
    }

    /** Persiste alteracoes feitas por outro servico na entidade gerenciada. */
    @Transactional
    public void save(ParticipantSession session) {
        repository.save(session);
    }
}
