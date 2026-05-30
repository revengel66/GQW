package com.example.gqw.admin.controller;

import com.example.gqw.shop.entity.OrderStatus;
import jakarta.servlet.http.HttpServletRequest;
import com.example.gqw.shop.service.LibraryStorageService;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Component
public class AdminControllerSupport {

    public record LibraryViewData(
        String selectedFolder,
        List<LibraryStorageService.LibraryFolder> folders,
        List<LibraryStorageService.LibraryFile> files
    ) {
    }

    private final LibraryStorageService libraryStorageService;

    public AdminControllerSupport(LibraryStorageService libraryStorageService) {
        this.libraryStorageService = libraryStorageService;
    }

    public LibraryViewData prepareLibraryViewData() {
        return prepareLibraryViewData(null);
    }

    public LibraryViewData prepareLibraryViewData(String preferredFolder) {
        List<LibraryStorageService.LibraryFolder> folders = libraryStorageService.listFolders();
        String selectedFolder;
        boolean hasPreferredFolder = preferredFolder != null && !preferredFolder.isBlank();
        if (hasPreferredFolder) {
            try {
                selectedFolder = libraryStorageService.normalizeFolderKey(preferredFolder);
            } catch (IllegalArgumentException ex) {
                selectedFolder = "library";
                hasPreferredFolder = false;
            }
        } else {
            selectedFolder = "library";
        }

        if (!hasPreferredFolder) {
            for (LibraryStorageService.LibraryFolder folder : folders) {
                if (folder != null && folder.key() != null && folder.filesCount() > 0) {
                    selectedFolder = folder.key();
                    break;
                }
            }
        }

        List<LibraryStorageService.LibraryFile> files = libraryStorageService.listFilesInFolder(selectedFolder);
        return new LibraryViewData(selectedFolder, folders, files);
    }

    public Map<String, String> orderStatusRuMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(OrderStatus.NEW.name(), "Новый");
        map.put(OrderStatus.ACCEPTED.name(), "Принят");
        map.put(OrderStatus.ASSEMBLED.name(), "Собран");
        map.put(OrderStatus.WAITING_PICKUP.name(), "Готов к выдаче");
        map.put(OrderStatus.DELIVERED.name(), "Доставлен");
        map.put(OrderStatus.REJECTED.name(), "Отменён");
        return map;
    }

    public Map<String, String> orderStatusClassMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(OrderStatus.NEW.name(), "text-bg-warning text-dark");
        map.put(OrderStatus.ACCEPTED.name(), "text-bg-primary");
        map.put(OrderStatus.ASSEMBLED.name(), "text-bg-info text-dark");
        map.put(OrderStatus.WAITING_PICKUP.name(), "text-bg-secondary");
        map.put(OrderStatus.DELIVERED.name(), "text-bg-success");
        map.put(OrderStatus.REJECTED.name(), "text-bg-danger");
        return map;
    }

    public Map<String, String> supportStatusRuMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("NEW", "Новая");
        map.put("IN_PROGRESS", "В обработке");
        map.put("PROCESSED", "Обработана");
        return map;
    }

    public Map<String, String> supportStatusClassMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("NEW", "text-bg-warning text-dark");
        map.put("IN_PROGRESS", "text-bg-primary");
        map.put("PROCESSED", "text-bg-success");
        return map;
    }

    public void collectCategoryAndDescendants(
        Long categoryId,
        Map<Long, List<Long>> childrenByParentId,
        Set<Long> result
    ) {
        if (categoryId == null || result.contains(categoryId)) {
            return;
        }
        result.add(categoryId);
        List<Long> children = childrenByParentId.get(categoryId);
        if (children == null || children.isEmpty()) {
            return;
        }
        for (Long childId : children) {
            collectCategoryAndDescendants(childId, childrenByParentId, result);
        }
    }

    public boolean isSaleOrderStatus(OrderStatus status) {
        return status == OrderStatus.ACCEPTED
            || status == OrderStatus.ASSEMBLED
            || status == OrderStatus.WAITING_PICKUP
            || status == OrderStatus.DELIVERED;
    }

    public void appendReviewRedirectFilters(
        RedirectAttributes redirectAttributes,
        String status,
        Integer rating,
        LocalDate dateFrom,
        LocalDate dateTo,
        Integer limit
    ) {
        if (status != null && !status.isBlank()) {
            redirectAttributes.addAttribute("status", status);
        }
        if (rating != null) {
            redirectAttributes.addAttribute("rating", rating);
        }
        if (dateFrom != null) {
            redirectAttributes.addAttribute("dateFrom", dateFrom);
        }
        if (dateTo != null) {
            redirectAttributes.addAttribute("dateTo", dateTo);
        }
        if (limit != null) {
            redirectAttributes.addAttribute("limit", limit);
        }
    }

    public boolean isAjaxRequest(HttpServletRequest request) {
        return request != null && "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));
    }

    public void rethrowAjax(IllegalArgumentException ex, HttpServletRequest request) {
        if (isAjaxRequest(request)) {
            throw new IllegalStateException(ex.getMessage());
        }
    }
}
