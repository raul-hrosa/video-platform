package com.videoplatform.room;

import com.videoplatform.auth.UserDirectory;
import com.videoplatform.auth.security.AuthenticatedUser;
import com.videoplatform.common.ApiException;
import com.videoplatform.common.PageResponse;
import com.videoplatform.common.logging.LogEvents;
import com.videoplatform.organization.OrganizationContext;
import com.videoplatform.participant.ParticipantSessionService;
import com.videoplatform.participant.ParticipantSummaryService;
import com.videoplatform.participant.RoomParticipantsService;
import com.videoplatform.participant.dto.ParticipantSessionResponse;
import com.videoplatform.participant.dto.ParticipantSummaryResponse.RoomParticipantsSummary;
import com.videoplatform.participant.dto.RoomParticipantResponse;
import com.videoplatform.provider.MediaConnectionInfoProvider;
import com.videoplatform.provider.MediaTokenProvider;
import com.videoplatform.provider.ParticipantIdentityProvider;
import com.videoplatform.quality.RoomQualityService;
import com.videoplatform.quality.dto.RoomQualityResponse;
import com.videoplatform.room.dto.GuestTokenRequest;
import com.videoplatform.room.dto.RoomResponse;
import com.videoplatform.room.dto.TokenResponse;
import com.videoplatform.roomprofile.RoomProfile;
import com.videoplatform.roomprofile.RoomProfileService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

    private final RoomService roomService;
    private final MediaTokenProvider tokenProvider;
    private final MediaConnectionInfoProvider connectionInfoProvider;
    private final ParticipantIdentityProvider participantIdentityProvider;
    private final ParticipantSessionService participantSessionService;
    private final RoomProfileService roomProfileService;
    private final ParticipantSummaryService participantSummaryService;
    private final RoomQualityService roomQualityService;
    private final RoomParticipantsService roomParticipantsService;
    private final RoomEventsService roomEventsService;
    private final UserDirectory userDirectory;
    private final com.videoplatform.participant.ParticipantNameRegistry participantNameRegistry;

    public RoomController(RoomService roomService, MediaTokenProvider tokenProvider,
                          MediaConnectionInfoProvider connectionInfoProvider,
                          ParticipantIdentityProvider participantIdentityProvider,
                          ParticipantSessionService participantSessionService,
                          RoomProfileService roomProfileService,
                          ParticipantSummaryService participantSummaryService,
                          RoomQualityService roomQualityService,
                          RoomParticipantsService roomParticipantsService,
                          RoomEventsService roomEventsService,
                          UserDirectory userDirectory,
                          com.videoplatform.participant.ParticipantNameRegistry participantNameRegistry) {
        this.roomService = roomService;
        this.tokenProvider = tokenProvider;
        this.connectionInfoProvider = connectionInfoProvider;
        this.participantIdentityProvider = participantIdentityProvider;
        this.participantSessionService = participantSessionService;
        this.roomProfileService = roomProfileService;
        this.participantSummaryService = participantSummaryService;
        this.roomQualityService = roomQualityService;
        this.roomParticipantsService = roomParticipantsService;
        this.roomEventsService = roomEventsService;
        this.userDirectory = userDirectory;
        this.participantNameRegistry = participantNameRegistry;
    }

    // ---- gestao (membros da Organization, conforme role) ----

    /**
     * Cria uma Room de infraestrutura direta (Sprint 9 §6): um clique, sem nome,
     * tipo, finalidade ou horario. O identificador e' gerado pelo backend. Para
     * criar a partir de um Room Profile, ver POST /api/v1/room-profiles/{id}/rooms.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse create(OrganizationContext ctx) {
        Room room = roomService.createDirect(ctx);
        return toResponse(room, null,
                userDirectory.namesByIds(List.of(room.getOwnerId())).get(room.getOwnerId()));
    }

    /**
     * Histórico de salas da Organization (Sprint 7 §20). Filtros opcionais:
     * {@code status}, {@code roomProfileId}, {@code createdFrom}, {@code createdTo}.
     * Não lista somente as salas criadas pelo usuário.
     */
    @GetMapping
    public PageResponse<RoomResponse> list(
            OrganizationContext ctx,
            @RequestParam(required = false) RoomStatus status,
            @RequestParam(required = false) UUID roomProfileId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            Pageable pageable) {
        Page<Room> page = roomService.listHistory(ctx,
                RoomHistoryQuery.of(status, roomProfileId, createdFrom, createdTo), pageable);
        Map<UUID, RoomProfile> profiles = roomProfileService.mapByIds(
                page.getContent().stream().map(Room::getRoomProfileId)
                        .filter(java.util.Objects::nonNull).toList());
        Map<UUID, String> owners = userDirectory.namesByIds(
                page.getContent().stream().map(Room::getOwnerId).toList());
        Map<String, Integer> connectedCounts = participantSessionService.countConnectedByRoomIds(
                page.getContent().stream().map(Room::getRoomId).toList());
        return PageResponse.from(page, room -> toResponse(room,
                room.getRoomProfileId() == null ? null : profiles.get(room.getRoomProfileId()),
                owners.get(room.getOwnerId()),
                connectedCounts.getOrDefault(room.getRoomId(), 0)));
    }

    /** Panorama do dashboard da Organization (Sprint 7 §22). */
    @GetMapping("/summary")
    public com.videoplatform.room.dto.RoomDashboardResponse summary(
            OrganizationContext ctx,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant todayStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant weekStart) {
        return roomService.dashboard(ctx, todayStart, weekStart);
    }

    @GetMapping("/{roomId}")
    public RoomResponse get(OrganizationContext ctx, @PathVariable String roomId) {
        Room room = roomService.getInOrg(roomId, ctx);
        roomService.logView(LogEvents.ROOM_DETAIL_VIEWED, room, ctx);
        RoomProfile profile = room.getRoomProfileId() == null ? null
                : roomProfileService.mapByIds(List.of(room.getRoomProfileId())).get(room.getRoomProfileId());
        return toResponse(room, profile,
                userDirectory.namesByIds(List.of(room.getOwnerId())).get(room.getOwnerId()));
    }

    /**
     * Identidades de participante da sala, com totais agregados (Sprint 9 §10).
     * <b>Breaking change controlado:</b> ate a Sprint 8 este endpoint devolvia a
     * lista de sessoes individuais — isso agora vive em {@code GET .../sessions}.
     */
    @GetMapping("/{roomId}/participants")
    public List<RoomParticipantResponse> participants(OrganizationContext ctx,
                                                      @PathVariable String roomId) {
        Room room = roomService.getInOrg(roomId, ctx);
        roomService.logView(LogEvents.ROOM_PARTICIPANTS_VIEWED, room, ctx);
        return roomParticipantsService.listByRoom(roomId);
    }

    /** Analytics detalhado de um participante da sala (Sprint 9 §11). */
    @GetMapping("/{roomId}/participants/{participantRef}")
    public com.videoplatform.participant.dto.ParticipantAnalyticsResponse participantDetail(
            OrganizationContext ctx, @PathVariable String roomId, @PathVariable String participantRef) {
        Room room = roomService.getInOrg(roomId, ctx);
        roomService.logView(LogEvents.PARTICIPANT_ANALYTICS_VIEWED, room, ctx);
        return roomParticipantsService.detail(roomId, participantRef);
    }

    /** Linha do tempo de eventos da sala (Sprint 9 §14). */
    @GetMapping("/{roomId}/events")
    public List<com.videoplatform.room.dto.RoomEventResponse> events(OrganizationContext ctx,
                                                                     @PathVariable String roomId) {
        Room room = roomService.getInOrg(roomId, ctx);
        roomService.logView(LogEvents.ROOM_EVENTS_VIEWED, room, ctx);
        return roomEventsService.forRoom(room);
    }

    @GetMapping("/{roomId}/sessions")
    public List<ParticipantSessionResponse> sessions(OrganizationContext ctx,
                                                     @PathVariable String roomId) {
        Room room = roomService.getInOrg(roomId, ctx);
        roomService.logView(LogEvents.ROOM_SESSIONS_VIEWED, room, ctx);
        return participantSessionService.listByRoom(roomId).stream()
                .map(ParticipantSessionResponse::from)
                .toList();
    }

    /** Resumo agregado por participante (Sprint 6 §30). */
    @GetMapping("/{roomId}/participants/summary")
    public RoomParticipantsSummary participantsSummary(OrganizationContext ctx,
                                                       @PathVariable String roomId) {
        Room room = roomService.getInOrg(roomId, ctx);
        roomService.logView(LogEvents.ROOM_PARTICIPANTS_VIEWED, room, ctx);
        return participantSummaryService.forRoom(roomId);
    }

    /** Qualidade de conexão por participante (Sprint 6 §21-24). */
    @GetMapping("/{roomId}/quality")
    public RoomQualityResponse quality(OrganizationContext ctx, @PathVariable String roomId) {
        Room room = roomService.getInOrg(roomId, ctx);
        roomService.logView(LogEvents.ROOM_QUALITY_VIEWED, room, ctx);
        return roomQualityService.forRoom(roomId);
    }

    private static RoomResponse toResponse(Room room, RoomProfile profile, String ownerName) {
        return toResponse(room, profile, ownerName, 0);
    }

    private static RoomResponse toResponse(Room room, RoomProfile profile, String ownerName,
                                           int connectedCount) {
        return profile == null
                ? RoomResponse.from(room, null, null, ownerName, connectedCount)
                : RoomResponse.from(room, profile.getName(), profile.getType().name(), ownerName, connectedCount);
    }

    // ---- entrar na chamada ----

    @PostMapping("/{roomId}/token")
    public TokenResponse createToken(OrganizationContext ctx,
                                     @AuthenticationPrincipal AuthenticatedUser user,
                                     @PathVariable String roomId) {
        Room room = roomService.getJoinableInOrg(roomId, ctx);
        String identity = participantIdentityProvider.forUser(user.id());
        MediaTokenProvider.MediaToken result = tokenProvider.createRoomToken(
                room.getRoomId(), identity, user.name());
        participantNameRegistry.record(room.getRoomId(), identity, user.name());
        return new TokenResponse(result.token(), room.getRoomId(), result.participantRef(),
                connectionInfoProvider.serverUrl());
    }

    /**
     * Entrada de visitante <b>sem conta</b> pelo link da sala (Sprint 5 §63.6):
     * rota publica. O convidado nao pertence a Organization nenhuma (Sprint 7
     * §43) — so a existencia/expiracao da sala e' checada.
     */
    @PostMapping("/{roomId}/guest-token")
    public TokenResponse createGuestToken(@PathVariable String roomId,
                                          @Valid @RequestBody GuestTokenRequest request) {
        Room room = roomService.getJoinableByRoomId(roomId);
        String identity = participantIdentityProvider.forGuest();
        MediaTokenProvider.MediaToken result = tokenProvider.createRoomToken(
                room.getRoomId(), identity, request.name().trim());
        participantNameRegistry.record(room.getRoomId(), identity, request.name().trim());
        return new TokenResponse(result.token(), room.getRoomId(), result.participantRef(),
                connectionInfoProvider.serverUrl());
    }

    /**
     * Nomes de exibicao por identidade de participante (Sprint 11). Rota de
     * leitura pública: o provider de mídia não propaga o nome dos outros
     * participantes, então o cliente na chamada resolve por aqui casando
     * o prefixo {@code <sub>} da identidade {@code <sub>.<8hex>}. Não expõe nada
     * sensível — só {@code {"user:uuid":"Nome"}} de quem pegou token nesta sala.
     */
    @GetMapping("/{roomId}/participant-names")
    public Map<String, String> participantNames(@PathVariable String roomId) {
        return participantNameRegistry.namesForRoom(roomId);
    }

    /** A propria sessao aberta do usuario naquela sala (para reportar qualidade). */
    @GetMapping("/{roomId}/sessions/mine")
    public ParticipantSessionResponse mySession(@AuthenticationPrincipal AuthenticatedUser user,
                                                @PathVariable String roomId) {
        roomService.getByRoomId(roomId);
        return participantSessionService.findOpenSessionByUser(roomId, user.id())
                .map(ParticipantSessionResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND",
                        "Nenhuma sessao aberta para voce nesta sala."));
    }
}
