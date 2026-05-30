package com.example.gqw.shop.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LibraryStorageService {

    private static final List<String> TOP_LEVEL_FOLDERS = List.of("library", "products", "categories", "reviews");

    private static final Map<String, String> TOP_LEVEL_FOLDER_LABELS = new LinkedHashMap<>();

    static {
        TOP_LEVEL_FOLDER_LABELS.put("library", "Библиотека");
        TOP_LEVEL_FOLDER_LABELS.put("products", "Товары");
        TOP_LEVEL_FOLDER_LABELS.put("categories", "Категории");
        TOP_LEVEL_FOLDER_LABELS.put("reviews", "Отзывы");
    }

    public record LibraryFile(
        String name,
        String url,
        long size,
        Integer width,
        Integer height,
        String folderKey
    ) {
    }

    public record LibraryFolder(
        String key,
        String label,
        int depth,
        long filesCount
    ) {
    }

    private final Path libraryRootDir;
    private final Path uploadsRootDir;

    public LibraryStorageService(@Value("${app.upload.library-dir:uploads/library}") String uploadDir) {
        this.libraryRootDir = Path.of(uploadDir).toAbsolutePath().normalize();
        this.uploadsRootDir = libraryRootDir.getParent() == null
            ? libraryRootDir
            : libraryRootDir.getParent().toAbsolutePath().normalize();
    }

    public List<String> store(List<MultipartFile> files) {
        return store("library", files);
    }

    public List<String> store(String folderKey, List<MultipartFile> files) {
        String normalizedFolderKey = normalizeFolderKey(folderKey);
        Path targetDir = resolveFolderDir(normalizedFolderKey);
        ensureDirExists(targetDir);
        List<String> urls = new ArrayList<>();
        if (files == null) {
            return urls;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String ext = resolveAndValidateExt(file.getOriginalFilename());
            String filename = buildFileName(file.getOriginalFilename(), ext);
            Path target = targetDir.resolve(filename);
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new IllegalArgumentException("Не удалось сохранить файл: " + file.getOriginalFilename(), e);
            }
            urls.add("/uploads/" + normalizedFolderKey + "/" + filename);
        }
        return urls;
    }

    public List<LibraryFile> listFiles() {
        return listFilesInFolder("library");
    }

    public List<LibraryFile> listFilesInFolder(String folderKey) {
        String normalizedFolderKey = normalizeFolderKey(folderKey);
        Path folderDir = resolveFolderDir(normalizedFolderKey);
        ensureDirExists(folderDir);
        List<LibraryFile> files = new ArrayList<>();
        try (var stream = Files.list(folderDir)) {
            stream.filter(Files::isRegularFile)
                .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .forEach(path -> {
                    try {
                        Integer width = null;
                        Integer height = null;
                        try {
                            var image = ImageIO.read(path.toFile());
                            if (image != null) {
                                width = image.getWidth();
                                height = image.getHeight();
                            }
                        } catch (IOException ignored) {
                            // image metadata is optional
                        }
                        files.add(new LibraryFile(
                            path.getFileName().toString(),
                            "/uploads/" + normalizedFolderKey + "/" + path.getFileName(),
                            Files.size(path),
                            width,
                            height,
                            normalizedFolderKey
                        ));
                    } catch (IOException ignored) {
                        // skip corrupted entry
                    }
                });
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать каталог библиотеки изображений", e);
        }
        return files;
    }

    public List<LibraryFolder> listFolders() {
        ensureTopLevelFoldersExist();
        List<LibraryFolder> folders = new ArrayList<>();
        for (String root : TOP_LEVEL_FOLDERS) {
            Path rootPath = uploadsRootDir.resolve(root);
            if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
                continue;
            }
            collectFolders(root, rootPath, 0, folders);
        }
        if (folders.isEmpty()) {
            folders.add(new LibraryFolder("library", TOP_LEVEL_FOLDER_LABELS.get("library"), 0, 0));
        }
        return folders;
    }

    public void deleteByUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IllegalArgumentException("Файл не указан");
        }
        String normalized = fileUrl.trim();
        int uploadsIndex = normalized.indexOf("/uploads/");
        if (uploadsIndex >= 0) {
            normalized = normalized.substring(uploadsIndex + "/uploads/".length());
        } else if (normalized.startsWith("uploads/")) {
            normalized = normalized.substring("uploads/".length());
        }
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Некорректный путь к файлу");
        }
        Path target = uploadsRootDir.resolve(normalized).normalize();
        if (!target.startsWith(uploadsRootDir)) {
            throw new IllegalArgumentException("Некорректный путь к файлу");
        }
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new IllegalArgumentException("Файл не найден");
        }
        try {
            Files.delete(target);
            cleanupEmptyParents(target.getParent());
        } catch (IOException e) {
            throw new IllegalArgumentException("Не удалось удалить файл", e);
        }
    }

    public String normalizeFolderKey(String folderKey) {
        String normalized = folderKey == null ? "" : folderKey.trim().replace('\\', '/');
        if (normalized.isBlank()) {
            return "library";
        }
        normalized = normalized
            .replaceAll("/{2,}", "/")
            .replaceAll("^/+", "")
            .replaceAll("/+$", "");
        if (normalized.isBlank()) {
            return "library";
        }
        List<String> segments = List.of(normalized.split("/"));
        if (segments.isEmpty()) {
            return "library";
        }
        String root = segments.getFirst().toLowerCase(Locale.ROOT);
        if (!TOP_LEVEL_FOLDERS.contains(root)) {
            throw new IllegalArgumentException("Недопустимая папка: " + root);
        }
        StringBuilder out = new StringBuilder(root);
        for (int i = 1; i < segments.size(); i++) {
            String segment = segments.get(i) == null ? "" : segments.get(i).trim();
            if (segment.isBlank()) {
                continue;
            }
            if (!segment.matches("[a-zA-Z0-9_-]+")) {
                throw new IllegalArgumentException("Недопустимое имя подпапки: " + segment);
            }
            out.append("/").append(segment);
        }
        return out.toString();
    }

    private Path resolveFolderDir(String normalizedFolderKey) {
        Path target = uploadsRootDir.resolve(normalizedFolderKey).normalize();
        if (!target.startsWith(uploadsRootDir)) {
            throw new IllegalArgumentException("Некорректный путь к папке");
        }
        return target;
    }

    private void ensureTopLevelFoldersExist() {
        for (String root : TOP_LEVEL_FOLDERS) {
            ensureDirExists(uploadsRootDir.resolve(root));
        }
    }

    private static void ensureDirExists(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось создать директорию библиотеки изображений: " + path, e);
        }
    }

    private void collectFolders(String folderKey, Path folderPath, int depth, List<LibraryFolder> out) {
        long filesCount = countFiles(folderPath);
        String label = depth == 0
            ? TOP_LEVEL_FOLDER_LABELS.getOrDefault(folderKey, folderKey)
            : folderPath.getFileName().toString();
        out.add(new LibraryFolder(folderKey, label, depth, filesCount));

        List<Path> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folderPath)) {
            for (Path child : stream) {
                if (Files.isDirectory(child)) {
                    children.add(child);
                }
            }
        } catch (IOException ignored) {
            return;
        }
        children.sort(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));
        for (Path child : children) {
            String childKey = folderKey + "/" + child.getFileName();
            collectFolders(childKey, child, depth + 1, out);
        }
    }

    private static long countFiles(Path folderPath) {
        try (var stream = Files.list(folderPath)) {
            return stream.filter(Files::isRegularFile).count();
        } catch (IOException ignored) {
            return 0;
        }
    }

    private static String buildFileName(String originalFileName, String extension) {
        String original = originalFileName == null ? "" : originalFileName;
        int dotIndex = original.lastIndexOf('.');
        String baseName = dotIndex <= 0 ? original : original.substring(0, dotIndex);
        baseName = transliterateRu(baseName);
        baseName = baseName.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("-{2,}", "-")
            .replaceAll("(^-|-$)", "");
        if (baseName.isBlank()) {
            baseName = "image";
        }
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return baseName + "-" + suffix + extension;
    }

    private static String transliterateRu(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        Map<Character, String> map = new LinkedHashMap<>();
        map.put('а', "a"); map.put('б', "b"); map.put('в', "v"); map.put('г', "g"); map.put('д', "d");
        map.put('е', "e"); map.put('ё', "e"); map.put('ж', "zh"); map.put('з', "z"); map.put('и', "i");
        map.put('й', "y"); map.put('к', "k"); map.put('л', "l"); map.put('м', "m"); map.put('н', "n");
        map.put('о', "o"); map.put('п', "p"); map.put('р', "r"); map.put('с', "s"); map.put('т', "t");
        map.put('у', "u"); map.put('ф', "f"); map.put('х', "h"); map.put('ц', "c"); map.put('ч', "ch");
        map.put('ш', "sh"); map.put('щ', "sch"); map.put('ъ', ""); map.put('ы', "y"); map.put('ь', "");
        map.put('э', "e"); map.put('ю', "yu"); map.put('я', "ya");
        StringBuilder out = new StringBuilder();
        for (char ch : input.toLowerCase(Locale.ROOT).toCharArray()) {
            out.append(map.getOrDefault(ch, String.valueOf(ch)));
        }
        return out.toString();
    }

    private void cleanupEmptyParents(Path path) {
        Path cursor = path;
        while (cursor != null && cursor.startsWith(uploadsRootDir) && !cursor.equals(uploadsRootDir)) {
            String rootSegment = uploadsRootDir.relativize(cursor).toString().replace('\\', '/');
            if (TOP_LEVEL_FOLDERS.contains(rootSegment)) {
                return;
            }
            try (var stream = Files.list(cursor)) {
                if (stream.findAny().isPresent()) {
                    return;
                }
            } catch (IOException ignored) {
                return;
            }
            try {
                Files.deleteIfExists(cursor);
            } catch (IOException ignored) {
                return;
            }
            cursor = cursor.getParent();
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
