package com.example.gqw.analytics.config;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;

@Configuration
public class AnalyticsUiTemplateConfig {

    @Bean
    public SpringResourceTemplateResolver analyticsTemplateResolver(ApplicationContext applicationContext) {
        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setApplicationContext(applicationContext);
        resolver.setPrefix("classpath:/META-INF/gqw-analytics/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCheckExistence(true);
        resolver.setOrder(0);
        resolver.setResolvablePatterns(Set.of("analytics/*"));
        return resolver;
    }
}

