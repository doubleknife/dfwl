package com.dfwl.fleet;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PackagedConfigurationTests {
    @Test
    void classpathConfigurationMatchesTheSingleSourceAndReferencesExternalSecrets() throws Exception {
        try (var stream = new ClassPathResource("application.yml").getInputStream()) {
            String config = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            String source = Files.readString(Path.of("src/main/resources/application.yml"));
            assertThat(config.replace("\r\n", "\n")).isEqualTo(source.replace("\r\n", "\n"));
            assertThat(config).contains("password: ${FLEET_DB_PASSWORD:}");
            assertThat(config).contains("secret-id: ${FLEET_OCR_TENCENT_SECRET_ID:}");
            assertThat(config).contains("secret-key: ${FLEET_OCR_TENCENT_SECRET_KEY:}");
        }
    }
}
