package com.videoplatform.organization;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugTest {

    @Test
    void lowercasesAndHyphenates() {
        assertThat(Slug.of("Clinica ABC")).isEqualTo("clinica-abc");
    }

    @Test
    void stripsAccentsAndPunctuation() {
        assertThat(Slug.of("Clínica ABC — Saúde!")).isEqualTo("clinica-abc-saude");
    }

    @Test
    void collapsesAndTrimsHyphens() {
        assertThat(Slug.of("  --Joao   da   Silva--  ")).isEqualTo("joao-da-silva");
    }

    @Test
    void fallsBackToOrgWhenEmpty() {
        assertThat(Slug.of("!!!")).isEqualTo("org");
        assertThat(Slug.of("")).isEqualTo("org");
        assertThat(Slug.of(null)).isEqualTo("org");
    }
}
