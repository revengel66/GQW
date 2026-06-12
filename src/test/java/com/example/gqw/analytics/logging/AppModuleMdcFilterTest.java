package com.example.gqw.analytics.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.gqw.analytics.support.AnalyticsTraceContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AppModuleMdcFilterTest {

    @Test
    void routesAdminAndAnalyticsPathsToAdminModule() {
        assertEquals("ADMIN", AppModuleMdcFilter.resolveModuleFromPath("/admin/products"));
        assertEquals("ADMIN", AppModuleMdcFilter.resolveModuleFromPath("/analytics"));
        assertEquals("ADMIN", AppModuleMdcFilter.resolveModuleFromPath("/analytics-admin/api/events"));
    }

    @Test
    void leavesShopPathsForDefaultModuleFallback() {
        assertNull(AppModuleMdcFilter.resolveModuleFromPath("/catalog"));
        assertNull(AppModuleMdcFilter.resolveModuleFromPath("/"));
    }

    @Test
    void generatesTraceIdAndExposesItToRequestAndResponse() throws Exception {
        AppModuleMdcFilter filter = new AppModuleMdcFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/catalog");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String traceId = response.getHeader(AnalyticsTraceContext.TRACE_ID_HEADER);
        assertNotNull(traceId);
        UUID.fromString(traceId);
        assertEquals(traceId, request.getAttribute(AnalyticsTraceContext.TRACE_ID_REQUEST_ATTRIBUTE));
    }

    @Test
    void preservesIncomingTraceId() throws Exception {
        AppModuleMdcFilter filter = new AppModuleMdcFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/catalog");
        request.addHeader(AnalyticsTraceContext.TRACE_ID_HEADER, "external-trace");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("external-trace", response.getHeader(AnalyticsTraceContext.TRACE_ID_HEADER));
        assertEquals(
            "external-trace",
            request.getAttribute(AnalyticsTraceContext.TRACE_ID_REQUEST_ATTRIBUTE)
        );
    }
}
