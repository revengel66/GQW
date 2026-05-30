package com.example.gqw.analytics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.gqw.analytics.entity.AnalyticsEvent;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.MissingServletRequestParameterException;

class ErrorClassClassifierTest {

    @Test
    void classifyFromEventReturnsNoneWhenEventIsNotError() {
        AnalyticsEvent event = new AnalyticsEvent();
        event.setIsError(false);
        event.setStatusCode(500);
        event.setErrorMessage("validation failed");

        assertEquals(ErrorClassClassifier.NONE, ErrorClassClassifier.classifyFromEvent(event));
    }

    @Test
    void classifyReturnsValidationForValidationThrowable() {
        MissingServletRequestParameterException exception =
            new MissingServletRequestParameterException("id", "String");

        String actual = ErrorClassClassifier.classify(400, exception);

        assertEquals(ErrorClassClassifier.VALIDATION, actual);
    }

    @Test
    void classifyReturnsSystemForStatus500AndHigher() {
        assertEquals(ErrorClassClassifier.SYSTEM, ErrorClassClassifier.classify(503, "boom", null));
    }

    @Test
    void classifyReturnsBusinessFor4xxWithoutValidationMessage() {
        assertEquals(ErrorClassClassifier.BUSINESS, ErrorClassClassifier.classify(404, "not found", null));
    }

    @Test
    void classifyReturnsValidationWhenMessageLooksLikeValidation() {
        assertEquals(
            ErrorClassClassifier.VALIDATION,
            ErrorClassClassifier.classify(400, "Не заполнено обязательное поле", null)
        );
    }

    @Test
    void normalizeFilterValueReturnsUppercaseKnownValue() {
        assertEquals(ErrorClassClassifier.VALIDATION, ErrorClassClassifier.normalizeFilterValue(" validation "));
    }

    @Test
    void normalizeFilterValueReturnsNullForUnknownValue() {
        assertNull(ErrorClassClassifier.normalizeFilterValue("random"));
    }
}

