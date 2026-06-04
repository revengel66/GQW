package com.example.gqw.analytics.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.filter.OncePerRequestFilter;

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

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> analyticsStaticUtf8Filter() {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
                String path = request.getRequestURI();
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                if (path.endsWith(".js")) {
                    response.setContentType("text/javascript;charset=UTF-8");
                } else if (path.endsWith(".css")) {
                    response.setContentType("text/css;charset=UTF-8");
                }
                filterChain.doFilter(request, response);
            }
        });
        registration.setUrlPatterns(List.of("/analytics/js/*", "/analytics/css/*"));
        registration.setName("analyticsStaticUtf8Filter");
        return registration;
    }
}
