package com.example.gqw.analytics.service;

import com.example.gqw.analytics.entity.EventType;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsSystemEventClassifier {

    public boolean isSystemEvent(String eventTypeCode, String eventTypeName, String requestPath, Integer statusCode) {
        String code = normalize(eventTypeCode);
        String name = text(eventTypeName);
        String path = normalizePath(requestPath);

        if (isTechnicalPath(path)) {
            return true;
        }
        if (code != null && code.startsWith("FRONTEND_")) {
            return true;
        }
        if ("HTTP_REQUEST_ERROR".equals(code) && path == null) {
            return true;
        }
        if ("HTTP_REQUEST_ERROR".equals(code) && isTechnicalPath(path)) {
            return true;
        }
        if ("HTTP_REQUEST_ERROR".equals(code) && statusCode != null && statusCode == 404 && isAssetLikePath(path)) {
            return true;
        }
        if (name.contains("http request error") && isTechnicalPath(path)) {
            return true;
        }
        return false;
    }

    public boolean isSystemEventType(EventType type) {
        if (type == null) {
            return false;
        }
        return isSystemEvent(type.getCode(), type.getName(), null, null);
    }

    public boolean isTechnicalPath(String pathRaw) {
        String path = normalizePath(pathRaw);
        if (path == null) {
            return false;
        }
        if ("/favicon.ico".equals(path) || "/robots.txt".equals(path) || "/error".equals(path)) {
            return true;
        }
        return path.startsWith("/static/")
            || path.startsWith("/css/")
            || path.startsWith("/js/")
            || path.startsWith("/images/")
            || path.startsWith("/img/")
            || path.startsWith("/webjars/")
            || path.startsWith("/actuator/");
    }

    private boolean isAssetLikePath(String path) {
        if (path == null) {
            return false;
        }
        return isTechnicalPath(path)
            || path.endsWith(".css")
            || path.endsWith(".js")
            || path.endsWith(".map")
            || path.endsWith(".ico")
            || path.endsWith(".png")
            || path.endsWith(".jpg")
            || path.endsWith(".jpeg")
            || path.endsWith(".svg")
            || path.endsWith(".gif")
            || path.endsWith(".webp")
            || path.endsWith(".woff")
            || path.endsWith(".woff2")
            || path.endsWith(".ttf");
    }

    private String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String path = value.trim().toLowerCase(Locale.ROOT);
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        return path.isBlank() ? null : path;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private String text(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
