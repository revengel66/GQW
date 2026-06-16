package com.example.gqw.shop.controller;

import com.example.gqw.analytics.aop.TrackAnalyticsAttribute;
import com.example.gqw.analytics.aop.TrackAnalyticsEvent;
import com.example.gqw.shop.entity.OrderItem;
import com.example.gqw.shop.entity.OrderStatus;
import com.example.gqw.shop.entity.OrderStatusHistory;
import com.example.gqw.shop.entity.Review;
import com.example.gqw.shop.entity.ShopOrder;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.entity.SupportRequest;
import com.example.gqw.shop.service.CurrentUserService;
import com.example.gqw.shop.service.OrderService;
import com.example.gqw.shop.service.ReviewService;
import com.example.gqw.shop.service.SupportService;
import com.example.gqw.shop.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AccountController {

    private final OrderService orderService;
    private final SupportService supportService;
    private final ReviewService reviewService;
    private final UserService userService;
    private final CurrentUserService currentUserService;

    public AccountController(
        OrderService orderService,
        SupportService supportService,
        ReviewService reviewService,
        UserService userService,
        CurrentUserService currentUserService
    ) {
        this.orderService = orderService;
        this.supportService = supportService;
        this.reviewService = reviewService;
        this.userService = userService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/account")
    @TrackAnalyticsEvent(
        code = "ACCOUNT_VIEW",
        attributes = {
            @TrackAnalyticsAttribute(code = "SORT_TYPE", value = "#p7")
        }
    )
    public String account(
        @RequestParam(required = false) Boolean checkoutSuccess,
        @RequestParam(required = false) Boolean profileUpdated,
        @RequestParam(required = false) String profileError,
        @RequestParam(required = false) String orderSuccess,
        @RequestParam(required = false) String orderError,
        @RequestParam(required = false) String supportSuccess,
        @RequestParam(required = false) String supportError,
        @RequestParam(required = false, defaultValue = "profile") String tab,
        @RequestParam(required = false) Long orderForSupport,
        Model model
    ) {
        ShopUser currentUser = currentUserService.findCurrentUser()
            .orElseThrow(() -> new IllegalStateException("Требуется авторизация"));
        if (Boolean.TRUE.equals(currentUser.getIsAdmin())) {
            return "redirect:/admin";
        }
        List<ShopOrder> orders = orderService.userOrders();
        String activeAccountTab = normalizeAccountTab(tab);
        Map<Long, List<OrderItem>> orderItemsByOrderId = new LinkedHashMap<>();
        Map<Long, Integer> orderQuantityByOrderId = new LinkedHashMap<>();
        Map<Long, List<OrderStatusHistory>> orderHistoryByOrderId = new LinkedHashMap<>();
        Map<Long, Boolean> editableOrderById = new LinkedHashMap<>();
        Map<Long, Boolean> cancelableOrderById = new LinkedHashMap<>();
        for (ShopOrder order : orders) {
            List<OrderItem> orderItems = orderService.itemsForOrder(order);
            orderItemsByOrderId.put(order.getId(), orderItems);
            int totalQuantity = orderItems.stream()
                .map(OrderItem::getQuantity)
                .filter(qty -> qty != null)
                .mapToInt(Integer::intValue)
                .sum();
            orderQuantityByOrderId.put(order.getId(), totalQuantity);
            orderHistoryByOrderId.put(order.getId(), orderService.statusHistoryForOrder(order));
            editableOrderById.put(order.getId(), orderService.canEditByCustomer(order));
            cancelableOrderById.put(order.getId(), orderService.canCancelByCustomer(order));
        }
        BigDecimal totalSpent = orders.stream()
            .map(ShopOrder::getTotalAmount)
            .filter(amount -> amount != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<SupportRequest> supportRequests = supportService.userRequests();
        List<Review> accountReviews = reviewService.currentUserTopLevelReviews();
        List<Review> reviewReplies = reviewService.repliesToReviews(accountReviews);
        Map<Long, List<Review>> reviewRepliesByParentId = new LinkedHashMap<>();
        for (Review reply : reviewReplies) {
            if (reply.getParent() == null || reply.getParent().getId() == null) {
                continue;
            }
            reviewRepliesByParentId.computeIfAbsent(reply.getParent().getId(), ignored -> new ArrayList<>()).add(reply);
        }
        Instant reviewRepliesSeenAt = currentUser.getReviewRepliesSeenAt();
        long newRepliesCount = reviewReplies.stream()
            .filter(reply -> reply.getCreatedAt() != null && (reviewRepliesSeenAt == null || reply.getCreatedAt().isAfter(reviewRepliesSeenAt)))
            .count();
        if ("reviews".equals(activeAccountTab) && newRepliesCount > 0) {
            Instant now = Instant.now();
            userService.markReviewRepliesSeen(currentUser, now);
            currentUser.setReviewRepliesSeenAt(now);
            newRepliesCount = 0;
        }
        Set<Long> reviewedProductIds = accountReviews.stream()
            .filter(review -> review.getProduct() != null && review.getProduct().getId() != null)
            .map(review -> review.getProduct().getId())
            .collect(Collectors.toCollection(LinkedHashSet::new));

        model.addAttribute("orders", orders);
        model.addAttribute("orderItemsByOrderId", orderItemsByOrderId);
        model.addAttribute("orderQuantityByOrderId", orderQuantityByOrderId);
        model.addAttribute("orderHistoryByOrderId", orderHistoryByOrderId);
        model.addAttribute("editableOrderById", editableOrderById);
        model.addAttribute("cancelableOrderById", cancelableOrderById);
        model.addAttribute("orderStatusRuMap", orderStatusRuMap());
        model.addAttribute("ordersCount", orders.size());
        model.addAttribute("totalSpent", totalSpent);
        model.addAttribute("lastOrder", orders.isEmpty() ? null : orders.get(0));
        model.addAttribute("supportRequests", supportRequests);
        model.addAttribute("supportRequestsCount", supportRequests.size());
        model.addAttribute("accountReviews", accountReviews);
        model.addAttribute("reviewReplies", reviewReplies);
        model.addAttribute("reviewRepliesByParentId", reviewRepliesByParentId);
        model.addAttribute("reviewedProductIds", reviewedProductIds);
        model.addAttribute("newReviewRepliesCount", newRepliesCount);
        model.addAttribute("activeAccountTab", activeAccountTab);
        model.addAttribute("preselectedSupportOrderId", orderForSupport);
        model.addAttribute("checkoutSuccess", checkoutSuccess != null && checkoutSuccess);
        model.addAttribute("profileUpdated", profileUpdated != null && profileUpdated);
        model.addAttribute("profileError", profileError);
        model.addAttribute("orderSuccess", orderSuccess);
        model.addAttribute("orderError", orderError);
        model.addAttribute("supportSuccess", supportSuccess);
        model.addAttribute("supportError", supportError);
        return "shop/account";
    }

    @PostMapping("/account/profile")
    @TrackAnalyticsEvent(
        code = "ACCOUNT_PROFILE_UPDATE",
        attributes = {
            @TrackAnalyticsAttribute(code = "EMAIL_DOMAIN", value = "#p2 == null || !#p2.contains('@') ? null : #p2.substring(#p2.indexOf('@') + 1)")
        }
    )
    public String updateProfile(
        @RequestParam String fullName,
        @RequestParam String phone,
        @RequestParam String email,
        RedirectAttributes redirectAttributes
    ) {
        ShopUser currentUser = currentUserService.findCurrentUser()
            .orElseThrow(() -> new IllegalStateException("Требуется авторизация"));
        if (Boolean.TRUE.equals(currentUser.getIsAdmin())) {
            return "redirect:/admin";
        }
        try {
            userService.updateContacts(currentUser, fullName, phone, email);
            redirectAttributes.addAttribute("profileUpdated", true);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addAttribute("profileError", ex.getMessage());
        }
        redirectAttributes.addAttribute("tab", "profile");
        return "redirect:/account";
    }

    @PostMapping("/account/address")
    @TrackAnalyticsEvent(code = "ACCOUNT_ADDRESS_UPDATE")
    public String updateAddress(
        @RequestParam(required = false) String addressStreet,
        @RequestParam(required = false) String addressHouse,
        @RequestParam(required = false) String addressApartment,
        @RequestParam(required = false) String addressEntrance,
        @RequestParam(required = false) String addressFloor,
        @RequestParam(required = false) String addressIntercom,
        RedirectAttributes redirectAttributes
    ) {
        ShopUser currentUser = currentUserService.findCurrentUser()
            .orElseThrow(() -> new IllegalStateException("Требуется авторизация"));
        if (Boolean.TRUE.equals(currentUser.getIsAdmin())) {
            return "redirect:/admin";
        }
        try {
            userService.updateAddress(
                currentUser,
                addressStreet,
                addressHouse,
                addressApartment,
                addressEntrance,
                addressFloor,
                addressIntercom
            );
            redirectAttributes.addAttribute("profileUpdated", true);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addAttribute("profileError", ex.getMessage());
        }
        redirectAttributes.addAttribute("tab", "address");
        return "redirect:/account";
    }

    @PostMapping("/account/delete")
    @TrackAnalyticsEvent(code = "ACCOUNT_DELETE")
    public String deleteAccount(
        HttpServletRequest request,
        HttpServletResponse response,
        RedirectAttributes redirectAttributes
    ) {
        ShopUser currentUser = currentUserService.findCurrentUser()
            .orElseThrow(() -> new IllegalStateException("Требуется авторизация"));
        if (Boolean.TRUE.equals(currentUser.getIsAdmin())) {
            return "redirect:/admin";
        }
        try {
            userService.deleteAccount(currentUser);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addAttribute("profileError", ex.getMessage());
            redirectAttributes.addAttribute("tab", "profile");
            return "redirect:/account";
        }
        new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
        return "redirect:/login?accountDeleted=true";
    }

    @PostMapping("/account/orders/{orderId}/cancel")
    @TrackAnalyticsEvent(code = "ACCOUNT_ORDER_CANCEL", entityType = "'ORDER'", entityId = "#p0")
    public String cancelOrderFromAccount(@PathVariable Long orderId, RedirectAttributes redirectAttributes) {
        try {
            orderService.cancelForCurrentUser(orderId);
            redirectAttributes.addAttribute("orderSuccess", "Заказ отменён");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addAttribute("orderError", ex.getMessage());
        }
        redirectAttributes.addAttribute("tab", "orders");
        return "redirect:/account";
    }

    @PostMapping("/account/orders/{orderId}/update")
    @TrackAnalyticsEvent(
        code = "ACCOUNT_ORDER_UPDATE",
        entityType = "'ORDER'",
        entityId = "#p0",
        attributes = {
            @TrackAnalyticsAttribute(code = "DELIVERY_TYPE", value = "#p4")
        }
    )
    public String updateOrderFromAccount(
        @PathVariable Long orderId,
        @RequestParam String customerName,
        @RequestParam String customerEmail,
        @RequestParam(required = false) String customerPhone,
        @RequestParam(required = false, defaultValue = "PICKUP") String deliveryType,
        @RequestParam(required = false) LocalDate pickupDate,
        @RequestParam(required = false) LocalDate deliveryDate,
        @RequestParam(required = false) LocalTime deliveryTime,
        @RequestParam(required = false) String deliveryStreet,
        @RequestParam(required = false) String deliveryHouse,
        @RequestParam(required = false) String deliveryApartment,
        @RequestParam(required = false) String deliveryEntrance,
        @RequestParam(required = false) String deliveryFloor,
        @RequestParam(required = false) String deliveryIntercom,
        @RequestParam(required = false) List<Long> itemId,
        @RequestParam(required = false) List<Integer> itemQuantity,
        RedirectAttributes redirectAttributes
    ) {
        try {
            orderService.updateByCurrentUser(
                orderId,
                customerName,
                customerEmail,
                customerPhone,
                deliveryType,
                pickupDate,
                deliveryDate,
                deliveryTime,
                deliveryStreet,
                deliveryHouse,
                deliveryApartment,
                deliveryEntrance,
                deliveryFloor,
                deliveryIntercom,
                itemId,
                itemQuantity
            );
            redirectAttributes.addAttribute("orderSuccess", "Заказ обновлён");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addAttribute("orderError", ex.getMessage());
        }
        redirectAttributes.addAttribute("tab", "orders");
        return "redirect:/account";
    }

    @PostMapping("/account/support/create")
    @TrackAnalyticsEvent(
        code = "ACCOUNT_SUPPORT_CREATE",
        entityType = "'ORDER'",
        entityId = "#p0",
        attributes = {
            @TrackAnalyticsAttribute(code = "SUPPORT_TOPIC", value = "'ACCOUNT_SUPPORT'")
        }
    )
    public String createSupportTicketFromAccount(
        @RequestParam(required = false) Long orderId,
        @RequestParam String message,
        RedirectAttributes redirectAttributes
    ) {
        try {
            supportService.createForAccount(orderId, message);
            redirectAttributes.addAttribute("supportSuccess", "Тикет создан. Мы ответим в ближайшее время.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addAttribute("supportError", ex.getMessage());
            if (orderId != null) {
                redirectAttributes.addAttribute("orderForSupport", orderId);
            }
        }
        redirectAttributes.addAttribute("tab", "support");
        return "redirect:/account";
    }

    private static String normalizeAccountTab(String tab) {
        if (tab == null) {
            return "profile";
        }
        String normalized = tab.trim().toLowerCase();
        return switch (normalized) {
            case "profile", "address", "orders", "support", "reviews" -> normalized;
            default -> "profile";
        };
    }

    private static Map<String, String> orderStatusRuMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(OrderStatus.NEW.name(), "Новый");
        map.put(OrderStatus.ACCEPTED.name(), "Принят");
        map.put(OrderStatus.ASSEMBLED.name(), "Собран");
        map.put(OrderStatus.WAITING_PICKUP.name(), "Готов к выдаче");
        map.put(OrderStatus.DELIVERED.name(), "Доставлен");
        map.put(OrderStatus.REJECTED.name(), "Отменён");
        return map;
    }
}
