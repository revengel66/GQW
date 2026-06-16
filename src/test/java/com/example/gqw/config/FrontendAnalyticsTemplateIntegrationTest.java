package com.example.gqw.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FrontendAnalyticsTemplateIntegrationTest {

    @Test
    void sharedScriptsFragmentInitializesFrontendAnalytics() throws IOException {
        try (var stream = getClass().getResourceAsStream("/shop/templates/fragments.html")) {
            assertTrue(stream != null, "Shared template fragments must be available");
            String template = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(template.contains("/analytics/js/analytics-web.js"));
            assertTrue(template.contains("window.AnalyticsWeb.init()"));
        }
    }
}
