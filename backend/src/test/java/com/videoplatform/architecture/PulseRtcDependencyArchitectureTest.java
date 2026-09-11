package com.videoplatform.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 11 §22: detalhes especificos do PulseRTC (cliente HTTP, DTOs de
 * transporte, parsing de webhook) ficam confinados em {@code provider/pulsertc/}.
 * O core/dominio nunca importa este pacote — so a fronteira de configuracao
 * ({@code VideoPlatformApplication}) o referencia para registrar as properties.
 */
class PulseRtcDependencyArchitectureTest {

    @Test
    void pulseRtcInternalsStayInProviderPackage() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        List<String> violations;
        try (var files = Files.walk(sourceRoot)) {
            violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.toString().replace('\\', '/'))
                    .filter(path -> !path.contains("/provider/pulsertc/"))
                    .filter(path -> !path.endsWith("/VideoPlatformApplication.java"))
                    .filter(path -> {
                        try {
                            return Files.readString(Path.of(path))
                                    .contains("import com.videoplatform.provider.pulsertc.");
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    })
                    .toList();
        }

        assertThat(violations).isEmpty();
    }
}
