package com.example.gqw.admin.controller;

import com.example.gqw.shop.entity.OrderItem;
import com.example.gqw.shop.entity.ShopOrder;
import com.example.gqw.shop.entity.SupportRequest;
import com.example.gqw.shop.service.OrderService;
import com.example.gqw.shop.service.SupportService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminSupportController {

    private final SupportService supportService;
    private final OrderService orderService;
    private final AdminControllerSupport controllerSupport;

    public AdminSupportController(
        SupportService supportService,
        OrderService orderService,
        AdminControllerSupport controllerSupport
    ) {
        this.supportService = supportService;
        this.orderService = orderService;
        this.controllerSupport = controllerSupport;
    }

    @GetMapping("/admin/support")
    public String support(
        @RequestParam(required = false, defaultValue = "CONTACT") String tab,
        @RequestParam(required = false, defaultValue = "ALL") String status,
        @RequestParam(required = false, defaultValue = "NEWEST") String sort,
        @RequestParam(required = false, defaultValue = "") String q,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        Model model
    ) {
        String normalizedTabRequested = tab == null ? "CONTACT" : tab.trim().toUpperCase(Locale.ROOT);
        String normalizedTab = switch (normalizedTabRequested) {
            case "SUPPORT" -> "SUPPORT";
            default -> "CONTACT";
        };
        String normalizedStatusRequested = status == null ? "ALL" : status.trim().toUpperCase(Locale.ROOT);
        String normalizedStatus = switch (normalizedStatusRequested) {
            case "NEW", "IN_PROGRESS", "PROCESSED" -> normalizedStatusRequested;
            default -> "ALL";
        };
        String normalizedSortRequested = sort == null ? "NEWEST" : sort.trim().toUpperCase(Locale.ROOT);
        String normalizedSort = switch (normalizedSortRequested) {
            case "OLDEST", "STATUS_ASC", "STATUS_DESC" -> normalizedSortRequested;
            default -> "NEWEST";
        };
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

        LocalDate normalizedFromMutable = dateFrom;
        LocalDate normalizedToMutable = dateTo;
        if (normalizedFromMutable != null && normalizedToMutable != null && normalizedFromMutable.isAfter(normalizedToMutable)) {
            LocalDate tmp = normalizedFromMutable;
            normalizedFromMutable = normalizedToMutable;
            normalizedToMutable = tmp;
        }
        final LocalDate normalizedFrom = normalizedFromMutable;
        final LocalDate normalizedTo = normalizedToMutable;
        ZoneId zoneId = ZoneId.systemDefault();

        List<SupportRequest> allRequests = supportService.requestsForAdmin();
        long contactTotal = allRequests.stream().filter(request -> request.getOrder() == null).count();
        long supportTotal = allRequests.stream().filter(request -> request.getOrder() != null).count();

        List<SupportRequest> filteredRequests = allRequests.stream()
            .filter(request -> {
                if ("SUPPORT".equals(normalizedTab)) {
                    return request.getOrder() != null;
                }
                return request.getOrder() == null;
            })
            .filter(request -> {
                if ("ALL".equals(normalizedStatus)) {
                    return true;
                }
                return normalizedStatus.equals(supportService.resolveAdminStatus(request));
            })
            .filter(request -> {
                if (request.getCreatedAt() == null) {
                    return normalizedFrom == null && normalizedTo == null;
                }
                LocalDate createdDate = request.getCreatedAt().atZone(zoneId).toLocalDate();
                if (normalizedFrom != null && createdDate.isBefore(normalizedFrom)) {
                    return false;
                }
                if (normalizedTo != null && createdDate.isAfter(normalizedTo)) {
                    return false;
                }
                return true;
            })
            .filter(request -> {
                if (query.isBlank()) {
                    return true;
                }
                String requestId = String.valueOf(request.getId());
                String name = request.getName() == null ? "" : request.getName().toLowerCase(Locale.ROOT);
                String email = request.getEmail() == null ? "" : request.getEmail().toLowerCase(Locale.ROOT);
                String phone = request.getPhone() == null ? "" : request.getPhone().toLowerCase(Locale.ROOT);
                String subjectValue = request.getSubject() == null ? "" : request.getSubject().toLowerCase(Locale.ROOT);
                String message = request.getMessage() == null ? "" : request.getMessage().toLowerCase(Locale.ROOT);
                String adminReply = request.getAdminReply() == null ? "" : request.getAdminReply().toLowerCase(Locale.ROOT);
                String userName = request.getUser() == null || request.getUser().getUsername() == null
                    ? ""
                    : request.getUser().getUsername().toLowerCase(Locale.ROOT);
                String orderId = request.getOrder() == null || request.getOrder().getId() == null
                    ? ""
                    : String.valueOf(request.getOrder().getId());
                return requestId.contains(query)
                    || name.contains(query)
                    || email.contains(query)
                    || phone.contains(query)
                    || subjectValue.contains(query)
                    || message.contains(query)
                    || adminReply.contains(query)
                    || userName.contains(query)
                    || orderId.contains(query);
            })
            .toList();

        Comparator<SupportRequest> comparator = Comparator.comparing(SupportRequest::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        if ("OLDEST".equals(normalizedSort)) {
            comparator = Comparator.comparing(SupportRequest::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        } else if ("STATUS_ASC".equals(normalizedSort)) {
            comparator = Comparator.comparing(supportService::resolveAdminStatus);
        } else if ("STATUS_DESC".equals(normalizedSort)) {
            comparator = Comparator.comparing((SupportRequest request) -> supportService.resolveAdminStatus(request)).reversed();
        }
        filteredRequests = filteredRequests.stream()
            .sorted(comparator.thenComparing(SupportRequest::getId, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();

        Map<Long, String> supportStatusByRequestId = new LinkedHashMap<>();
        for (SupportRequest request : filteredRequests) {
            supportStatusByRequestId.put(request.getId(), supportService.resolveAdminStatus(request));
        }

        model.addAttribute("requests", filteredRequests);
        model.addAttribute("supportTab", normalizedTab);
        model.addAttribute("supportStatusFilter", normalizedStatus);
        model.addAttribute("supportSort", normalizedSort);
        model.addAttribute("supportQuery", q == null ? "" : q.trim());
        model.addAttribute("supportDateFrom", normalizedFrom);
        model.addAttribute("supportDateTo", normalizedTo);
        model.addAttribute("supportStatusRuMap", controllerSupport.supportStatusRuMap());
        model.addAttribute("supportStatusClassMap", controllerSupport.supportStatusClassMap());
        model.addAttribute("supportStatusByRequestId", supportStatusByRequestId);
        model.addAttribute("contactRequestsTotal", contactTotal);
        model.addAttribute("supportRequestsTotal", supportTotal);
        return "admin/support";
    }

    @GetMapping("/admin/support/{id}")
    public String supportDetails(
        @PathVariable Long id,
        @RequestParam(required = false, defaultValue = "CONTACT") String tab,
        @RequestParam(required = false, defaultValue = "ALL") String status,
        @RequestParam(required = false, defaultValue = "NEWEST") String sort,
        @RequestParam(required = false, defaultValue = "") String q,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        SupportRequest supportRequest;
        try {
            supportRequest = supportService.requestById(id);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("supportError", ex.getMessage());
            return "redirect:/admin/support";
        }

        ShopOrder order = supportRequest.getOrder();
        List<OrderItem> orderItems = order == null ? List.of() : orderService.itemsForOrder(order);
        List<com.example.gqw.shop.entity.OrderStatusHistory> orderHistory = order == null ? List.of() : orderService.statusHistoryForOrder(order);
        int orderItemsCount = orderItems.stream()
            .map(OrderItem::getQuantity)
            .filter(quantity -> quantity != null && quantity > 0)
            .mapToInt(Integer::intValue)
            .sum();

        model.addAttribute("supportRequest", supportRequest);
        model.addAttribute("requestStatus", supportService.resolveAdminStatus(supportRequest));
        model.addAttribute("supportStatusRuMap", controllerSupport.supportStatusRuMap());
        model.addAttribute("supportStatusClassMap", controllerSupport.supportStatusClassMap());
        model.addAttribute("order", order);
        model.addAttribute("orderItems", orderItems);
        model.addAttribute("orderItemsCount", orderItemsCount);
        model.addAttribute("orderStatusHistory", orderHistory);
        model.addAttribute("orderStatusRuMap", controllerSupport.orderStatusRuMap());
        model.addAttribute("orderStatusClassMap", controllerSupport.orderStatusClassMap());
        model.addAttribute("tab", tab);
        model.addAttribute("status", status);
        model.addAttribute("sort", sort);
        model.addAttribute("q", q);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        return "admin/support-details";
    }

    @PostMapping("/admin/support/{id}/status")
    public String updateSupportStatus(
        @PathVariable Long id,
        @RequestParam String status,
        @RequestParam(required = false, defaultValue = "list") String returnTo,
        @RequestParam(required = false, defaultValue = "CONTACT") String tab,
        @RequestParam(required = false, defaultValue = "ALL") String listStatus,
        @RequestParam(required = false, defaultValue = "NEWEST") String sort,
        @RequestParam(required = false, defaultValue = "") String q,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        HttpServletRequest request,
        RedirectAttributes redirectAttributes
    ) {
        try {
            supportService.updateAdminStatus(id, status);
            redirectAttributes.addFlashAttribute("supportSuccess", "Статус заявки обновлён");
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("supportError", ex.getMessage());
        }
        if ("detail".equalsIgnoreCase(returnTo)) {
            redirectAttributes.addAttribute("tab", tab);
            redirectAttributes.addAttribute("status", listStatus);
            redirectAttributes.addAttribute("sort", sort);
            redirectAttributes.addAttribute("q", q);
            if (dateFrom != null) {
                redirectAttributes.addAttribute("dateFrom", dateFrom);
            }
            if (dateTo != null) {
                redirectAttributes.addAttribute("dateTo", dateTo);
            }
            return "redirect:/admin/support/" + id;
        }
        redirectAttributes.addAttribute("tab", tab);
        redirectAttributes.addAttribute("status", listStatus);
        redirectAttributes.addAttribute("sort", sort);
        redirectAttributes.addAttribute("q", q);
        if (dateFrom != null) {
            redirectAttributes.addAttribute("dateFrom", dateFrom);
        }
        if (dateTo != null) {
            redirectAttributes.addAttribute("dateTo", dateTo);
        }
        return "redirect:/admin/support";
    }

    @PostMapping("/admin/support/{id}/reply")
    public String replySupportRequest(
        @PathVariable Long id,
        @RequestParam String adminReply,
        @RequestParam(required = false, defaultValue = "false") boolean markProcessed,
        @RequestParam(required = false, defaultValue = "CONTACT") String tab,
        @RequestParam(required = false, defaultValue = "ALL") String status,
        @RequestParam(required = false, defaultValue = "NEWEST") String sort,
        @RequestParam(required = false, defaultValue = "") String q,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        HttpServletRequest request,
        RedirectAttributes redirectAttributes
    ) {
        try {
            supportService.replyByAdmin(id, adminReply, markProcessed);
            redirectAttributes.addFlashAttribute("supportSuccess", "Ответ отправлен");
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("supportError", ex.getMessage());
        }
        redirectAttributes.addAttribute("tab", tab);
        redirectAttributes.addAttribute("status", status);
        redirectAttributes.addAttribute("sort", sort);
        redirectAttributes.addAttribute("q", q);
        if (dateFrom != null) {
            redirectAttributes.addAttribute("dateFrom", dateFrom);
        }
        if (dateTo != null) {
            redirectAttributes.addAttribute("dateTo", dateTo);
        }
        return "redirect:/admin/support/" + id;
    }

    @PostMapping("/admin/support/{id}/processed")
    public String markSupportProcessed(
        @PathVariable Long id,
        HttpServletRequest request,
        RedirectAttributes redirectAttributes
    ) {
        try {
            supportService.markProcessed(id);
            redirectAttributes.addFlashAttribute("supportSuccess", "Заявка помечена как обработанная");
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("supportError", ex.getMessage());
        }
        return "redirect:/admin/support";
    }
}
