package com.example.gqw.analytics.aop;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;

@Retention(RetentionPolicy.RUNTIME)
@Target(METHOD)
public @interface TrackAnalyticsEvent {

    String code();

    String codeExpression() default "";

    String entityType() default "";

    String entityId() default "";

    TrackAnalyticsAttribute[] attributes() default {};

    TrackAnalyticsMetric[] metrics() default {};

    boolean trackPayloadSize() default true;

    String operationDescription() default "";
}
