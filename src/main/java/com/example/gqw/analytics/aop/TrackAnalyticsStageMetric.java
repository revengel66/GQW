package com.example.gqw.analytics.aop;

import static java.lang.annotation.ElementType.METHOD;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(METHOD)
public @interface TrackAnalyticsStageMetric {

    String code() default "";

    String value() default "";

    String unit() default "";

    boolean required() default false;

    TrackAnalyticsMetric[] metrics() default {};
}
