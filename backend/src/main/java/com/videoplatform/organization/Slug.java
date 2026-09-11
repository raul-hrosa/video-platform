package com.videoplatform.organization;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Gera slugs estaveis a partir de um nome. Mesma ideia do backfill V12, mas para
 * Organizations criadas em runtime (registro de usuario).
 */
public final class Slug {

    private Slug() {
    }

    /** Ex.: {@code "Clínica ABC"} -> {@code "clinica-abc"}. Vazio -> {@code "org"}. */
    public static String of(String raw) {
        if (raw == null) {
            return "org";
        }
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String slug = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return slug.isEmpty() ? "org" : slug;
    }
}
