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
public class ReviewImageStorageService {

    private final Path rootDir;

    public ReviewImageStorageService(@Value("${app.upload.reviews-dir:uploads/reviews}") String uploadDir) {
        this.rootDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public List<String> store(Long reviewId, List<MultipartFile> files) {
        ensureDirExists(rootDir);
        List<String> urls = new ArrayList<>();
        if (files == null || files.isEmpty()) {
            return urls;
        }
        if (reviewId == null || reviewId <= 0) {
            throw new IllegalArgumentException("Некорректный идентификатор отзыва для загрузки изображений");
        }
        Path reviewDir = rootDir.resolve(String.valueOf(reviewId));
        ensureDirExists(reviewDir);
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String ext = resolveAndValidateExt(file.getOriginalFilename());
            String filename = UUID.randomUUID() + ext;
            Path target = reviewDir.resolve(filename);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new IllegalArgumentException("Не удалось сохранить фото отзыва: " + file.getOriginalFilename(), e);
            }
            urls.add("/uploads/reviews/" + reviewId + "/" + filename);
        }
        return urls;
    }

    private static void ensureDirExists(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось создать директорию для фото отзывов: " + dir, e);
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
