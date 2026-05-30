package com.example.gqw.config;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;

@Configuration
public class ShopAdminUiConfig implements WebMvcConfigurer {

    @Bean
    public SpringResourceTemplateResolver shopTemplateResolver(ApplicationContext applicationContext) {
        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setApplicationContext(applicationContext);
        resolver.setPrefix("classpath:/shop/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCheckExistence(true);
        resolver.setOrder(1);
        resolver.setResolvablePatterns(Set.of("shop/*", "fragments"));
        return resolver;
    }

    @Bean
    public SpringResourceTemplateResolver adminTemplateResolver(ApplicationContext applicationContext) {
        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setApplicationContext(applicationContext);
        resolver.setPrefix("classpath:/admin/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCheckExistence(true);
        resolver.setOrder(2);
        resolver.setResolvablePatterns(Set.of("admin/*"));
        return resolver;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/shop/**")
            .addResourceLocations("classpath:/shop/static/");
        registry.addResourceHandler("/admin/**")
            .addResourceLocations("classpath:/admin/static/");
        registry.addResourceHandler("/img/**")
            .addResourceLocations("classpath:/shop/static/img/");
    }
}

