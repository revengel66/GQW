package com.example.gqw.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingServletRequestParameterException;

class AppErrorSupportTest {

    @Test
    void isAjaxReturnsTrueForXmlHttpRequestHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Requested-With", "XMLHttpRequest");

        assertTrue(AppErrorSupport.isAjax(request));
    }

    @Test
    void isAjaxReturnsTrueForJsonAcceptHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", "application/json");

        assertTrue(AppErrorSupport.isAjax(request));
    }

    @Test
    void isAjaxReturnsFalseForPlainHtmlRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", "text/html");

        assertFalse(AppErrorSupport.isAjax(request));
    }

    @Test
    void resolveViewNameReturnsAdminTemplateForAdminPath() {
        assertEquals("admin/error", AppErrorSupport.resolveViewName("/admin/products"));
    }

    @Test
    void resolveViewNameReturnsAdminTemplateForAnalyticsAdminPath() {
        assertEquals("admin/error", AppErrorSupport.resolveViewName("/analytics-admin/dashboard"));
        assertEquals("admin/error", AppErrorSupport.resolveViewName("/analytics/dashboard"));
    }

    @Test
    void resolveViewNameReturnsShopTemplateForOtherPaths() {
        assertEquals("shop/error", AppErrorSupport.resolveViewName("/catalog/laptops"));
    }

    @Test
    void userMessageContainsFriendlyFieldLabelForMissingParameter() {
        MissingServletRequestParameterException ex =
            new MissingServletRequestParameterException("productName", "String");

        String actual = AppErrorSupport.userMessage(400, ex, null);

        assertTrue(actual.contains("Название товара"));
    }

    @Test
    void userMessageReturnsFallbackMessageForUnknownStatus() {
        String actual = AppErrorSupport.userMessage(418, null, "custom error");

        assertEquals("custom error", actual);
    }
}

