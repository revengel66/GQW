package com.example.gqw.admin.controller;

import com.example.gqw.analytics.aop.TrackAnalyticsEvent;
import com.example.gqw.admin.service.AdminService;
import com.example.gqw.shop.service.CatalogService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminFiltersController {

    private final AdminService adminService;
    private final CatalogService catalogService;
    private final AdminControllerSupport controllerSupport;

    public AdminFiltersController(
        AdminService adminService,
        CatalogService catalogService,
        AdminControllerSupport controllerSupport
    ) {
        this.adminService = adminService;
        this.catalogService = catalogService;
        this.controllerSupport = controllerSupport;
    }

    @GetMapping("/admin/filters")
    @TrackAnalyticsEvent(code = "FILTER_LIST_VIEW")
    public String filters(Model model) {
        model.addAttribute("filters", adminService.filters());
        return "admin/filters";
    }

    @GetMapping("/admin/filters/new")
    @TrackAnalyticsEvent(code = "FILTER_CREATE_VIEW")
    public String newFilter(Model model) {
        model.addAttribute("editingFilter", null);
        model.addAttribute("categoryRows", adminService.categoryTreeRows());
        model.addAttribute("categoryTree", catalogService.categoryTree());
        model.addAttribute("filterCategoryIdsMap", Map.of());
        model.addAttribute("selectedCategoryIds", List.of());
        model.addAttribute("filterOptions", List.of());
        model.addAttribute("filterFormMode", "create");
        return "admin/filter-form";
    }

    @GetMapping("/admin/filters/{id}/edit")
    @TrackAnalyticsEvent(code = "FILTER_EDIT_VIEW")
    public String editFilter(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            var filter = adminService.filterById(id);
            model.addAttribute("editingFilter", filter);
            model.addAttribute("categoryRows", adminService.categoryTreeRows());
            model.addAttribute("categoryTree", catalogService.categoryTree());
            model.addAttribute("selectedCategoryIds", adminService.filterCategoryIdsMap().getOrDefault(filter.getId(), List.of()));
            model.addAttribute("filterOptions", adminService.filterOptionsMap().getOrDefault(filter.getId(), List.of()));
            model.addAttribute("filterFormMode", "edit");
            return "admin/filter-form";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("filterError", ex.getMessage());
            return "redirect:/admin/filters";
        }
    }

    @PostMapping("/admin/filters/save")
    @TrackAnalyticsEvent(
        code = "FILTER_UPDATE",
        codeExpression = "#filterId == null ? 'FILTER_CREATE' : 'FILTER_UPDATE'"
    )
    public String saveFilter(
        @RequestParam(required = false) Long filterId,
        @RequestParam(required = false) String code,
        @RequestParam String name,
        @RequestParam(required = false) String valueType,
        @RequestParam(required = false) String viewType,
        @RequestParam(defaultValue = "false") boolean multiValue,
        @RequestParam(defaultValue = "false") boolean enabled,
        @RequestParam(required = false) List<Long> categoryIds,
        @RequestParam(required = false) String predefinedValues,
        RedirectAttributes redirectAttributes,
        HttpServletRequest request
    ) {
        try {
            adminService.saveFilter(filterId, code, name, valueType, viewType, multiValue, enabled, categoryIds, predefinedValues);
            redirectAttributes.addFlashAttribute("filterSuccess", "Фильтр сохранён");
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("filterError", ex.getMessage());
        }
        return "redirect:/admin/filters";
    }

    @PostMapping("/admin/filters/{id}/delete")
    @TrackAnalyticsEvent(code = "FILTER_DELETE")
    public String deleteFilter(
        @PathVariable Long id,
        RedirectAttributes redirectAttributes,
        HttpServletRequest request
    ) {
        try {
            adminService.deleteFilter(id);
            redirectAttributes.addFlashAttribute("filterSuccess", "Фильтр удалён");
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("filterError", ex.getMessage());
        }
        return "redirect:/admin/filters";
    }
}
