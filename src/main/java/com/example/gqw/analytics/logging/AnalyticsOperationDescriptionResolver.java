package com.example.gqw.analytics.logging;

public interface AnalyticsOperationDescriptionResolver {

    String resolve(String className, String methodName, String layer);
}
