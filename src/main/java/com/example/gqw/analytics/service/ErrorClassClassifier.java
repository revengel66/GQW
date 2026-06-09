package com.example.gqw.analytics.service;

import com.example.gqw.analytics.entity.AnalyticsEvent;
import jakarta.validation.ConstraintViolationException;
import java.util.Locale;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

public final class ErrorClassClassifier {

    public static final String NONE = "NONE";
    public static final String VALIDATION = "VALIDATION";
    public static final String BUSINESS = "BUSINESS";
    public static final String SYSTEM = "SYSTEM";

    private ErrorClassClassifier() {
    }

    public static String classifyFromEvent(AnalyticsEvent event) {
        if (event == null || !Boolean.TRUE.equals(event.getIsError())) {
            return NONE;
        }
        return classify(event.getStatusCode(), event.getErrorMessage(), null);
    }

    public static String classify(Integer statusCode, Throwable throwable) {
        return classify(statusCode, throwable != null ? throwable.getMessage() : null, throwable);
    }

    public static String classify(Integer statusCode, String errorMessage, Throwable throwable) {
        if (throwable != null) {
            if (isValidationThrowable(throwable)) {
                return VALIDATION;
            }
            if (throwable instanceof ResponseStatusException ex) {
                return classify(ex.getStatusCode().value(), ex.getReason(), null);
            }
        }

        int status = statusCode == null ? 0 : statusCode;
        if (status >= 500) {
            return SYSTEM;
        }
        if (status == 400) {
            return looksLikeValidation(errorMessage) ? VALIDATION : BUSINESS;
        }
        if (status >= 401 && status <= 499) {
            return BUSINESS;
        }
        if (looksLikeValidation(errorMessage)) {
            return VALIDATION;
        }
        return SYSTEM;
    }

    public static String normalizeFilterValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case NONE, VALIDATION, BUSINESS, SYSTEM -> normalized;
            default -> null;
        };
    }

    private static boolean isValidationThrowable(Throwable throwable) {
        return throwable instanceof MissingServletRequestParameterException
            || throwable instanceof MethodArgumentTypeMismatchException
            || throwable instanceof MethodArgumentNotValidException
            || throwable instanceof BindException
            || throwable instanceof ConstraintViolationException
            || throwable instanceof HttpMessageNotReadableException
            || throwable instanceof ServletRequestBindingException
            || throwable instanceof IllegalArgumentException;
    }

    private static boolean looksLikeValidation(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return false;
        }
        String message = errorMessage.toLowerCase(Locale.ROOT);
        return message.contains("required request parameter")
            || message.contains("validation")
            || message.contains("bind")
            || message.contains("failed to convert")
            || message.contains("cannot be null")
            || message.contains("must not")
            || message.contains("некоррект")
            || message.contains("не заполн")
            || message.contains("обязатель")
            || message.contains("валидац");
    }
}
