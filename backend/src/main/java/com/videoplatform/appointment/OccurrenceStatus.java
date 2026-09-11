package com.videoplatform.appointment;

/**
 * Estado de uma ocorrencia (§4). {@code SCHEDULED} e {@code CANCELLED} sao
 * persistidos; {@code WAITING/ACTIVE/ENDED/EXPIRED} sao derivados da Room
 * vinculada + relogio na montagem das respostas (evita escrita dupla
 * Room -> Occurrence). A coluna e' atualizada quando ha transicao inequivoca.
 */
public enum OccurrenceStatus {
    SCHEDULED,
    WAITING,
    ACTIVE,
    ENDED,
    EXPIRED,
    CANCELLED
}
