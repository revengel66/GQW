package com.example.gqw.shop.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CategoryImageStorageService {

    private final Path rootDir;

    public CategoryImageStorageService(@Value("${app.upload.categories-dir:uploads/categories}") String uploadDir) {
        this.rootDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public String store(Long categoryId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (categoryId == null || categoryId <= 0) {
            throw new IllegalArgumentException("Некорректный идентификатор категории для загрузки изображения");
        }
        ensureDirExists(rootDir);
        Path categoryDir = rootDir.resolve(String.valueOf(categoryId));
        ensureDirExists(categoryDir);

        String ext = resolveAndValidateExt(file.getOriginalFilename());
        String filename = UUID.randomUUID() + ext;
        Path target = categoryDir.resolve(filename);
        try (InputStream is = file.getInputStream()) {
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalArgumentException("Не удалось сохранить изображение категории: " + file.getOriginalFilename(), e);
        }
        return "/uploads/categories/" + categoryId + "/" + filename;
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
