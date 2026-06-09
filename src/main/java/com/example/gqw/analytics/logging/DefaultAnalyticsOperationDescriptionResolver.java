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
            return "create or add data";
        }
        if (lower.startsWith("update") || lower.startsWith("edit")) {
            return "update data";
        }
        if (lower.startsWith("remove") || lower.startsWith("delete")) {
            return "delete data";
        }
        if (lower.startsWith("find") || lower.startsWith("get") || lower.startsWith("list") || lower.startsWith("items")) {
            return "read data";
        }
        if (lower.startsWith("count")) {
            return "count data";
        }
        if (lower.startsWith("merge")) {
            return "merge data";
        }
        return switch (layer) {
            case "CONTROLLER" -> "handle incoming request";
            case "SERVICE" -> "execute business operation";
            case "REPOSITORY" -> "execute data access operation";
            default -> "execute application operation";
        };
    }
}
