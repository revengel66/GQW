package com.example.gqw.admin.controller;

import com.example.gqw.analytics.aop.TrackAnalyticsEvent;
import com.example.gqw.shop.entity.OrderStatus;
import com.example.gqw.shop.entity.ShopOrder;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.admin.service.AdminService;
import com.example.gqw.shop.service.ReviewService;
import com.example.gqw.shop.service.SupportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminDashboardController {

    private final AdminService adminService;
    private final SupportService supportService;
    private final ReviewService reviewService;
    private final AdminControllerSupport controllerSupport;

    public AdminDashboardController(
        AdminService adminService,
        SupportService supportService,
        ReviewService reviewService,
        AdminControllerSupport controllerSupport
    ) {
        this.adminService = adminService;
        this.supportService = supportService;
        this.reviewService = reviewService;
        this.controllerSupport = controllerSupport;
    }

    @GetMapping("/admin")
    @TrackAnalyticsEvent(code = "DASHBOARD_VIEW")
    public String adminHome(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false, defaultValue = "") String period,
        Model model
    ) {
        List<ShopOrder> orders = adminService.orders();
        List<ShopUser> users = adminService.users();
        List<?> products = adminService.products();
        int openRequestsCount = supportService.openRequests().size();
        int pendingReviewsCount = reviewService.pendingReviews().size();
        Instant recentUsersThreshold = Instant.now().minus(7, ChronoUnit.DAYS);
        long usersCount = users.stream()
            .filter(user -> !Boolean.TRUE.equals(user.getIsAdmin()))
            .count();

        long newUsersCount = users.stream()
            .filter(user -> !Boolean.TRUE.equals(user.getIsAdmin()))
            .filter(user -> user.getCreatedAt() != null && user.getCreatedAt().isAfter(recentUsersThreshold))
            .count();
        long newOrdersCount = orders.stream()
            .filter(order -> order.getStatus() == OrderStatus.NEW)
            .count();

        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zoneId);
        LocalDate periodTo = to;
        LocalDate periodFrom = from;
        String normalizedPeriod = period == null ? "" : period.trim().toLowerCase(Locale.ROOT);
        if ((periodFrom == null || periodTo == null) && !normalizedPeriod.isBlank()) {
            if ("7d".equals(normalizedPeriod)) {
                periodTo = today;
                periodFrom = today.minusDays(6);
            } else if ("30d".equals(normalizedPeriod)) {
                periodTo = today;
                periodFrom = today.minusDays(29);
            } else if ("90d".equals(normalizedPeriod)) {
                periodTo = today;
                periodFrom = today.minusDays(89);
            }
        }
        if (periodTo == null) {
            periodTo = today;
        }
        if (periodFrom == null) {
            periodFrom = periodTo.minusDays(29);
        }
        if (periodFrom.isAfter(periodTo)) {
            LocalDate tmp = periodFrom;
            periodFrom = periodTo;
            periodTo = tmp;
        }

        Instant periodFromInclusive = periodFrom.atStartOfDay(zoneId).toInstant();
        Instant periodToExclusive = periodTo.plusDays(1).atStartOfDay(zoneId).toInstant();
        List<ShopOrder> periodOrders = new ArrayList<>();
        for (ShopOrder order : orders) {
            if (order.getCreatedAt() == null) {
                continue;
            }
            if (!order.getCreatedAt().isBefore(periodFromInclusive) && order.getCreatedAt().isBefore(periodToExclusive)) {
                periodOrders.add(order);
            }
        }

        Map<OrderStatus, Long> statusCounts = new LinkedHashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            statusCounts.put(status, 0L);
        }
        long periodCompletedOrdersCount = 0;
        long periodCancelledOrdersCount = 0;
        long periodDeliveryCount = 0;
        long periodPickupCount = 0;
        BigDecimal periodRevenue = BigDecimal.ZERO;
        for (ShopOrder order : periodOrders) {
            OrderStatus status = order.getStatus();
            if (status != null) {
                statusCounts.put(status, statusCounts.getOrDefault(status, 0L) + 1);
            }
            if ("DELIVERY".equalsIgnoreCase(order.getDeliveryType())) {
                periodDeliveryCount++;
            } else {
                periodPickupCount++;
            }
            if (status == OrderStatus.REJECTED) {
                periodCancelledOrdersCount++;
                continue;
            }
            if (controllerSupport.isSaleOrderStatus(status)) {
                periodCompletedOrdersCount++;
                if (order.getTotalAmount() != null) {
                    periodRevenue = periodRevenue.add(order.getTotalAmount());
                }
            }
        }

        long inProgressOrdersCount = Math.max(0, periodOrders.size() - periodCompletedOrdersCount - periodCancelledOrdersCount);
        BigDecimal periodAverageCheck = BigDecimal.ZERO;
        if (periodCompletedOrdersCount > 0) {
            periodAverageCheck = periodRevenue.divide(BigDecimal.valueOf(periodCompletedOrdersCount), 2, RoundingMode.HALF_UP);
        }

        long totalCompletedOrdersCount = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (ShopOrder order : orders) {
            if (controllerSupport.isSaleOrderStatus(order.getStatus())) {
                totalCompletedOrdersCount++;
                if (order.getTotalAmount() != null) {
                    totalRevenue = totalRevenue.add(order.getTotalAmount());
                }
            }
        }

        Map<String, String> statusRu = controllerSupport.orderStatusRuMap();
        List<Map<String, Object>> dashboardStatusStats = new ArrayList<>();
        long periodOrdersCount = periodOrders.size();
        for (OrderStatus status : OrderStatus.values()) {
            long count = statusCounts.getOrDefault(status, 0L);
            int percent = periodOrdersCount == 0 ? 0 : (int) Math.round((double) count * 100.0 / (double) periodOrdersCount);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", status.name());
            row.put("label", statusRu.getOrDefault(status.name(), status.name()));
            row.put("count", count);
            row.put("percent", percent);
            dashboardStatusStats.add(row);
        }

        model.addAttribute("productsCount", products.size());
        model.addAttribute("ordersCount", orders.size());
        model.addAttribute("usersCount", usersCount);
        model.addAttribute("openRequestsCount", openRequestsCount);
        model.addAttribute("pendingReviewsCount", pendingReviewsCount);
        model.addAttribute("newUsersCount", newUsersCount);
        model.addAttribute("newOrdersCount", newOrdersCount);
        model.addAttribute("newRequestsCount", openRequestsCount);
        model.addAttribute("newReviewsCount", pendingReviewsCount);
        model.addAttribute("adminUsername", adminService.resolveAdminUsername());
        model.addAttribute("dashboardFromDate", periodFrom);
        model.addAttribute("dashboardToDate", periodTo);
        model.addAttribute("dashboardPeriod", normalizedPeriod);
        model.addAttribute("periodOrdersCount", periodOrdersCount);
        model.addAttribute("periodCompletedOrdersCount", periodCompletedOrdersCount);
        model.addAttribute("periodCancelledOrdersCount", periodCancelledOrdersCount);
        model.addAttribute("periodInProgressOrdersCount", inProgressOrdersCount);
        model.addAttribute("periodRevenue", periodRevenue);
        model.addAttribute("periodAverageCheck", periodAverageCheck);
        model.addAttribute("periodDeliveryCount", periodDeliveryCount);
        model.addAttribute("periodPickupCount", periodPickupCount);
        model.addAttribute("totalCompletedOrdersCount", totalCompletedOrdersCount);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("dashboardStatusStats", dashboardStatusStats);
        return "admin/dashboard";
    }

    @PostMapping("/admin/credentials")
    @TrackAnalyticsEvent(code = "CREDENTIALS_UPDATE")
    public String updateCredentials(
        @RequestParam String username,
        @RequestParam String password,
        HttpServletRequest request,
        HttpServletResponse response,
        RedirectAttributes redirectAttributes
    ) {
        try {
            adminService.updateAdminCredentials(username, password);
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("credentialsError", ex.getMessage());
            return "redirect:/admin";
        }

        new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
        return "redirect:/login?credentialsUpdated=true";
    }
}
