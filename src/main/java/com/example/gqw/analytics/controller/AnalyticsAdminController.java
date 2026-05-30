package com.example.gqw.analytics.controller;

import com.example.gqw.analytics.entity.AnalyticsCodeAliasType;
import com.example.gqw.analytics.entity.MetricValueKind;
import com.example.gqw.analytics.service.AnalyticsAdminAuthService;
import com.example.gqw.analytics.service.AnalyticsDictionaryAdminService;
import com.example.gqw.analytics.service.AnalyticsEventTypeMaintenanceService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AnalyticsAdminController {

    private static final DateTimeFormatter INPUT_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final AnalyticsAdminAuthService authService;
    private final AnalyticsDictionaryAdminService dictionaryAdminService;
    private final AnalyticsEventTypeMaintenanceService analyticsEventTypeMaintenanceService;

    public AnalyticsAdminController(
        AnalyticsAdminAuthService authService,
        AnalyticsDictionaryAdminService dictionaryAdminService,
        AnalyticsEventTypeMaintenanceService analyticsEventTypeMaintenanceService
    ) {
        this.authService = authService;
        this.dictionaryAdminService = dictionaryAdminService;
        this.analyticsEventTypeMaintenanceService = analyticsEventTypeMaintenanceService;
    }

    @GetMapping("/analytics-admin")
    public String root(HttpServletRequest request) {
        if (!authService.isSetupComplete()) {
            return "redirect:/analytics-admin/setup";
        }
        Object authFlag = request.getSession(true).getAttribute(AnalyticsAdminAuthService.SESSION_KEY_AUTH);
        if (authFlag instanceof Boolean authenticated && authenticated) {
            return "redirect:/analytics-admin/dashboard";
        }
        return "redirect:/analytics-admin/login";
    }

    @GetMapping("/analytics-admin/setup")
    public String setupPage() {
        if (authService.isSetupComplete()) {
            return "redirect:/analytics-admin/login";
        }
        return "analytics/admin-setup";
    }

    @PostMapping("/analytics-admin/setup")
    public String setupSubmit(
        @RequestParam String username,
        @RequestParam String password,
        @RequestParam String passwordRepeat,
        RedirectAttributes redirectAttributes
    ) {
        if (!password.equals(passwordRepeat)) {
            redirectAttributes.addFlashAttribute("error", "Пароли не совпадают");
            return "redirect:/analytics-admin/setup";
        }
        try {
            authService.registerInitial(username, password);
            redirectAttributes.addFlashAttribute("success", "Учётная запись создана. Выполните вход.");
            return "redirect:/analytics-admin/login";
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/analytics-admin/setup";
        }
    }

    @GetMapping("/analytics-admin/login")
    public String loginPage() {
        if (!authService.isSetupComplete()) {
            return "redirect:/analytics-admin/setup";
        }
        return "analytics/admin-login";
    }

    @PostMapping("/analytics-admin/login")
    public String loginSubmit(
        @RequestParam String username,
        @RequestParam String password,
        HttpServletRequest request,
        RedirectAttributes redirectAttributes
    ) {
        try {
            var user = authService.authenticate(username, password);
            request.getSession(true).setAttribute(AnalyticsAdminAuthService.SESSION_KEY_AUTH, true);
            request.getSession(true).setAttribute(AnalyticsAdminAuthService.SESSION_KEY_USER_ID, user.getId());
            request.getSession(true).setAttribute(AnalyticsAdminAuthService.SESSION_KEY_USERNAME, user.getUsername());
            return "redirect:/analytics-admin/dashboard";
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/analytics-admin/login";
        }
    }

    @PostMapping("/analytics-admin/logout")
    public String logout(HttpServletRequest request) {
        request.getSession(true).invalidate();
        return "redirect:/analytics-admin/login";
    }

    @GetMapping("/analytics-admin/dashboard")
    public String dashboard(Model model, HttpServletRequest request) {
        model.addAttribute(
            "analyticsAdminUsername",
            request.getSession(true).getAttribute(AnalyticsAdminAuthService.SESSION_KEY_USERNAME)
        );
        LocalDateTime now = LocalDateTime.now();
        model.addAttribute("analyticsTo", now.format(INPUT_DT));
        model.addAttribute("analyticsFrom", now.minusHours(24).format(INPUT_DT));
        model.addAttribute("analyticsApiBase", "/analytics-admin/api");
        return "analytics/admin-dashboard";
    }

    @GetMapping("/analytics-admin/dictionaries")
    public String dictionaries(
        Model model,
        HttpServletRequest request,
        @RequestParam(required = false) String eventModuleCode
    ) {
        populateDictionariesModel(model, request, eventModuleCode);
        return "analytics/admin-dictionaries";
    }

    @PostMapping("/analytics-admin/credentials")
    public String updateCredentials(
        @RequestParam String username,
        @RequestParam String password,
        @RequestParam String passwordRepeat,
        HttpServletRequest request,
        RedirectAttributes redirectAttributes
    ) {
        if (!password.equals(passwordRepeat)) {
            redirectAttributes.addFlashAttribute("error", "Пароли не совпадают");
            return "redirect:/analytics-admin/dictionaries";
        }
        try {
            Long userId = asLong(request.getSession(true).getAttribute(AnalyticsAdminAuthService.SESSION_KEY_USER_ID));
            var user = authService.updateCredentials(userId, username, password);
            request.getSession(true).setAttribute(AnalyticsAdminAuthService.SESSION_KEY_USERNAME, user.getUsername());
            redirectAttributes.addFlashAttribute("success", "Данные входа обновлены");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/analytics-admin/dictionaries";
    }

    @PostMapping("/analytics-admin/dictionaries/events/save")
    public String saveEventType(
        @RequestParam(required = false) String originalCode,
        @RequestParam String code,
        @RequestParam(required = false) String moduleCode,
        @RequestParam String name,
        @RequestParam(required = false) String description,
        @RequestParam(required = false) String eventModuleFilter,
        RedirectAttributes redirectAttributes
    ) {
        try {
            dictionaryAdminService.createOrUpdateEventType(originalCode, code, moduleCode, name, description);
            redirectAttributes.addFlashAttribute("success", "Событие сохранено");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return redirectDictionariesByModule(eventModuleFilter);
    }

    @PostMapping("/analytics-admin/dictionaries/events/save-all")
    public String saveAllEventTypes(
        @RequestParam(name = "originalCode[]", required = false) List<String> originalCodes,
        @RequestParam(name = "code[]", required = false) List<String> codes,
        @RequestParam(name = "moduleCode[]", required = false) List<String> moduleCodes,
        @RequestParam(name = "name[]", required = false) List<String> names,
        @RequestParam(name = "description[]", required = false) List<String> descriptions,
        @RequestParam(required = false) String eventModuleFilter,
        RedirectAttributes redirectAttributes
    ) {
        try {
            int size = maxSize(originalCodes, codes, moduleCodes, names, descriptions);
            int updated = 0;
            for (int i = 0; i < size; i++) {
                String code = listValue(codes, i);
                String name = listValue(names, i);
                if (isBlank(code) && isBlank(name)) {
                    continue;
                }
                dictionaryAdminService.createOrUpdateEventType(
                    listValue(originalCodes, i),
                    code,
                    listValue(moduleCodes, i),
                    name,
                    listValue(descriptions, i)
                );
                updated++;
            }
            redirectAttributes.addFlashAttribute("success", "События сохранены: " + updated);
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return redirectDictionariesByModule(eventModuleFilter);
    }

    @PostMapping("/analytics-admin/dictionaries/events/disable")
    public String disableEventType(
        @RequestParam(required = false) String originalCode,
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String eventModuleFilter,
        RedirectAttributes redirectAttributes
    ) {
        try {
            boolean active = dictionaryAdminService.toggleEventType(resolveDictionaryCode(originalCode, code));
            redirectAttributes.addFlashAttribute("success", active ? "Событие включено" : "Событие отключено");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return redirectDictionariesByModule(eventModuleFilter);
    }

    @PostMapping("/analytics-admin/dictionaries/events/delete")
    public String deleteEventType(
        @RequestParam(required = false) String originalCode,
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String eventModuleFilter,
        RedirectAttributes redirectAttributes
    ) {
        try {
            dictionaryAdminService.deleteEventType(resolveDictionaryCode(originalCode, code));
            redirectAttributes.addFlashAttribute("success", "Событие удалено");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return redirectDictionariesByModule(eventModuleFilter);
    }

    @GetMapping("/analytics-admin/dictionaries/events/delete/precheck")
    @ResponseBody
    public Map<String, Object> precheckDeleteEventType(
        @RequestParam(required = false) String originalCode,
        @RequestParam(required = false) String code
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        try {
            String resolvedCode = resolveDictionaryCode(originalCode, code);
            List<String> usages = analyticsEventTypeMaintenanceService.findTrackedEventUsages(resolvedCode);
            payload.put("ok", true);
            payload.put("code", resolvedCode);
            payload.put("deletable", usages.isEmpty());
            payload.put("usages", usages);
            payload.put("reason", usages.isEmpty() ? "" : "Событие используется в коде и не может быть удалено.");
        } catch (RuntimeException ex) {
            payload.put("ok", false);
            payload.put("deletable", false);
            payload.put("reason", ex.getMessage());
            payload.put("usages", List.of());
        }
        return payload;
    }

    @PostMapping("/analytics-admin/dictionaries/modules/save")
    public String saveModuleType(
        @RequestParam(required = false) String originalCode,
        @RequestParam String code,
        @RequestParam String name,
        @RequestParam(required = false) String description,
        RedirectAttributes redirectAttributes
    ) {
        try {
            dictionaryAdminService.createOrUpdateModuleType(originalCode, code, name, description);
            redirectAttributes.addFlashAttribute("success", "Модуль сохранён");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/analytics-admin/dictionaries";
    }

    @PostMapping("/analytics-admin/dictionaries/modules/save-all")
    public String saveAllModuleTypes(
        @RequestParam(name = "originalCode[]", required = false) List<String> originalCodes,
        @RequestParam(name = "code[]", required = false) List<String> codes,
        @RequestParam(name = "name[]", required = false) List<String> names,
        @RequestParam(name = "description[]", required = false) List<String> descriptions,
        RedirectAttributes redirectAttributes
    ) {
        try {
            int size = maxSize(originalCodes, codes, names, descriptions);
            int updated = 0;
            for (int i = 0; i < size; i++) {
                String code = listValue(codes, i);
                String name = listValue(names, i);
                if (isBlank(code) && isBlank(name)) {
                    continue;
                }
                dictionaryAdminService.createOrUpdateModuleType(
                    listValue(originalCodes, i),
                    code,
                    name,
                    listValue(descriptions, i)
                );
                updated++;
            }
            redirectAttributes.addFlashAttribute("success", "Модули сохранены: " + updated);
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/analytics-admin/dictionaries";
    }

    @PostMapping("/analytics-admin/dictionaries/modules/disable")
    public String disableModuleType(
        @RequestParam(required = false) String originalCode,
        @RequestParam(required = false) String code,
        RedirectAttributes redirectAttributes
    ) {
        try {
            boolean active = dictionaryAdminService.toggleModuleType(resolveDictionaryCode(originalCode, code));
            redirectAttributes.addFlashAttribute("success", active ? "Модуль включён" : "Модуль отключён");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/analytics-admin/dictionaries";
    }

    @PostMapping("/analytics-admin/dictionaries/modules/delete")
    public String deleteModuleType(
        @RequestParam(required = false) String originalCode,
        @RequestParam(required = false) String code,
        RedirectAttributes redirectAttributes
    ) {
        try {
            dictionaryAdminService.deleteModuleType(resolveDictionaryCode(originalCode, code));
            redirectAttributes.addFlashAttribute("success", "Модуль удалён");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/analytics-admin/dictionaries";
    }

    @GetMapping("/analytics-admin/dictionaries/modules/delete/precheck")
    @ResponseBody
    public Map<String, Object> precheckDeleteModuleType(
        @RequestParam(required = false) String originalCode,
        @RequestParam(required = false) String code
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        try {
            String resolvedCode = resolveDictionaryCode(originalCode, code);
            AnalyticsDictionaryAdminService.ModuleDeletePrecheck precheck = dictionaryAdminService.precheckDeleteModuleType(resolvedCode);
            payload.put("ok", true);
            payload.put("code", precheck.code());
            payload.put("deletable", precheck.deletable());
            payload.put("reason", precheck.reason());
            payload.put("usages", precheck.usages());
            payload.put("eventTypeCount", precheck.eventTypeCount());
            payload.put("eventCount", precheck.eventCount());
        } catch (RuntimeException ex) {
            payload.put("ok", false);
            payload.put("deletable", false);
            payload.put("reason", ex.getMessage());
            payload.put("usages", List.of());
            payload.put("eventTypeCount", 0);
            payload.put("eventCount", 0);
        }
        return payload;
    }

    @PostMapping("/analytics-admin/dictionaries/attributes/save")
    public String saveEventAttributeType(
        @RequestParam(required = false) String originalCode,
        @RequestParam String code,
        @RequestParam String name,
        @RequestParam(required = false) String description,
        @RequestParam String valueKind,
        @RequestParam(required = false) String unitDefault,
        RedirectAttributes redirectAttributes
    ) {
        try {
            dictionaryAdminService.createOrUpdateEventAttributeType(
                originalCode,
                code,
                name,
                description,
                valueKind,
                unitDefault
            );
            redirectAttributes.addFlashAttribute("success", "Атрибут сохранён");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/analytics-admin/dictionaries";
    }

    @PostMapping("/analytics-admin/dictionaries/attributes/save-all")
    public String saveAllEventAttributeTypes(
        @RequestParam(name = "originalCode[]", required = false) List<String> originalCodes,
        @RequestParam(name = "code[]", required = false) List<String> codes,
        @RequestParam(name = "name[]", required = false) List<String> names,
        @RequestParam(name = "description[]", required = false) List<String> descriptions,
        @RequestParam(name = "valueKind[]", required = false) List<String> valueKinds,
        @RequestParam(name = "unitDefault[]", required = false) List<String> unitDefaults,
        RedirectAttributes redirectAttributes
    ) {
        try {
            int size = maxSize(originalCodes, codes, names, descriptions, valueKinds, unitDefaults);
            int updated = 0;
            for (int i = 0; i < size; i++) {
                String code = listValue(codes, i);
                String name = listValue(names, i);
                if (isBlank(code) && isBlank(name)) {
                    continue;
                }
                dictionaryAdminService.createOrUpdateEventAttributeType(
                    listValue(originalCodes, i),
                    code,
                    name,
                    listValue(descriptions, i),
                    listValue(valueKinds, i),
                    listValue(unitDefaults, i)
                );
                updated++;
            }
            redirectAttributes.addFlashAttribute("success", "Атрибуты сохранены: " + updated);
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/analytics-admin/dictionaries";
    }

    @PostMapping("/analytics-admin/dictionaries/attributes/disable")
    public String disableEventAttributeType(
        @RequestParam(required = false) String originalCode,
        @RequestParam(required = false) String code,
        RedirectAttributes redirectAttributes
    ) {
        try {
            boolean active = dictionaryAdminService.toggleEventAttributeType(resolveDictionaryCode(originalCode, code));
            redirectAttributes.addFlashAttribute("success", active ? "Атрибут включён" : "Атрибут отключён");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/analytics-admin/dictionaries";
    }

    @PostMapping("/analytics-admin/dictionaries/attributes/delete")
    public String deleteEventAttributeType(
        @RequestParam(required = false) String originalCode,
        @RequestParam(required = false) String code,
        RedirectAttributes redirectAttributes
    ) {
        try {
            dictionaryAdminService.deleteEventAttributeType(resolveDictionaryCode(originalCode, code));
            redirectAttributes.addFlashAttribute("success", "Атрибут удалён");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/analytics-admin/dictionaries";
    }

    @GetMapping("/analytics-admin/dictionaries/attributes/delete/precheck")
    @ResponseBody
    public Map<String, Object> precheckDeleteEventAttributeType(
        @RequestParam(required = false) String originalCode,
        @RequestParam(required = false) String code
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        try {
            String resolvedCode = resolveDictionaryCode(originalCode, code);
            List<String> usages = analyticsEventTypeMaintenanceService.findTrackedAttributeUsages(resolvedCode);
            payload.put("ok", true);
            payload.put("code", resolvedCode);
            payload.put("deletable", usages.isEmpty());
            payload.put("usages", usages);
            payload.put("reason", usages.isEmpty() ? "" : "Атрибут используется в коде и не может быть удалён.");
        } catch (RuntimeException ex) {
            payload.put("ok", false);
            payload.put("deletable", false);
            payload.put("reason", ex.getMessage());
            payload.put("usages", List.of());
        }
        return payload;
    }

    @PostMapping("/analytics-admin/dictionaries/metrics/save")
    public String saveStageMetricType(
        @RequestParam(required = false) String originalCode,
        @RequestParam String code,
        @RequestParam String name,
        @RequestParam(required = false) String description,
        @RequestParam(required = false) String readingGuide,
        @RequestParam String valueKind,
        @RequestParam(required = false) String unitDefault,
        RedirectAttributes redirectAttributes
    ) {
        try {
            dictionaryAdminService.createOrUpdateStageMetricType(
                originalCode,
                code,
                name,
                description,
                readingGuide,
                valueKind,
                unitDefault
            );
            redirectAttributes.addFlashAttribute("success", "Метрика сохранена");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/analytics-admin/dictionaries";
    }

    @PostMapping("/analytics-admin/dictionaries/stages/save")
    public String saveStageType(
        @RequestParam(required = false) String originalCode,
        @RequestParam String code,
        @RequestParam String name,
        @RequestParam(required = false) String description,
        RedirectAttributes redirectAttributes
    ) {
        try {
            dictionaryAdminService.createOrUpdateStageType(originalCode, code, name, description);
            redirectAttributes.addFlashAttribute("success", "Этап сохранён");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/analytics-admin/dictionaries";
    }

    @PostMapping("/analytics-admin/dictionaries/stages/save-all")
    public String saveAllStageTypes(
        @RequestParam(name = "originalCode[]", required = false) List<String> originalCodes,
        @RequestParam(name = "code[]", required = false) List<String> codes,
        @RequestParam(name = "name[]", required = false) List<String> names,
        @RequestParam(name = "description[]", required = false) List<String> descriptions,
        RedirectAttributes redirectAttributes
    ) {
        try {
            int size = maxSize(originalCodes, codes, names, descriptions);
            int updated = 0;
            for (int i = 0; i < size; i++) {
                String code = listValue(codes, i);
                String name = listValue(names, i);
                if (isBlank(code) && isBlank(name)) {
                    continue;
                }
                dictionaryAdminService.createOrUpdateStageType(
                    listValue(originalCodes, i),
                    code,
                    name,
                    listValue(descriptions, i)
                );
                updated++;
            }
            redirectAttributes.addFlashAttribute("success", "Этапы сохранены: " + updated);
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/analytics-admin/dictionaries";
    }

    @PostMapping("/analytics-admin/dictionaries/stages/disable")
    public String disableStageType(
        @RequestParam(required = false) String originalCode,
        @RequestParam(required = false) String code,
        RedirectAttributes redirectAttributes
    ) {
        try {
            boolean active = dictionaryAdminService.toggleStageType(resolveDictionaryCode(originalCode, code));
            redirectAttributes.addFlashAttribute("success", active ? "Этап включён" : "Этап отключён");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/analytics-admin/dictionaries";
    }

    @PostMapping("/analytics-admin/dictionaries/stages/delete")
    public String deleteStageType(
        @RequestParam(required = false) String originalCode,
        @RequestParam(required = false) String code,
        RedirectAttributes redirectAttributes
    ) {
        try {
            dictionaryAdminService.deleteStageType(resolveDictionaryCode(originalCode, code));
            redirectAttributes.addFlashAttribute("success", "Этап удалён");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/analytics-admin/dictionaries";
    }

    @PostMapping("/analytics-admin/dictionaries/metrics/save-all")
    public String saveAllStageMetricTypes(
        @RequestParam(name = "originalCode[]", required = false) List<String> originalCodes,
        @RequestParam(name = "code[]", required = false) List<String> codes,
        @RequestParam(name = "name[]", required = false) List<String> names,
        @RequestParam(name = "description[]", required = false) List<String> descriptions,
        @RequestParam(name = "readingGuide[]", required = false) List<String> readingGuides,
        @RequestParam(name = "valueKind[]", required = false) List<String> valueKinds,
        @RequestParam(name = "unitDefault[]", required = false) List<String> unitDefaults,
        RedirectAttributes redirectAttributes
    ) {
        try {
            int size = maxSize(originalCodes, codes, names, descriptions, readingGuides, valueKinds, unitDefaults);
            int updated = 0;
            for (int i = 0; i < size; i++) {
                String code = listValue(codes, i);
                String name = listValue(names, i);
                if (isBlank(code) && isBlank(name)) {
                    continue;
                }
                dictionaryAdminService.createOrUpdateStageMetricType(
                    listValue(originalCodes, i),
                    code,
                    name,
                    listValue(descriptions, i),
                    listValue(readingGuides, i),
                    listValue(valueKinds, i),
                    listValue(unitDefaults, i)
                );
                updated++;
            }
            redirectAttributes.addFlashAttribute("success", "Метрики сохранены: " + updated);
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/analytics-admin/dictionaries";
    }

    @PostMapping("/analytics-admin/dictionaries/metrics/disable")
    public String disableStageMetricType(
        @RequestParam(required = false) String originalCode,
        @RequestParam(required = false) String code,
        RedirectAttributes redirectAttributes
    ) {
        try {
            boolean active = dictionaryAdminService.toggleStageMetricType(resolveDictionaryCode(originalCode, code));
            redirectAttributes.addFlashAttribute("success", active ? "Метрика включена" : "Метрика отключена");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/analytics-admin/dictionaries";
    }

    @PostMapping("/analytics-admin/dictionaries/metrics/delete")
    public String deleteStageMetricType(
        @RequestParam(required = false) String originalCode,
        @RequestParam(required = false) String code,
        RedirectAttributes redirectAttributes
    ) {
        try {
            dictionaryAdminService.deleteStageMetricType(resolveDictionaryCode(originalCode, code));
            redirectAttributes.addFlashAttribute("success", "Метрика удалена");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/analytics-admin/dictionaries";
    }

    @PostMapping("/analytics-admin/dictionaries/aliases/save")
    public String saveAlias(
        @RequestParam String aliasType,
        @RequestParam String sourceCode,
        @RequestParam String targetCode,
        RedirectAttributes redirectAttributes
    ) {
        try {
            AnalyticsCodeAliasType type = AnalyticsCodeAliasType.valueOf(aliasType);
            dictionaryAdminService.upsertAlias(type, sourceCode, targetCode);
            redirectAttributes.addFlashAttribute("success", "Alias сохранён");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/analytics-admin/dictionaries";
    }

    @PostMapping("/analytics-admin/dictionaries/aliases/disable")
    public String disableAlias(@RequestParam Long aliasId, RedirectAttributes redirectAttributes) {
        try {
            boolean active = dictionaryAdminService.toggleAlias(aliasId);
            redirectAttributes.addFlashAttribute("success", active ? "Alias включён" : "Alias отключён");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/analytics-admin/dictionaries";
    }

    @PostMapping("/analytics-admin/dictionaries/aliases/delete")
    public String deleteAlias(@RequestParam Long aliasId, RedirectAttributes redirectAttributes) {
        try {
            dictionaryAdminService.deleteAlias(aliasId);
            redirectAttributes.addFlashAttribute("success", "Alias удалён");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/analytics-admin/dictionaries";
    }

    private void populateDictionariesModel(Model model, HttpServletRequest request, String eventModuleCode) {
        model.addAttribute("modules", dictionaryAdminService.allModules());
        var eventTypes = dictionaryAdminService.allEventTypes(eventModuleCode);
        model.addAttribute("eventTypes", eventTypes);
        model.addAttribute("eventTypeEventCounts", dictionaryAdminService.eventTypeEventCounts(eventTypes));
        var attributeTypes = dictionaryAdminService.allEventAttributeTypes();
        model.addAttribute("attributeTypes", attributeTypes);
        model.addAttribute("attributeTypeValueCounts", dictionaryAdminService.eventAttributeValueCounts(attributeTypes));
        model.addAttribute("stageTypes", dictionaryAdminService.allStageTypes());
        model.addAttribute("metricTypes", dictionaryAdminService.allStageMetricTypes());
        model.addAttribute("builtInEventAttributeCodes", dictionaryAdminService.builtInEventAttributeCodes());
        model.addAttribute("builtInStageCodes", dictionaryAdminService.builtInStageCodes());
        model.addAttribute("builtInStageMetricCodes", dictionaryAdminService.builtInStageMetricCodes());
        model.addAttribute("eventAliases", dictionaryAdminService.allAliases(AnalyticsCodeAliasType.EVENT));
        model.addAttribute("attributeAliases", dictionaryAdminService.allAliases(AnalyticsCodeAliasType.ATTRIBUTE));
        model.addAttribute("metricAliases", dictionaryAdminService.allAliases(AnalyticsCodeAliasType.METRIC));
        model.addAttribute("valueKinds", MetricValueKind.values());
        model.addAttribute("selectedEventModuleCode", eventModuleCode == null ? "" : eventModuleCode);
        model.addAttribute(
            "analyticsAdminUsername",
            request.getSession(true).getAttribute(AnalyticsAdminAuthService.SESSION_KEY_USERNAME)
        );
    }

    private String redirectDictionariesByModule(String eventModuleFilter) {
        if (eventModuleFilter == null || eventModuleFilter.isBlank()) {
            return "redirect:/analytics-admin/dictionaries";
        }
        return "redirect:/analytics-admin/dictionaries?eventModuleCode="
            + eventModuleFilter.trim().toUpperCase(Locale.ROOT);
    }

    private static String resolveDictionaryCode(String preferred, String fallback) {
        String direct = firstCodeToken(preferred);
        if (direct != null) {
            return direct;
        }
        direct = firstCodeToken(fallback);
        if (direct != null) {
            return direct;
        }
        throw new IllegalArgumentException("Код обязателен");
    }

    private static String firstCodeToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(",");
        for (String part : parts) {
            String token = part == null ? "" : part.trim();
            if (!token.isEmpty()) {
                return token;
            }
        }
        return null;
    }

    private static Long asLong(Object value) {
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Integer intValue) {
            return (long) intValue;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text.trim());
        }
        return null;
    }

    @SafeVarargs
    private static int maxSize(List<String>... lists) {
        int max = 0;
        if (lists == null) {
            return max;
        }
        for (List<String> list : lists) {
            if (list != null && list.size() > max) {
                max = list.size();
            }
        }
        return max;
    }

    private static String listValue(List<String> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
