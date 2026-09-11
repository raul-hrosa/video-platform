package com.videoplatform.auth.dto;

import com.videoplatform.auth.User;

import java.util.UUID;

/** Usado por /register e /me. Nunca inclui senha/hash. */
public record UserResponse(UUID id, String name, String email) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }
}
