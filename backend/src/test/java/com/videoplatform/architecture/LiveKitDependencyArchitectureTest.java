package com.videoplatform.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LiveKitDependencyArchitectureTest {

    @Test
    void liveKitSdkImportsStayInLiveKitInfrastructure() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        List<String> violations;
        try (var files = Files.walk(sourceRoot)) {
            violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().replace('\\', '/').contains("/livekit/"))
                    .filter(path -> !path.toString().replace('\\', '/').contains("/provider/livekit/"))
                    .filter(path -> {
                        try {
                            String source = Files.readString(path);
                            return source.contains("import io.livekit.")
                                    || source.contains("import livekit.");
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    })
                    .map(Path::toString)
                    .toList();
        }

        assertThat(violations).isEmpty();
    }
}
