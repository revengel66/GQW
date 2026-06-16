package com.example.gqw.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModuleLoggingConfigurationTest {

    @Test
    void logbackRoutesRecordsToModuleFilesUsingAppModuleMdc() throws IOException {
        try (var stream = getClass().getResourceAsStream("/logback-spring.xml")) {
            assertTrue(stream != null, "logback-spring.xml must be available");
            String config = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(config.contains("ch.qos.logback.classic.sift.SiftingAppender"));
            assertTrue(config.contains("<key>appModule</key>"));
            assertTrue(config.contains("${MODULE_LOG_DIR}/${appModule}.log"));
        }
    }
}
