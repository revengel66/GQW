package com.example.gqw.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;

final class AppErrorSupport {

    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
        Map.entry("productName", "Название товара"),
        Map.entry("productSlug", "Slug товара"),
        Map.entry("productPrice", "Цена товара"),
        Map.entry("productDescription", "Описание товара"),
        Map.entry("productShortDescription", "Краткое описание товара"),
        Map.entry("categoryIds", "Категории товара")
    );

    private AppErrorSupport() {
    }

    static boolean isAjax(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String xrw = request.getHeader("X-Requested-With");
        if ("XMLHttpRequest".equalsIgnoreCase(xrw)) {
            return true;
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.toLowerCase().contains("application/json");
    }

    static String resolveViewName(String requestPath) {
        if (requestPath != null) {
            if (requestPath.startsWith("/admin") || requestPath.startsWith("/analytics-admin")) {
                return "admin/error";
            }
        }
        return "shop/error";
    }

    static String titleForStatus(int statusCode) {
        if (statusCode == 400) {
            return "Некорректный запрос";
        }
        if (statusCode == 401) {
            return "Требуется авторизация";
        }
        if (statusCode == 403) {
            return "Доступ запрещён";
        }
        if (statusCode == 404) {
            return "Страница не найдена";
        }
        if (statusCode >= 500) {
            return "Внутренняя ошибка сервера";
        }
        HttpStatus status = HttpStatus.resolve(statusCode);
        return status != null ? status.getReasonPhrase() : "Ошибка";
    }

    static String userMessage(int statusCode, Throwable ex, String fallbackMessage) {
        if (ex instanceof MissingServletRequestParameterException missing) {
            String paramName = missing.getParameterName();
            String fieldLabel = FIELD_LABELS.getOrDefault(paramName, paramName);
            return "Не заполнено обязательное поле: " + fieldLabel + ". Проверьте форму и повторите сохранение.";
        }
        if (statusCode == 400) {
            return "Запрос содержит некорректные или неполные данные. Проверьте заполнение формы и повторите действие.";
        }
        if (statusCode == 401) {
            return "Необходимо войти в систему, чтобы выполнить это действие.";
        }
        if (statusCode == 403) {
            return "У вас недостаточно прав для выполнения этого действия.";
        }
        if (statusCode == 404) {
            return "Запрошенная страница или ресурс не найдены.";
        }
        if (statusCode >= 500) {
            return "На сервере произошла ошибка при обработке запроса. Попробуйте повторить действие позже.";
        }
        if (fallbackMessage != null && !fallbackMessage.isBlank()) {
            return fallbackMessage;
        }
        return "Произошла ошибка при выполнении запроса.";
    }
}
