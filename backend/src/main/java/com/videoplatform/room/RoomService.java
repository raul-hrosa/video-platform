package com.videoplatform.room;

import com.videoplatform.common.ApiException;
import com.videoplatform.common.logging.LogEvents;
import com.videoplatform.organization.OrganizationContext;
import com.videoplatform.roomprofile.RoomProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras de negocio das salas. A partir da Sprint 7 o isolamento e' por
 * <b>Organization</b> (não mais por {@code owner_id}): a gestão de uma sala é
 * acessível a qualquer membro da Organization dona, conforme a role (§24). O
 * {@code ownerId} continua registrando quem criou a sala.
 */
@Service
public class RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUM = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final RoomRepository roomRepository;
    private final com.videoplatform.participant.ParticipantSessionRepository participantSessionRepository;

    public RoomService(RoomRepository roomRepository,
                       com.videoplatform.participant.ParticipantSessionRepository participantSessionRepository) {
        this.roomRepository = roomRepository;
        this.participantSessionRepository = participantSessionRepository;
    }

    /**
     * Cria uma Room a partir de um {@link RoomProfile} (ja validado e no tenant
     * correto pelo chamador). A Room herda a Organization do Profile; o
     * {@code owner} e' o usuario do contexto (Sprint 7 §14).
     */
    @Transactional
    public Room createFromProfile(RoomProfile profile, OrganizationContext ctx) {
        String roomId = generateUniqueRoomId();
        Room room = roomRepository.save(Room.createFromProfile(
                roomId, profile.getId(), profile.getOrganizationId(),
                profile.getName(), profile.getDurationMinutes(), ctx.userId(), Instant.now()));
        log.atInfo()
                .addKeyValue("event", LogEvents.ROOM_CREATED_FROM_PROFILE)
                .addKeyValue("roomId", roomId)
                .addKeyValue("organizationId", profile.getOrganizationId())
                .addKeyValue("roomProfileId", profile.getId())
                .addKeyValue("userId", ctx.userId())
                .addKeyValue("durationMinutes", profile.getDurationMinutes())
                .addKeyValue("expiresAt", room.getExpiresAt().toString())
                .setMessage("room created from profile")
                .log();
        return room;
    }

    /**
     * Cria uma Room de infraestrutura direta (Sprint 9 §6): sem Profile, sem nome,
     * sem duracao/expiracao. Herda a Organization do contexto; o {@code owner} e' o
     * usuario que chamou.
     */
    @Transactional
    public Room createDirect(OrganizationContext ctx) {
        String roomId = generateUniqueRoomId();
        Room room = roomRepository.save(Room.createDirect(
                roomId, ctx.organizationId(), ctx.userId(), Instant.now()));
        log.atInfo()
                .addKeyValue("event", LogEvents.ROOM_CREATED)
                .addKeyValue("roomId", roomId)
                .addKeyValue("organizationId", ctx.organizationId())
                .addKeyValue("userId", ctx.userId())
                .addKeyValue("creationMode", "DIRECT")
                .setMessage("room created")
                .log();
        return room;
    }

    /**
     * Cria a Room de uma ocorrencia de Appointment (Sprint 8 §28.4). Sem
     * {@link OrganizationContext}: o acesso e' pelo link publico. A Room herda a
     * Organization/owner do Appointment; {@code expiresAt} = fim agendado.
     */
    @Transactional
    public Room createForOccurrence(UUID roomProfileId, UUID organizationId, UUID ownerId,
                                    String name, int durationMinutes, UUID occurrenceId,
                                    Instant scheduledEnd, Instant now) {
        String roomId = generateUniqueRoomId();
        Room room = roomRepository.save(Room.createForOccurrence(
                roomId, roomProfileId, organizationId, name, durationMinutes,
                ownerId, occurrenceId, scheduledEnd, now));
        log.atInfo()
                .addKeyValue("event", LogEvents.ROOM_CREATED_FROM_PROFILE)
                .addKeyValue("roomId", roomId)
                .addKeyValue("organizationId", organizationId)
                .addKeyValue("roomProfileId", roomProfileId)
                .addKeyValue("appointmentOccurrenceId", occurrenceId)
                .addKeyValue("expiresAt", room.getExpiresAt().toString())
                .setMessage("room created for appointment occurrence")
                .log();
        return room;
    }

    /** Status de varias Rooms por id — para o sync de estado das ocorrencias (Sprint 8). */
    @Transactional(readOnly = true)
    public java.util.Map<UUID, RoomStatus> roomStatusByIds(java.util.Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return java.util.Map.of();
        }
        return roomRepository.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(Room::getId, Room::getStatus));
    }

    /** Codigo publico ({@code room-xxxx}) de varias Rooms por id — para a listagem de ocorrencias (Sprint 8). */
    @Transactional(readOnly = true)
    public java.util.Map<UUID, String> roomCodesByIds(java.util.Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return java.util.Map.of();
        }
        return roomRepository.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(Room::getId, Room::getRoomId));
    }

    /** Consulta sem checagem de tenant — usada pelos webhooks e pela ingestao de qualidade. */
    @Transactional(readOnly = true)
    public Room getByRoomId(String roomId) {
        return findOrThrow(roomId);
    }

    /** Consulta por id interno (UUID) — usada na materializacao de ocorrencia (Sprint 8). */
    @Transactional(readOnly = true)
    public Room requireById(UUID id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "Room not found."));
    }

    /**
     * Consulta para <b>entrar na chamada</b> como visitante sem conta: a sala
     * precisa existir e nao estar expirada. Sem checagem de tenant (o convidado
     * nao pertence a Organization nenhuma — Sprint 7 §43).
     */
    @Transactional(readOnly = true)
    public Room getJoinableByRoomId(String roomId) {
        Room room = findOrThrow(roomId);
        ensureJoinable(room);
        return room;
    }

    /**
     * Consulta para entrar na chamada como <b>usuario autenticado</b>: alem de
     * existir e nao estar expirada, a sala precisa ser da Organization do
     * usuario — impede minerar token de sala de outro tenant (Sprint 7 §15).
     */
    @Transactional(readOnly = true)
    public Room getJoinableInOrg(String roomId, OrganizationContext ctx) {
        Room room = requireInOrg(roomId, ctx);
        ensureJoinable(room);
        return room;
    }

    /**
     * Consulta de gestao: a sala precisa pertencer a' Organization do usuario.
     * Uma sala de outra Organization e' tratada como inexistente — nunca vaza
     * (Sprint 7 §15, §24). Substitui o antigo {@code getOwnedByRoomId}.
     */
    @Transactional(readOnly = true)
    public Room getInOrg(String roomId, OrganizationContext ctx) {
        return requireInOrg(roomId, ctx);
    }

    private Room requireInOrg(String roomId, OrganizationContext ctx) {
        Room room = findOrThrow(roomId);
        if (!room.belongsToOrg(ctx.organizationId())) {
            log.atWarn()
                    .addKeyValue("event", LogEvents.ORGANIZATION_ACCESS_DENIED)
                    .addKeyValue("organizationId", ctx.organizationId())
                    .addKeyValue("resourceOrganizationId", room.getOrganizationId())
                    .addKeyValue("roomId", roomId)
                    .addKeyValue("userId", ctx.userId())
                    .setMessage("cross-organization room access blocked")
                    .log();
            throw new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "Room not found.");
        }
        return room;
    }

    private void ensureJoinable(Room room) {
        if (room.isExpired(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "ROOM_EXPIRED",
                    "Esta sala expirou e nao aceita novos participantes.");
        }
    }

    /** Histórico de salas da Organization (Sprint 7 §20). */
    @Transactional(readOnly = true)
    public Page<Room> listHistory(OrganizationContext ctx, RoomHistoryQuery query, Pageable pageable) {
        Pageable effective = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Room> page = roomRepository.findAll(historySpec(ctx.organizationId(), query), effective);
        log.atInfo()
                .addKeyValue("event", LogEvents.ROOM_HISTORY_VIEWED)
                .addKeyValue("organizationId", ctx.organizationId())
                .addKeyValue("userId", ctx.userId())
                .addKeyValue("status", query.status())
                .addKeyValue("roomProfileId", query.roomProfileId())
                .addKeyValue("resultCount", page.getNumberOfElements())
                .setMessage("room history viewed")
                .log();
        return page;
    }

    /** Panorama do dashboard da Organization (Sprint 7 §22). */
    @Transactional(readOnly = true)
    public com.videoplatform.room.dto.RoomDashboardResponse dashboard(
            OrganizationContext ctx, Instant todayStart, Instant weekStart) {
        UUID orgId = ctx.organizationId();
        return new com.videoplatform.room.dto.RoomDashboardResponse(
                roomRepository.countByOrganizationIdAndCreatedAtGreaterThanEqual(orgId, todayStart),
                roomRepository.countByOrganizationIdAndCreatedAtGreaterThanEqual(orgId, weekStart),
                roomRepository.countByOrganizationId(orgId),
                roomRepository.sumCallSecondsByOrganization(orgId),
                participantSessionRepository.countDistinctParticipantsByOrganization(orgId));
    }

    private static org.springframework.data.jpa.domain.Specification<Room> historySpec(
            UUID organizationId, RoomHistoryQuery query) {
        return (root, cq, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> where = new java.util.ArrayList<>();
            where.add(cb.equal(root.get("organizationId"), organizationId));
            if (query.status() != null) {
                where.add(cb.equal(root.get("status"), query.status()));
            }
            if (query.roomProfileId() != null) {
                where.add(cb.equal(root.get("roomProfileId"), query.roomProfileId()));
            }
            if (query.createdFrom() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("createdAt"), query.createdFrom()));
            }
            if (query.createdTo() != null) {
                where.add(cb.lessThan(root.get("createdAt"), query.createdTo()));
            }
            return cb.and(where.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    /** Resumo operacional de um Profile (Sprint 6 §39). Tenant validado pelo chamador. */
    @Transactional(readOnly = true)
    public com.videoplatform.roomprofile.dto.RoomProfileSummaryResponse profileSummary(UUID roomProfileId) {
        return new com.videoplatform.roomprofile.dto.RoomProfileSummaryResponse(
                roomProfileId,
                roomRepository.countByRoomProfileId(roomProfileId),
                roomRepository.sumCallSecondsByProfile(roomProfileId),
                participantSessionRepository.countSessionsByProfile(roomProfileId));
    }

    /** Log de acesso a informacoes da sala (Sprint 6 §34 / Sprint 7 §34). Um evento por consulta. */
    public void logView(String event, Room room, OrganizationContext ctx) {
        log.atInfo()
                .addKeyValue("event", event)
                .addKeyValue("roomId", room.getRoomId())
                .addKeyValue("organizationId", room.getOrganizationId())
                .addKeyValue("roomProfileId", room.getRoomProfileId())
                .addKeyValue("userId", ctx.userId())
                .setMessage("room information viewed")
                .log();
    }

    /** Primeiro participante entrou: WAITING -> ACTIVE. Idempotente. */
    @Transactional
    public void markActive(String roomId, Instant at) {
        withRoom(roomId, room -> {
            RoomStatus before = room.getStatus();
            room.markActive(at);
            if (before != room.getStatus()) {
                log.atInfo()
                        .addKeyValue("event", LogEvents.ROOM_STARTED)
                        .addKeyValue("roomId", roomId)
                        .setMessage("room started")
                        .log();
            }
        });
    }

    /** Sala terminou: -> ENDED. Idempotente. No-op se ja e' terminal. */
    @Transactional
    public void markEnded(String roomId, Instant at) {
        withRoom(roomId, room -> {
            RoomStatus before = room.getStatus();
            room.markEnded(at);
            if (before != room.getStatus()) {
                log.atInfo()
                        .addKeyValue("event", LogEvents.ROOM_ENDED)
                        .addKeyValue("roomId", roomId)
                        .setMessage("room ended")
                        .log();
            }
        });
    }

    /**
     * Varredura de expiracao (scheduler): move para EXPIRED as salas WAITING/ACTIVE
     * com {@code expires_at} vencido. Idempotente. Retorna quantas transicionaram.
     */
    @Transactional
    public int expireDueRooms(Instant now) {
        List<Room> due = roomRepository.findByStatusInAndExpiresAtBefore(
                EnumSet.of(RoomStatus.WAITING, RoomStatus.ACTIVE), now);
        int expired = 0;
        for (Room room : due) {
            RoomStatus before = room.getStatus();
            room.markExpired(now);
            if (before != room.getStatus()) {
                logExpired(room);
                expired++;
            }
        }
        return expired;
    }

    private void logExpired(Room room) {
        log.atInfo()
                .addKeyValue("event", LogEvents.ROOM_EXPIRED)
                .addKeyValue("roomId", room.getRoomId())
                .addKeyValue("organizationId", room.getOrganizationId())
                .addKeyValue("roomProfileId", room.getRoomProfileId())
                .addKeyValue("ownerId", room.getOwnerId())
                .addKeyValue("expiresAt", room.getExpiresAt().toString())
                .setMessage("room expired")
                .log();
    }

    private Room findOrThrow(String roomId) {
        return roomRepository.findByRoomId(roomId)
                .orElseThrow(() -> {
                    log.atWarn()
                            .addKeyValue("event", LogEvents.ROOM_NOT_FOUND)
                            .addKeyValue("roomId", roomId)
                            .setMessage("room not found")
                            .log();
                    return new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "Room not found.");
                });
    }

    private void withRoom(String roomId, java.util.function.Consumer<Room> action) {
        Optional<Room> found = roomRepository.findByRoomId(roomId);
        if (found.isEmpty()) {
            log.atWarn()
                    .addKeyValue("event", LogEvents.ROOM_NOT_FOUND)
                    .addKeyValue("roomId", roomId)
                    .setMessage("room event for unknown room, ignoring")
                    .log();
            return;
        }
        action.accept(found.get());
    }

    private String generateUniqueRoomId() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "room-" + randomToken(8);
            if (!roomRepository.existsByRoomId(candidate)) {
                return candidate;
            }
        }
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Nao foi possivel gerar um identificador de sala.");
    }

    private static String randomToken(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
        }
        return sb.toString();
    }
}
