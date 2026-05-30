package com.example.gqw.admin.controller;

import com.example.gqw.analytics.aop.TrackAnalyticsEvent;
import com.example.gqw.shop.entity.Category;
import com.example.gqw.admin.service.AdminService;
import com.example.gqw.shop.service.CatalogService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminCategoriesController {

    private final AdminService adminService;
    private final CatalogService catalogService;
    private final AdminControllerSupport controllerSupport;

    public AdminCategoriesController(
        AdminService adminService,
        CatalogService catalogService,
        AdminControllerSupport controllerSupport
    ) {
        this.adminService = adminService;
        this.catalogService = catalogService;
        this.controllerSupport = controllerSupport;
    }

    @GetMapping("/admin/categories")
    @TrackAnalyticsEvent(code = "CATEGORY_LIST_VIEW")
    public String categories(
        @RequestParam(required = false, defaultValue = "date_desc") String sort,
        Model model
    ) {
        String normalizedSort = sort == null ? "date_desc" : sort.trim().toLowerCase(Locale.ROOT);
        if (!List.of("date_desc", "date_asc", "name_asc", "name_desc").contains(normalizedSort)) {
            normalizedSort = "date_desc";
        }
        model.addAttribute("categorySort", normalizedSort);
        model.addAttribute("categoryRows", adminService.categoryTreeRows(normalizedSort));
        return "admin/categories";
    }

    @GetMapping("/admin/categories/new")
    @TrackAnalyticsEvent(code = "CATEGORY_CREATE_VIEW")
    public String newCategory(Model model) {
        AdminControllerSupport.LibraryViewData libraryViewData = controllerSupport.prepareLibraryViewData();
        model.addAttribute("categoryRows", adminService.categoryTreeRows());
        model.addAttribute("categoryTree", catalogService.categoryTree());
        model.addAttribute("libraryFiles", libraryViewData.files());
        model.addAttribute("fileFolders", libraryViewData.folders());
        model.addAttribute("selectedLibraryFolder", libraryViewData.selectedFolder());
        model.addAttribute("categoryFormMode", "create");
        model.addAttribute("editingCategory", null);
        return "admin/category-form";
    }

    @GetMapping("/admin/categories/{id}/edit")
    @TrackAnalyticsEvent(code = "CATEGORY_EDIT_VIEW")
    public String editCategory(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Category category = adminService.categoryById(id);
            AdminControllerSupport.LibraryViewData libraryViewData = controllerSupport.prepareLibraryViewData();
            model.addAttribute("categoryRows", adminService.categoryTreeRows());
            model.addAttribute("categoryTree", catalogService.categoryTree());
            model.addAttribute("libraryFiles", libraryViewData.files());
            model.addAttribute("fileFolders", libraryViewData.folders());
            model.addAttribute("selectedLibraryFolder", libraryViewData.selectedFolder());
            model.addAttribute("categoryFormMode", "edit");
            model.addAttribute("editingCategory", category);
            model.addAttribute("categoryProducts", adminService.categoryProducts(id));
            return "admin/category-form";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("categoryError", ex.getMessage());
            return "redirect:/admin/categories";
        }
    }

    @PostMapping("/admin/categories")
    @TrackAnalyticsEvent(code = "CATEGORY_CREATE")
    public String createCategory(
        @RequestParam String name,
        @RequestParam(required = false) String slug,
        @RequestParam(required = false) String description,
        @RequestParam(required = false) Long parentId,
        @RequestParam(defaultValue = "false") boolean isPublished,
        @RequestParam(required = false) String libraryImageUrl,
        @RequestParam(required = false) MultipartFile image,
        RedirectAttributes redirectAttributes,
        HttpServletRequest request
    ) {
        try {
            adminService.createCategory(name, slug, description, parentId, isPublished, libraryImageUrl, image);
            if (controllerSupport.isAjaxRequest(request)) {
                return "redirect:/admin/categories";
            }
            redirectAttributes.addFlashAttribute("categorySuccess", "Категория сохранена");
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("categoryError", ex.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/admin/categories/{id}")
    @TrackAnalyticsEvent(code = "CATEGORY_UPDATE")
    public String updateCategory(
        @PathVariable Long id,
        @RequestParam String name,
        @RequestParam(required = false) String slug,
        @RequestParam(required = false) String description,
        @RequestParam(required = false) Long parentId,
        @RequestParam(defaultValue = "false") boolean isPublished,
        @RequestParam(required = false) String libraryImageUrl,
        @RequestParam(required = false) MultipartFile image,
        RedirectAttributes redirectAttributes,
        HttpServletRequest request
    ) {
        try {
            adminService.updateCategory(id, name, slug, description, parentId, isPublished, libraryImageUrl, image);
            if (controllerSupport.isAjaxRequest(request)) {
                return "redirect:/admin/categories/" + id + "/edit";
            }
            redirectAttributes.addFlashAttribute("categorySuccess", "Категория обновлена");
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("categoryError", ex.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/admin/categories/{id}/delete")
    @TrackAnalyticsEvent(code = "CATEGORY_DELETE")
    public String deleteCategory(
        @PathVariable Long id,
        RedirectAttributes redirectAttributes,
        HttpServletRequest request
    ) {
        try {
            adminService.deleteCategory(id);
            redirectAttributes.addFlashAttribute("categorySuccess", "Категория удалена");
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("categoryError", ex.getMessage());
        }
        return "redirect:/admin/categories";
    }
}
