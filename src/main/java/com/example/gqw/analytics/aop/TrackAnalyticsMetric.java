package com.example.gqw.analytics.aop;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface TrackAnalyticsMetric {

    String code();

    String value();

    String unit() default "";

    boolean required() default false;
}
