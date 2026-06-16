package com.example.gqw.analytics.config;

import com.example.gqw.analytics.service.AnalyticsDataSourcePoolDiagnostics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class AnalyticsAdminWebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsAdminWebConfig.class);
    private static final long ANALYTICS_API_SLOW_LOG_MS = 3000L;

    private final AnalyticsAdminAuthInterceptor analyticsAdminAuthInterceptor;
    private final AnalyticsDataSourcePoolDiagnostics poolDiagnostics;

    public AnalyticsAdminWebConfig(
        AnalyticsAdminAuthInterceptor analyticsAdminAuthInterceptor,
        AnalyticsDataSourcePoolDiagnostics poolDiagnostics
    ) {
        this.analyticsAdminAuthInterceptor = analyticsAdminAuthInterceptor;
        this.poolDiagnostics = poolDiagnostics;
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

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> analyticsApiPerformanceFilter() {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
                long started = System.nanoTime();
                String endpoint = request.getRequestURI();
                try {
                    filterChain.doFilter(request, response);
                } catch (ServletException | IOException | RuntimeException error) {
                    poolDiagnostics.logPoolFailure(endpoint, error);
                    throw error;
                } finally {
                    long totalMs = (System.nanoTime() - started) / 1_000_000L;
                    if (totalMs > ANALYTICS_API_SLOW_LOG_MS) {
                        poolDiagnostics.logSlowEndpoint(endpoint, totalMs);
                    } else if (response.getStatus() >= 500) {
                        log.warn(
                            "Analytics endpoint failed endpoint={} status={} totalMs={} {}",
                            endpoint,
                            response.getStatus(),
                            totalMs,
                            poolDiagnostics.snapshot()
                        );
                    }
                }
            }
        });
        registration.setUrlPatterns(List.of("/analytics/api/*", "/analytics-admin/api/*"));
        registration.setName("analyticsApiPerformanceFilter");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 20);
        return registration;
    }
}
