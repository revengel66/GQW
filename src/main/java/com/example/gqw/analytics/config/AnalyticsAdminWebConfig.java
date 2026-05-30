package com.example.gqw.analytics.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AnalyticsAdminWebConfig implements WebMvcConfigurer {

    private final AnalyticsAdminAuthInterceptor analyticsAdminAuthInterceptor;

    public AnalyticsAdminWebConfig(AnalyticsAdminAuthInterceptor analyticsAdminAuthInterceptor) {
        this.analyticsAdminAuthInterceptor = analyticsAdminAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(analyticsAdminAuthInterceptor)
            .addPathPatterns("/analytics-admin/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/analytics/js/**")
            .addResourceLocations("classpath:/META-INF/gqw-analytics/static/js/");
        registry.addResourceHandler("/analytics/css/**")
            .addResourceLocations("classpath:/META-INF/gqw-analytics/static/css/");
        registry.addResourceHandler("/analytics/img/**")
            .addResourceLocations("classpath:/META-INF/gqw-analytics/static/img/");
    }
}
