package com.videoplatform.participant;

/** Natureza da identidade de um participante numa sala (Sprint 9 §10). */
public enum ParticipantKind {
    /** Usuario autenticado — identity {@code user:{uuid}}. */
    USER,
    /** Visitante sem conta pelo link — identity {@code guest:{uuid}}. */
    GUEST
}
