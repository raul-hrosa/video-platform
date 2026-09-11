package com.videoplatform.roomprofile.dto;

import com.videoplatform.roomprofile.RoomType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateRoomProfileRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @Min(1) @Max(480) Integer durationMinutes,
        @NotNull RoomType type
) {
}
