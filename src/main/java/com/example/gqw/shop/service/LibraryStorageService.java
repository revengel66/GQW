package com.example.gqw.shop.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LibraryStorageService {

    public record LibraryFile(String name, String url, long size) {
    }

    private final Path rootDir;

    public LibraryStorageService(@Value("${app.upload.library-dir:uploads/library}") String uploadDir) {
        this.rootDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public List<String> store(List<MultipartFile> files) {
        ensureDirExists();
        List<String> urls = new ArrayList<>();
        if (files == null) {
            return urls;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String ext = resolveAndValidateExt(file.getOriginalFilename());
            String filename = UUID.randomUUID() + ext;
            Path target = rootDir.resolve(filename);
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new IllegalArgumentException("Не удалось сохранить файл: " + file.getOriginalFilename(), e);
            }
            urls.add("/uploads/library/" + filename);
        }
        return urls;
    }

    public List<LibraryFile> listFiles() {
        ensureDirExists();
        List<LibraryFile> files = new ArrayList<>();
        try (var stream = Files.list(rootDir)) {
            stream.filter(Files::isRegularFile)
                .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .forEach(path -> {
                    try {
                        files.add(new LibraryFile(path.getFileName().toString(), "/uploads/library/" + path.getFileName(), Files.size(path)));
                    } catch (IOException ignored) {
                        // skip corrupted entry
                    }
                });
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать каталог библиотеки изображений", e);
        }
        return files;
    }

    private void ensureDirExists() {
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось создать директорию библиотеки изображений: " + rootDir, e);
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
