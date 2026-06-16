package com.example.gqw.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private final String productsUploadDir;
    private final String categoriesUploadDir;
    private final String libraryUploadDir;
    private final String reviewsUploadDir;

    public StaticResourceConfig(
        @Value("${app.upload.products-dir:uploads/products}") String productsUploadDir,
        @Value("${app.upload.categories-dir:uploads/categories}") String categoriesUploadDir,
        @Value("${app.upload.library-dir:uploads/library}") String libraryUploadDir,
        @Value("${app.upload.reviews-dir:uploads/reviews}") String reviewsUploadDir
    ) {
        this.productsUploadDir = productsUploadDir;
        this.categoriesUploadDir = categoriesUploadDir;
        this.libraryUploadDir = libraryUploadDir;
        this.reviewsUploadDir = reviewsUploadDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String productsLocation = Path.of(productsUploadDir).toAbsolutePath().normalize().toUri().toString();
        String categoriesLocation = Path.of(categoriesUploadDir).toAbsolutePath().normalize().toUri().toString();
        String libraryLocation = Path.of(libraryUploadDir).toAbsolutePath().normalize().toUri().toString();
        String reviewsLocation = Path.of(reviewsUploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/products/**")
            .addResourceLocations(productsLocation);
        registry.addResourceHandler("/uploads/categories/**")
            .addResourceLocations(categoriesLocation);
        registry.addResourceHandler("/uploads/library/**")
            .addResourceLocations(libraryLocation);
        registry.addResourceHandler("/uploads/reviews/**")
            .addResourceLocations(reviewsLocation);
    }
}
