package com.example.gqw.admin.controller;

import com.example.gqw.shop.entity.OrderItem;
import com.example.gqw.shop.entity.OrderStatus;
import com.example.gqw.shop.entity.ShopOrder;
import com.example.gqw.shop.entity.SupportRequest;
import com.example.gqw.admin.service.AdminService;
import com.example.gqw.shop.service.OrderService;
import com.example.gqw.shop.service.SupportService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Instant;
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
public class AdminOrdersController {

    private final AdminService adminService;
    private final OrderService orderService;
    private final SupportService supportService;
    private final AdminControllerSupport controllerSupport;

    public AdminOrdersController(
        AdminService adminService,
        OrderService orderService,
        SupportService supportService,
        AdminControllerSupport controllerSupport
    ) {
        this.adminService = adminService;
        this.orderService = orderService;
        this.supportService = supportService;
        this.controllerSupport = controllerSupport;
    }

    @GetMapping("/admin/orders")
    public String orders(
        @RequestParam(required = false, defaultValue = "ALL") String status,
        @RequestParam(required = false, defaultValue = "ALL") String type,
        @RequestParam(required = false, defaultValue = "CREATED_DESC") String sort,
        @RequestParam(required = false, defaultValue = "") String q,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        Model model
    ) {
        LocalDate normalizedFromMutable = dateFrom;
        LocalDate normalizedToMutable = dateTo;
        if (normalizedFromMutable != null && normalizedToMutable != null && normalizedFromMutable.isAfter(normalizedToMutable)) {
            LocalDate tmp = normalizedFromMutable;
            normalizedFromMutable = normalizedToMutable;
            normalizedToMutable = tmp;
        }
        final LocalDate normalizedFrom = normalizedFromMutable;
        final LocalDate normalizedTo = normalizedToMutable;

        String normalizedStatusRequested = status == null ? "ALL" : status.trim().toUpperCase(Locale.ROOT);
        OrderStatus selectedStatus = null;
        if (!"ALL".equals(normalizedStatusRequested)) {
            try {
                selectedStatus = OrderStatus.valueOf(normalizedStatusRequested);
            } catch (IllegalArgumentException ignored) {
                selectedStatus = null;
            }
        }
        String normalizedStatus = selectedStatus == null ? "ALL" : selectedStatus.name();

        String normalizedTypeRequested = type == null ? "ALL" : type.trim().toUpperCase(Locale.ROOT);
        String normalizedType = switch (normalizedTypeRequested) {
            case "DELIVERY", "PICKUP" -> normalizedTypeRequested;
            default -> "ALL";
        };

        String normalizedSortRequested = sort == null ? "CREATED_DESC" : sort.trim().toUpperCase(Locale.ROOT);
        String normalizedSort = switch (normalizedSortRequested) {
            case "CREATED_ASC", "UPDATED_DESC", "UPDATED_ASC", "AMOUNT_DESC", "AMOUNT_ASC", "STATUS_ASC", "STATUS_DESC" -> normalizedSortRequested;
            default -> "CREATED_DESC";
        };
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        ZoneId zoneId = ZoneId.systemDefault();

        final OrderStatus selectedStatusFilter = selectedStatus;
        List<ShopOrder> filteredOrders = adminService.orders().stream()
            .filter(order -> selectedStatusFilter == null || order.getStatus() == selectedStatusFilter)
            .filter(order -> {
                if ("ALL".equals(normalizedType)) {
                    return true;
                }
                return normalizedType.equalsIgnoreCase(order.getDeliveryType());
            })
            .filter(order -> {
                if (order.getCreatedAt() == null) {
                    return normalizedFrom == null && normalizedTo == null;
                }
                LocalDate createdDate = order.getCreatedAt().atZone(zoneId).toLocalDate();
                if (normalizedFrom != null && createdDate.isBefore(normalizedFrom)) {
                    return false;
                }
                if (normalizedTo != null && createdDate.isAfter(normalizedTo)) {
                    return false;
                }
                return true;
            })
            .filter(order -> {
                if (query.isBlank()) {
                    return true;
                }
                String orderId = String.valueOf(order.getId());
                String customerName = order.getCustomerName() == null ? "" : order.getCustomerName().toLowerCase(Locale.ROOT);
                String customerEmail = order.getCustomerEmail() == null ? "" : order.getCustomerEmail().toLowerCase(Locale.ROOT);
                String customerPhone = order.getCustomerPhone() == null ? "" : order.getCustomerPhone().toLowerCase(Locale.ROOT);
                String userName = order.getUser() == null || order.getUser().getUsername() == null
                    ? ""
                    : order.getUser().getUsername().toLowerCase(Locale.ROOT);
                return orderId.contains(query)
                    || customerName.contains(query)
                    || customerEmail.contains(query)
                    || customerPhone.contains(query)
                    || userName.contains(query);
            })
            .toList();

        Map<Long, Instant> orderLastStatusChangedById = new LinkedHashMap<>();
        Map<Long, Integer> orderItemCountById = new LinkedHashMap<>();
        for (ShopOrder order : filteredOrders) {
            List<com.example.gqw.shop.entity.OrderStatusHistory> history = orderService.statusHistoryForOrder(order);
            Instant lastStatusTime = history.isEmpty()
                ? (order.getUpdatedAt() != null ? order.getUpdatedAt() : order.getCreatedAt())
                : history.get(history.size() - 1).getChangedAt();
            orderLastStatusChangedById.put(order.getId(), lastStatusTime);
            int itemsCount = orderService.itemsForOrder(order).stream()
                .map(OrderItem::getQuantity)
                .filter(quantity -> quantity != null && quantity > 0)
                .mapToInt(Integer::intValue)
                .sum();
            orderItemCountById.put(order.getId(), itemsCount);
        }

        final Map<Long, Instant> lastStatusMap = orderLastStatusChangedById;
        Comparator<ShopOrder> comparator = Comparator.comparing(ShopOrder::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        if ("CREATED_ASC".equals(normalizedSort)) {
            comparator = Comparator.comparing(ShopOrder::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        } else if ("UPDATED_DESC".equals(normalizedSort)) {
            comparator = Comparator.comparing((ShopOrder order) -> lastStatusMap.get(order.getId()), Comparator.nullsLast(Comparator.reverseOrder()));
        } else if ("UPDATED_ASC".equals(normalizedSort)) {
            comparator = Comparator.comparing((ShopOrder order) -> lastStatusMap.get(order.getId()), Comparator.nullsLast(Comparator.naturalOrder()));
        } else if ("AMOUNT_DESC".equals(normalizedSort)) {
            comparator = Comparator.comparing(
                (ShopOrder order) -> order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount(),
                Comparator.reverseOrder()
            );
        } else if ("AMOUNT_ASC".equals(normalizedSort)) {
            comparator = Comparator.comparing(order -> order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount());
        } else if ("STATUS_ASC".equals(normalizedSort)) {
            comparator = Comparator.comparing(order -> order.getStatus() == null ? "" : order.getStatus().name());
        } else if ("STATUS_DESC".equals(normalizedSort)) {
            comparator = Comparator.comparing((ShopOrder order) -> order.getStatus() == null ? "" : order.getStatus().name()).reversed();
        }

        filteredOrders = filteredOrders.stream()
            .sorted(comparator.thenComparing(ShopOrder::getId, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();

        model.addAttribute("orders", filteredOrders);
        model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("orderStatusRuMap", controllerSupport.orderStatusRuMap());
        model.addAttribute("orderStatusClassMap", controllerSupport.orderStatusClassMap());
        model.addAttribute("orderStatusFilter", normalizedStatus);
        model.addAttribute("orderTypeFilter", normalizedType);
        model.addAttribute("orderSort", normalizedSort);
        model.addAttribute("orderQuery", q == null ? "" : q.trim());
        model.addAttribute("orderDateFrom", normalizedFrom);
        model.addAttribute("orderDateTo", normalizedTo);
        model.addAttribute("orderLastStatusChangedById", orderLastStatusChangedById);
        model.addAttribute("orderItemCountById", orderItemCountById);
        return "admin/orders";
    }

    @GetMapping("/admin/orders/{id}")
    public String orderDetails(
        @PathVariable Long id,
        @RequestParam(required = false, defaultValue = "ALL") String status,
        @RequestParam(required = false, defaultValue = "ALL") String type,
        @RequestParam(required = false, defaultValue = "CREATED_DESC") String sort,
        @RequestParam(required = false, defaultValue = "") String q,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        ShopOrder order;
        try {
            order = orderService.orderById(id);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("orderError", ex.getMessage());
            return "redirect:/admin/orders";
        }

        List<OrderItem> items = orderService.itemsForOrder(order);
        int totalItems = items.stream()
            .map(OrderItem::getQuantity)
            .filter(quantity -> quantity != null && quantity > 0)
            .mapToInt(Integer::intValue)
            .sum();
        List<com.example.gqw.shop.entity.OrderStatusHistory> statusHistory = orderService.statusHistoryForOrder(order);
        Instant lastStatusChangedAt = statusHistory.isEmpty()
            ? (order.getUpdatedAt() != null ? order.getUpdatedAt() : order.getCreatedAt())
            : statusHistory.get(statusHistory.size() - 1).getChangedAt();
        List<SupportRequest> orderRequests = supportService.requestsForAdmin().stream()
            .filter(request -> request.getOrder() != null && order.getId().equals(request.getOrder().getId()))
            .toList();

        model.addAttribute("order", order);
        model.addAttribute("orderItems", items);
        model.addAttribute("orderItemsCount", totalItems);
        model.addAttribute("orderStatusHistory", statusHistory);
        model.addAttribute("orderLastStatusChangedAt", lastStatusChangedAt);
        model.addAttribute("orderRequests", orderRequests);
        model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("orderStatusRuMap", controllerSupport.orderStatusRuMap());
        model.addAttribute("orderStatusClassMap", controllerSupport.orderStatusClassMap());
        model.addAttribute("orderStatusFilter", status);
        model.addAttribute("orderTypeFilter", type);
        model.addAttribute("orderSort", sort);
        model.addAttribute("orderQuery", q);
        model.addAttribute("orderDateFrom", dateFrom);
        model.addAttribute("orderDateTo", dateTo);
        return "admin/order-details";
    }

    @PostMapping("/admin/orders/{id}/status")
    public String updateOrderStatus(
        @PathVariable Long id,
        @RequestParam OrderStatus status,
        @RequestParam(required = false, defaultValue = "orders") String returnTo,
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) String tab,
        @RequestParam(required = false) String orderStatus,
        @RequestParam(required = false) String orderType,
        @RequestParam(required = false) String orderSort,
        @RequestParam(required = false) String orderQuery,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderDateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderDateTo,
        HttpServletRequest request,
        RedirectAttributes redirectAttributes
    ) {
        boolean returnToUser = "user".equalsIgnoreCase(returnTo) && userId != null;
        boolean returnToDetail = "detail".equalsIgnoreCase(returnTo);
        try {
            orderService.changeStatus(id, status);
            if (returnToUser) {
                redirectAttributes.addFlashAttribute("userSuccess", "Статус заказа обновлён");
            } else {
                redirectAttributes.addFlashAttribute("orderSuccess", "Статус заказа обновлён");
            }
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            if (returnToUser) {
                redirectAttributes.addFlashAttribute("userError", ex.getMessage());
            } else {
                redirectAttributes.addFlashAttribute("orderError", ex.getMessage());
            }
        }

        if (returnToUser) {
            redirectAttributes.addAttribute("tab", tab == null ? "orders" : tab);
            if (orderStatus != null) {
                redirectAttributes.addAttribute("orderStatus", orderStatus);
            }
            if (orderType != null) {
                redirectAttributes.addAttribute("orderType", orderType);
            }
            if (orderSort != null) {
                redirectAttributes.addAttribute("orderSort", orderSort);
            }
            if (orderQuery != null) {
                redirectAttributes.addAttribute("orderQuery", orderQuery);
            }
            return "redirect:/admin/users/" + userId;
        }

        if (returnToDetail) {
            if (orderStatus != null) {
                redirectAttributes.addAttribute("status", orderStatus);
            }
            if (orderType != null) {
                redirectAttributes.addAttribute("type", orderType);
            }
            if (orderSort != null) {
                redirectAttributes.addAttribute("sort", orderSort);
            }
            if (orderQuery != null) {
                redirectAttributes.addAttribute("q", orderQuery);
            }
            if (orderDateFrom != null) {
                redirectAttributes.addAttribute("dateFrom", orderDateFrom);
            }
            if (orderDateTo != null) {
                redirectAttributes.addAttribute("dateTo", orderDateTo);
            }
            return "redirect:/admin/orders/" + id;
        }

        if (orderStatus != null) {
            redirectAttributes.addAttribute("status", orderStatus);
        }
        if (orderType != null) {
            redirectAttributes.addAttribute("type", orderType);
        }
        if (orderSort != null) {
            redirectAttributes.addAttribute("sort", orderSort);
        }
        if (orderQuery != null) {
            redirectAttributes.addAttribute("q", orderQuery);
        }
        if (orderDateFrom != null) {
            redirectAttributes.addAttribute("dateFrom", orderDateFrom);
        }
        if (orderDateTo != null) {
            redirectAttributes.addAttribute("dateTo", orderDateTo);
        }
        return "redirect:/admin/orders";
    }

    @PostMapping("/admin/orders/{id}/delete")
    public String deleteOrder(
        @PathVariable Long id,
        @RequestParam(required = false, defaultValue = "orders") String returnTo,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String sort,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        HttpServletRequest request,
        RedirectAttributes redirectAttributes
    ) {
        try {
            orderService.deleteByAdmin(id);
            redirectAttributes.addFlashAttribute("orderSuccess", "Заказ удалён");
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("orderError", ex.getMessage());
        }
        if ("detail".equalsIgnoreCase(returnTo)) {
            return "redirect:/admin/orders";
        }
        if (status != null) {
            redirectAttributes.addAttribute("status", status);
        }
        if (type != null) {
            redirectAttributes.addAttribute("type", type);
        }
        if (sort != null) {
            redirectAttributes.addAttribute("sort", sort);
        }
        if (q != null) {
            redirectAttributes.addAttribute("q", q);
        }
        if (dateFrom != null) {
            redirectAttributes.addAttribute("dateFrom", dateFrom);
        }
        if (dateTo != null) {
            redirectAttributes.addAttribute("dateTo", dateTo);
        }
        return "redirect:/admin/orders";
    }
}
