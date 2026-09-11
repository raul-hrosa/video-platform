package com.videoplatform.roomprofile;

import com.videoplatform.organization.OrganizationContext;
import com.videoplatform.room.RoomService;
import com.videoplatform.room.dto.RoomResponse;
import com.videoplatform.roomprofile.dto.CreateRoomProfileRequest;
import com.videoplatform.roomprofile.dto.RoomProfileListResponse;
import com.videoplatform.roomprofile.dto.RoomProfileResponse;
import com.videoplatform.roomprofile.dto.UpdateRoomProfileRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/room-profiles")
public class RoomProfileController {

    private final RoomProfileService roomProfileService;
    private final RoomService roomService;

    public RoomProfileController(RoomProfileService roomProfileService, RoomService roomService) {
        this.roomProfileService = roomProfileService;
        this.roomService = roomService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomProfileResponse create(OrganizationContext ctx,
                                      @Valid @RequestBody CreateRoomProfileRequest request) {
        return RoomProfileResponse.from(roomProfileService.create(
                ctx, request.name(), request.durationMinutes(), request.type()));
    }

    @GetMapping
    public RoomProfileListResponse list(OrganizationContext ctx) {
        return RoomProfileListResponse.of(roomProfileService.list(ctx));
    }

    @GetMapping("/{profileId}")
    public RoomProfileResponse get(OrganizationContext ctx, @PathVariable UUID profileId) {
        return RoomProfileResponse.from(roomProfileService.getInOrg(profileId, ctx));
    }

    /** Resumo operacional do Profile: salas realizadas, tempo total, participacoes (Sprint 6 §39). */
    @GetMapping("/{profileId}/summary")
    public com.videoplatform.roomprofile.dto.RoomProfileSummaryResponse summary(
            OrganizationContext ctx, @PathVariable UUID profileId) {
        RoomProfile profile = roomProfileService.getInOrg(profileId, ctx);
        return roomService.profileSummary(profile.getId());
    }

    @PutMapping("/{profileId}")
    public RoomProfileResponse update(OrganizationContext ctx, @PathVariable UUID profileId,
                                      @Valid @RequestBody UpdateRoomProfileRequest request) {
        return RoomProfileResponse.from(roomProfileService.update(
                profileId, ctx, request.name(), request.durationMinutes(), request.type()));
    }

    @DeleteMapping("/{profileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(OrganizationContext ctx, @PathVariable UUID profileId) {
        roomProfileService.softDelete(profileId, ctx);
    }

    /**
     * Cria uma Room concreta a partir do Profile (Sprint 5 §16). A Room herda a
     * Organization do Profile; o {@code owner} e' quem chamou (Sprint 7 §14).
     */
    @PostMapping("/{profileId}/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse createRoom(OrganizationContext ctx, @PathVariable UUID profileId) {
        RoomProfile profile = roomProfileService.getInOrg(profileId, ctx);
        return RoomResponse.from(roomService.createFromProfile(profile, ctx));
    }
}
