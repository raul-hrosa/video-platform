package com.videoplatform.organization.dto;

import java.util.List;

/** Envelope {@code { "content": [...] }} — mesmo shape das demais listagens. */
public record MemberListResponse(List<MemberResponse> content) {
}
