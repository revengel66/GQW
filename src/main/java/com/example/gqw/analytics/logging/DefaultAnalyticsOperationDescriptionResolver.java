package com.example.gqw.analytics.logging;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class DefaultAnalyticsOperationDescriptionResolver implements AnalyticsOperationDescriptionResolver {

    @Override
    public String resolve(String className, String methodName, String layer) {
        return genericOperationDescription(methodName, layer);
    }

    private static String genericOperationDescription(String methodName, String layer) {
        String lower = methodName.toLowerCase();
        if (lower.startsWith("add") || lower.startsWith("create") || lower.startsWith("save")) {
            return "создание/добавление данных";
        }
        if (lower.startsWith("update") || lower.startsWith("edit")) {
            return "обновление данных";
        }
        if (lower.startsWith("remove") || lower.startsWith("delete")) {
            return "удаление данных";
        }
        if (lower.startsWith("find") || lower.startsWith("get") || lower.startsWith("list") || lower.startsWith("items")) {
            return "получение данных";
        }
        if (lower.startsWith("count")) {
            return "подсчёт данных";
        }
        if (lower.startsWith("merge")) {
            return "объединение данных";
        }
        return switch (layer) {
            case "CONTROLLER" -> "обработка входящего запроса";
            case "SERVICE" -> "выполнение бизнес-операции";
            case "REPOSITORY" -> "выполнение операции доступа к данным";
            default -> "выполнение прикладной операции";
        };
    }
}
