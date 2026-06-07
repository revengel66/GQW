package com.example.gqw.analytics.aop;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({TYPE, METHOD})
public @interface TrackAnalyticsLayer {

    String code();

    String operation() default "";

    boolean enabled() default true;
}
