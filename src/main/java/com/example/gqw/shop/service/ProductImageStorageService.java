package com.example.gqw.shop.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductImageStorageService {

    private final Path rootDir;

    public ProductImageStorageService(@Value("${app.upload.products-dir:uploads/products}") String uploadDir) {
        this.rootDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public List<String> store(Long productId, List<MultipartFile> files) {
        ensureDirExists();
        List<String> urls = new ArrayList<>();
        if (files == null) {
            return urls;
        }
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Некорректный идентификатор товара для загрузки изображения");
        }
        Path productDir = rootDir.resolve(String.valueOf(productId));
        ensureDirExists(productDir);
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String ext = resolveAndValidateExt(file.getOriginalFilename());
            String filename = UUID.randomUUID() + ext;
            Path target = productDir.resolve(filename);
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new IllegalArgumentException("Не удалось сохранить изображение: " + file.getOriginalFilename(), e);
            }
            urls.add("/uploads/products/" + productId + "/" + filename);
        }
        return urls;
    }

    private void ensureDirExists() {
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось создать директорию для изображений: " + rootDir, e);
        }
    }

    private static void ensureDirExists(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось создать директорию для изображений: " + path, e);
        }
    }

    private static String resolveAndValidateExt(String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("Файл без имени");
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return ".jpg";
        }
        if (lower.endsWith(".png")) {
            return ".png";
        }
        if (lower.endsWith(".webp")) {
            return ".webp";
        }
        throw new IllegalArgumentException("Поддерживаются только изображения .jpg, .jpeg, .png, .webp");
    }
}
