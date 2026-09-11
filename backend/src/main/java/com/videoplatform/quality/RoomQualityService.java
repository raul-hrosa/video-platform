package com.videoplatform.quality;

import com.videoplatform.participant.ParticipantSession;
import com.videoplatform.participant.ParticipantSessionService;
import com.videoplatform.provider.PlatformParticipantIdentity;
import com.videoplatform.provider.QualityMapper;
import com.videoplatform.quality.dto.RoomQualityResponse;
import com.videoplatform.quality.dto.RoomQualityResponse.ParticipantQuality;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Visão de qualidade de conexão por participante de uma sala (Sprint 6 §21-24).
 *
 * <p>Lista <b>todos</b> os participantes da sala. Para os que enviaram snapshots
 * (Sprint 3), mostra a última leitura na mesma escala {@link QualityLevel} — não
 * há segunda escala (§21, §25). Para os demais (ex.: convidados, que não
 * persistem métricas), {@code latestLevel} e as métricas ficam {@code null}:
 * "sem medição", nunca um dado inventado (§22). A checagem de ownership é
 * responsabilidade do chamador.
 */
@Service
public class RoomQualityService {

    private final ParticipantSessionService participantSessionService;
    private final ConnectionQualityMetricRepository metricRepository;

    public RoomQualityService(ParticipantSessionService participantSessionService,
                              ConnectionQualityMetricRepository metricRepository) {
        this.participantSessionService = participantSessionService;
        this.metricRepository = metricRepository;
    }

    @Transactional(readOnly = true)
    public RoomQualityResponse forRoom(String roomId) {
        return build(
                participantSessionService.listByRoom(roomId),
                metricRepository.findByRoomIdOrderByRecordedAtAsc(roomId));
    }

    /** Visível para teste: a lógica de agregação, sem tocar no banco. */
    static RoomQualityResponse build(List<ParticipantSession> sessions,
                                     List<ConnectionQualityMetric> metricsAsc) {
        Map<String, List<ConnectionQualityMetric>> byParticipant = new LinkedHashMap<>();
        for (ConnectionQualityMetric m : metricsAsc) {
            byParticipant.computeIfAbsent(m.getParticipantId(), k -> new ArrayList<>()).add(m);
        }

        // Ordem de exibição: participantes na ordem em que entraram na sala.
        Map<String, String> nameByParticipant = new LinkedHashMap<>();
        for (ParticipantSession s : sessions) {
            nameByParticipant.putIfAbsent(s.getParticipantId(), s.getParticipantName());
        }
        // Defensivo: métricas de um participante sem sessão registrada.
        for (String pid : byParticipant.keySet()) {
            nameByParticipant.putIfAbsent(pid, pid);
        }

        boolean hasData = !byParticipant.isEmpty();
        boolean timelineAvailable = byParticipant.values().stream().anyMatch(l -> l.size() >= 2);

        // Sem nenhuma métrica na sala: nada a mostrar por participante (§23).
        if (!hasData) {
            return new RoomQualityResponse(false, false, List.of());
        }

        List<ParticipantQuality> participants = new ArrayList<>();
        for (Map.Entry<String, String> e : nameByParticipant.entrySet()) {
            String pid = e.getKey();
            List<ConnectionQualityMetric> history = byParticipant.get(pid);
            boolean guest = PlatformParticipantIdentity.isGuest(pid);
            if (history == null || history.isEmpty()) {
                participants.add(new ParticipantQuality(
                        pid, e.getValue(), guest, null, null, null, null, null, 0));
                continue;
            }
            ConnectionQualityMetric latest = history.stream()
                    .max(Comparator.comparing(ConnectionQualityMetric::getRecordedAt))
                    .orElseThrow();
            participants.add(new ParticipantQuality(
                    pid, e.getValue(), guest,
                    QualityMapper.fromLegacy(latest.getQualityLevel()).name(),
                    latest.getRttMs(), latest.getPacketLossPercent(), latest.getJitterMs(),
                    latest.getRecordedAt(), history.size()));
        }

        return new RoomQualityResponse(hasData, timelineAvailable, participants);
    }
}
