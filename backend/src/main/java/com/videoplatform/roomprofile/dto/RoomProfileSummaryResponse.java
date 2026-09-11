package com.videoplatform.roomprofile.dto;

import java.util.UUID;

/**
 * Resumo operacional de um Room Profile (Sprint 6 §39): quantas salas ele já
 * gerou, tempo total em chamadas e nº de participações. Calculado com queries
 * agregadas — sem varrer rooms no frontend.
 */
public record RoomProfileSummaryResponse(
        UUID roomProfileId,
        long roomsTotal,
        long totalCallSeconds,
        long participations
) {
}
