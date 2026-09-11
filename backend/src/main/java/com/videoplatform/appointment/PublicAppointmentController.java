package com.videoplatform.appointment;

import com.videoplatform.appointment.AppointmentAccessService.Resolution;
import com.videoplatform.appointment.AppointmentAccessService.State;
import com.videoplatform.appointment.dto.PublicAppointmentResponse;
import com.videoplatform.appointment.dto.PublicJoinRequest;
import com.videoplatform.common.ApiException;
import com.videoplatform.common.logging.LogEvents;
import com.videoplatform.provider.MediaConnectionInfoProvider;
import com.videoplatform.provider.MediaTokenProvider;
import com.videoplatform.provider.ParticipantIdentityProvider;
import com.videoplatform.room.dto.TokenResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Link publico do Appointment (§28, §32). Rota aberta (permitAll): permite
 * <b>somente participar</b> — nunca editar, cancelar, ver analytics/historico/
 * sessions/quality/organization. Devolve apenas o minimo para a experiencia do
 * participante.
 */
@RestController
@RequestMapping("/api/v1/public/appointments")
public class PublicAppointmentController {

    private static final Logger log = LoggerFactory.getLogger(PublicAppointmentController.class);

    private final AppointmentAccessService accessService;
    private final MediaTokenProvider tokenProvider;
    private final MediaConnectionInfoProvider connectionInfoProvider;
    private final ParticipantIdentityProvider participantIdentityProvider;
    private final com.videoplatform.participant.ParticipantNameRegistry participantNameRegistry;

    public PublicAppointmentController(AppointmentAccessService accessService,
                                       MediaTokenProvider tokenProvider,
                                       MediaConnectionInfoProvider connectionInfoProvider,
                                       ParticipantIdentityProvider participantIdentityProvider,
                                       com.videoplatform.participant.ParticipantNameRegistry participantNameRegistry) {
        this.accessService = accessService;
        this.tokenProvider = tokenProvider;
        this.connectionInfoProvider = connectionInfoProvider;
        this.participantIdentityProvider = participantIdentityProvider;
        this.participantNameRegistry = participantNameRegistry;
    }

    @GetMapping("/{publicAccessId}")
    public PublicAppointmentResponse resolve(@PathVariable String publicAccessId) {
        Resolution resolution = accessService.resolve(publicAccessId);
        log.atInfo()
                .addKeyValue("event", LogEvents.APPOINTMENT_VIEWED)
                .addKeyValue("appointmentId", resolution.appointment().getId())
                .addKeyValue("state", resolution.state().name())
                .setMessage("public appointment link viewed")
                .log();
        return PublicAppointmentResponse.from(resolution);
    }

    @PostMapping("/{publicAccessId}/token")
    public TokenResponse join(@PathVariable String publicAccessId,
                              @Valid @RequestBody PublicJoinRequest request) {
        Resolution resolution = accessService.resolve(publicAccessId);
        if (!resolution.canJoin()) {
            throw notJoinable(resolution.state());
        }

        String name = resolveName(request.name(), resolution.appointment().getParticipantName());
        String identity = participantIdentityProvider.forGuest();
        MediaTokenProvider.MediaToken result = tokenProvider.createRoomToken(
                resolution.roomId(), identity, name);
        participantNameRegistry.record(resolution.roomId(), identity, name);

        String event = resolution.state() == State.WAITING_ROOM
                ? LogEvents.WAITING_ROOM_ENTERED
                : LogEvents.APPOINTMENT_CALL_STARTED;
        log.atInfo()
                .addKeyValue("event", event)
                .addKeyValue("appointmentId", resolution.appointment().getId())
                .addKeyValue("roomId", resolution.roomId())
                .setMessage("participant entered appointment via public link")
                .log();
        return new TokenResponse(result.token(), resolution.roomId(), result.participantRef(),
                connectionInfoProvider.serverUrl());
    }

    private static String resolveName(String provided, String preConfigured) {
        String name = provided != null && !provided.isBlank() ? provided.trim()
                : (preConfigured != null ? preConfigured.trim() : null);
        if (name == null || name.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NAME_REQUIRED", "Informe o seu nome.");
        }
        return name;
    }

    private static ApiException notJoinable(State state) {
        return switch (state) {
            case CANCELLED -> new ApiException(HttpStatus.CONFLICT, "APPOINTMENT_CANCELLED",
                    "Este atendimento foi cancelado.");
            case ENDED -> new ApiException(HttpStatus.CONFLICT, "APPOINTMENT_ENDED",
                    "Este atendimento ja foi encerrado.");
            case BEFORE_WINDOW -> new ApiException(HttpStatus.CONFLICT, "APPOINTMENT_NOT_YET_OPEN",
                    "Este atendimento ainda nao esta disponivel.");
            default -> new ApiException(HttpStatus.CONFLICT, "APPOINTMENT_NOT_JOINABLE",
                    "Nao e' possivel entrar neste atendimento agora.");
        };
    }
}
