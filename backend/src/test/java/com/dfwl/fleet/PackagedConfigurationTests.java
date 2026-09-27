package com.dfwl.fleet;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PackagedConfigurationTests {
    @Test
    void classpathConfigurationOnlyReferencesExternalSecrets() throws Exception {
        try (var stream = new ClassPathResource("application.yml").getInputStream()) {
            String config = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(config).contains("password: ${FLEET_DB_PASSWORD}");
            assertThat(config).contains("secret-id: ${FLEET_OCR_TENCENT_SECRET_ID:}");
            assertThat(config).contains("secret-key: ${FLEET_OCR_TENCENT_SECRET_KEY:}");
            assertThat(config).doesNotContain("jdbc:mysql://localhost");
        }
    }
}
