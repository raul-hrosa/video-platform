package com.videoplatform.participant;

import com.videoplatform.participant.dto.ParticipantSummaryResponse;
import com.videoplatform.participant.dto.ParticipantSummaryResponse.RoomParticipantsSummary;
import com.videoplatform.provider.PlatformParticipantIdentity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agrega as {@link ParticipantSession}s de uma sala por participante (Sprint 6
 * §26-30). Usa apenas dados já persistidos — nada é inventado.
 */
@Service
public class ParticipantSummaryService {

    private final ParticipantSessionService participantSessionService;

    public ParticipantSummaryService(ParticipantSessionService participantSessionService) {
        this.participantSessionService = participantSessionService;
    }

    @Transactional(readOnly = true)
    public RoomParticipantsSummary forRoom(String roomId) {
        return summarize(participantSessionService.listByRoom(roomId), Instant.now());
    }

    /** Visível para teste: a lógica de agregação, sem tocar no banco. */
    static RoomParticipantsSummary summarize(List<ParticipantSession> sessions, Instant now) {
        Map<String, List<ParticipantSession>> byParticipant = new LinkedHashMap<>();
        for (ParticipantSession s : sessions) {
            byParticipant.computeIfAbsent(s.getParticipantId(), k -> new ArrayList<>()).add(s);
        }

        List<ParticipantSummaryResponse> participants = new ArrayList<>();
        int totalSessions = 0;
        long totalSeconds = 0;
        for (Map.Entry<String, List<ParticipantSession>> e : byParticipant.entrySet()) {
            List<ParticipantSession> list = e.getValue();
            long personSeconds = 0;
            int completed = 0;
            int reconnects = 0;
            Instant firstJoined = null;
            Instant lastLeft = null;
            boolean hasOpen = false;
            for (ParticipantSession s : list) {
                Instant end = s.getLeftAt() != null ? s.getLeftAt() : now;
                personSeconds += Math.max(0, Duration.between(s.getJoinedAt(), end).getSeconds());
                reconnects += s.getReconnectCount();
                if (firstJoined == null || s.getJoinedAt().isBefore(firstJoined)) {
                    firstJoined = s.getJoinedAt();
                }
                if (s.getLeftAt() != null) {
                    completed++;
                    if (lastLeft == null || s.getLeftAt().isAfter(lastLeft)) {
                        lastLeft = s.getLeftAt();
                    }
                } else {
                    hasOpen = true;
                }
            }
            participants.add(new ParticipantSummaryResponse(
                    e.getKey(),
                    list.get(list.size() - 1).getParticipantName(),
                    PlatformParticipantIdentity.isGuest(e.getKey()),
                    list.size(),
                    completed,
                    reconnects,
                    personSeconds,
                    firstJoined,
                    hasOpen ? null : lastLeft));
            totalSessions += list.size();
            totalSeconds += personSeconds;
        }

        return new RoomParticipantsSummary(
                byParticipant.size(), totalSessions, totalSessions, totalSeconds, participants);
    }
}
