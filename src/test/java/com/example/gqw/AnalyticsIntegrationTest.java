package com.example.gqw;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.gqw.analytics.repository.AnalyticsEventRepository;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.repository.ShopUserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AnalyticsEventRepository analyticsEventRepository;

    @Autowired
    private ShopUserRepository shopUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void analyticsApiRedirectsAnonymousUserToLogin() throws Exception {
        mockMvc.perform(get("/analytics/api/dictionaries"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("/login")));
    }

    @Test
    void analyticsApiForbidsNonAdminUser() throws Exception {
        MockHttpSession userSession = loginAs("analytics_user", false);
        mockMvc.perform(get("/analytics/api/dictionaries").session(userSession))
            .andExpect(status().isForbidden());
    }

    @Test
    void analyticsDictionariesReturnsJsonForAdmin() throws Exception {
        MockHttpSession adminSession = loginAs("analytics_admin", true);
        mockMvc.perform(get("/analytics/api/dictionaries").session(adminSession).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.modules").isArray())
            .andExpect(jsonPath("$.eventTypes").isArray())
            .andExpect(jsonPath("$.stageTypes").isArray())
            .andExpect(jsonPath("$.stageMetricTypes").isArray())
            .andExpect(jsonPath("$.eventAttributeTypes").isArray());
    }

    @Test
    void frontendIngestAcceptsPayloadAndPersistsAnalyticsEvent() throws Exception {
        long before = analyticsEventRepository.count();

        String payload = """
            {
              "events": [
                {
                  "code": "FRONTEND_JS_ERROR",
                  "pagePath": "/catalog",
                  "requestPath": "/catalog",
                  "httpMethod": "GET",
                  "traceId": "trace-integration-1",
                  "statusCode": 500,
                  "error": true,
                  "errorMessage": "JS integration error",
                  "metricsNum": {
                    "FRONTEND_HTTP_STATUS": 500
                  },
                  "metricsText": {
                    "FRONTEND_ERROR_MESSAGE": "JS integration error"
                  }
                }
              ]
            }
            """;

        mockMvc.perform(
                post("/api/analytics/frontend/ingest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
            .andExpect(status().isAccepted());

        long after = analyticsEventRepository.count();
        assertTrue(after > before, "Frontend ingest must persist at least one analytics event");
    }

    @Test
    void frontendIngestReturnsBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(
                post("/api/analytics/frontend/ingest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{")
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void analyticsEventsEndpointReturnsCreatedFrontendEvent() throws Exception {
        MockHttpSession adminSession = loginAs("analytics_admin_events", true);

        String payload = """
            {
              "events": [
                {
                  "code": "FRONTEND_JS_ERROR",
                  "pagePath": "/product/laptop",
                  "requestPath": "/product/laptop",
                  "httpMethod": "GET",
                  "traceId": "trace-integration-2",
                  "statusCode": 500,
                  "error": true,
                  "errorMessage": "Frontend crash"
                }
              ]
            }
            """;

        mockMvc.perform(
                post("/api/analytics/frontend/ingest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
            .andExpect(status().isAccepted());

        Instant now = Instant.now();
        String from = now.minus(1, ChronoUnit.DAYS).toString();
        String to = now.plus(1, ChronoUnit.HOURS).toString();

        mockMvc.perform(
                get("/analytics/api/events")
                    .session(adminSession)
                    .param("from", from)
                    .param("to", to)
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total", greaterThan(0)))
            .andExpect(content().string(containsString("FRONTEND_JS_ERROR")));
    }

    private MockHttpSession loginAs(String username, boolean admin) throws Exception {
        createUserIfMissing(username, admin);
        MvcResult result = mockMvc.perform(
                post("/login")
                    .with(csrf())
                    .param("username", username)
                    .param("password", "secret123")
            )
            .andExpect(status().is3xxRedirection())
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void createUserIfMissing(String username, boolean admin) {
        if (shopUserRepository.findByUsername(username).isPresent()) {
            return;
        }
        ShopUser user = new ShopUser();
        user.setUsername(username);
        user.setEmail(username + "@test.local");
        user.setPasswordHash(passwordEncoder.encode("secret123"));
        user.setFullName("Integration User " + username);
        user.setIsAdmin(admin);
        user.setIsEnabled(true);
        shopUserRepository.save(user);
    }
}
