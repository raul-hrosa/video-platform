package com.videoplatform.organization;

/**
 * Papel de um usuario dentro de uma Organization (Sprint 7 §6). A matriz de
 * permissoes vive centralizada em {@link OrgPolicy} — nao espalhada pelos
 * controllers (§25).
 */
public enum OrgRole {
    OWNER,
    ADMIN,
    MEMBER;

    public boolean atLeast(OrgRole other) {
        return ordinal() <= other.ordinal();
    }
}
