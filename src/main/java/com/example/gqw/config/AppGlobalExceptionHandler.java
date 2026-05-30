package com.example.gqw.config;

import com.example.gqw.analytics.service.AnalyticsHttpErrorTrackingService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice(annotations = Controller.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AppGlobalExceptionHandler {

    private final AnalyticsHttpErrorTrackingService analyticsHttpErrorTrackingService;

    public AppGlobalExceptionHandler(AnalyticsHttpErrorTrackingService analyticsHttpErrorTrackingService) {
        this.analyticsHttpErrorTrackingService = analyticsHttpErrorTrackingService;
    }

    @ExceptionHandler({
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        BindException.class,
        HttpMessageNotReadableException.class,
        ServletRequestBindingException.class,
        IllegalArgumentException.class,
        IllegalStateException.class
    })
    public Object handleBadRequest(Exception ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, ex, request);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public Object handleNotFound(Exception ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex, request);
    }

    @ExceptionHandler(Throwable.class)
    public Object handleAny(Throwable ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex, request);
    }

    private Object buildErrorResponse(HttpStatus status, Throwable ex, HttpServletRequest request) {
        if (request != null && ex != null) {
            request.setAttribute(AnalyticsHttpErrorTrackingService.ERROR_THROWABLE_REQUEST_ATTRIBUTE, ex);
            analyticsHttpErrorTrackingService.trackIfMissing(request, status.value(), ex);
        }

        String requestPath = request != null ? request.getRequestURI() : "";
        String title = AppErrorSupport.titleForStatus(status.value());
        String message = AppErrorSupport.userMessage(status.value(), ex, ex != null ? ex.getMessage() : null);

        if (AppErrorSupport.isAjax(request)) {
            return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_PLAIN)
                .body(message);
        }

        ModelAndView modelAndView = new ModelAndView(AppErrorSupport.resolveViewName(requestPath));
        modelAndView.setStatus(status);
        modelAndView.addObject("errorStatus", status.value());
        modelAndView.addObject("errorTitle", title);
        modelAndView.addObject("errorMessage", message);
        modelAndView.addObject("errorPath", requestPath);
        modelAndView.addObject("errorTimestamp", OffsetDateTime.now().toString());
        return modelAndView;
    }
}
