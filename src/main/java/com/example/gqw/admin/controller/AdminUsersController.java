package com.example.gqw.admin.controller;

import com.example.gqw.analytics.aop.TrackAnalyticsEvent;
import com.example.gqw.shop.entity.OrderItem;
import com.example.gqw.shop.entity.OrderStatus;
import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.Review;
import com.example.gqw.shop.entity.ShopOrder;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.entity.SupportRequest;
import com.example.gqw.admin.service.AdminService;
import com.example.gqw.shop.service.OrderService;
import com.example.gqw.shop.service.ReviewService;
import com.example.gqw.shop.service.SupportService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminUsersController {

    private record OrderReviewGroup(Product product, List<Review> reviews) {
    }

    private final AdminService adminService;
    private final OrderService orderService;
    private final SupportService supportService;
    private final ReviewService reviewService;
    private final AdminControllerSupport controllerSupport;

    public AdminUsersController(
        AdminService adminService,
        OrderService orderService,
        SupportService supportService,
        ReviewService reviewService,
        AdminControllerSupport controllerSupport
    ) {
        this.adminService = adminService;
        this.orderService = orderService;
        this.supportService = supportService;
        this.reviewService = reviewService;
        this.controllerSupport = controllerSupport;
    }

    @GetMapping("/admin/users")
    @TrackAnalyticsEvent(code = "USER_LIST_VIEW")
    public String users(
        @RequestParam(required = false, defaultValue = "") String q,
        @RequestParam(required = false, defaultValue = "ALL") String userEnabled,
        @RequestParam(required = false, defaultValue = "NEWEST") String sort,
        @RequestParam(required = false, defaultValue = "1") Integer page,
        Model model
    ) {
        String search = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String enabledFilterRequested = userEnabled == null ? "ALL" : userEnabled.trim().toUpperCase(Locale.ROOT);
        String enabledFilter = switch (enabledFilterRequested) {
            case "ACTIVE", "BLOCKED" -> enabledFilterRequested;
            default -> "ALL";
        };
        String sortRequested = sort == null ? "NEWEST" : sort.trim().toUpperCase(Locale.ROOT);
        String sortMode = switch (sortRequested) {
            case "OLDEST", "NAME_ASC", "NAME_DESC", "ORDERS_DESC", "SPENT_DESC" -> sortRequested;
            default -> "NEWEST";
        };
        int currentPageRequested = page == null ? 1 : page;
        int pageSize = 20;

        List<ShopUser> allUsers = adminService.users().stream()
            .filter(user -> !Boolean.TRUE.equals(user.getIsAdmin()))
            .toList();
        List<ShopOrder> allOrders = adminService.orders();

        Map<Long, Integer> orderCountByUserId = new LinkedHashMap<>();
        Map<Long, BigDecimal> userTotalSpentById = new LinkedHashMap<>();
        for (ShopUser user : allUsers) {
            List<ShopOrder> userOrders = allOrders.stream()
                .filter(order -> order.getUser() != null && user.getId().equals(order.getUser().getId()))
                .toList();
            orderCountByUserId.put(user.getId(), userOrders.size());
            BigDecimal totalSpent = userOrders.stream()
                .map(order -> order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            userTotalSpentById.put(user.getId(), totalSpent);
        }

        List<ShopUser> filteredUsers = allUsers.stream()
            .filter(user -> {
                if ("ACTIVE".equals(enabledFilter)) {
                    return Boolean.TRUE.equals(user.getIsEnabled());
                }
                if ("BLOCKED".equals(enabledFilter)) {
                    return !Boolean.TRUE.equals(user.getIsEnabled());
                }
                return true;
            })
            .filter(user -> {
                if (search.isBlank()) {
                    return true;
                }
                String username = user.getUsername() == null ? "" : user.getUsername().toLowerCase(Locale.ROOT);
                String fullName = user.getFullName() == null ? "" : user.getFullName().toLowerCase(Locale.ROOT);
                String email = user.getEmail() == null ? "" : user.getEmail().toLowerCase(Locale.ROOT);
                String phone = user.getPhone() == null ? "" : user.getPhone().toLowerCase(Locale.ROOT);
                return username.contains(search) || fullName.contains(search) || email.contains(search) || phone.contains(search);
            })
            .toList();

        Comparator<ShopUser> usersComparator = Comparator.comparing(ShopUser::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        if ("OLDEST".equals(sortMode)) {
            usersComparator = Comparator.comparing(ShopUser::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        } else if ("NAME_ASC".equals(sortMode)) {
            usersComparator = Comparator.comparing(
                user -> ((user.getFullName() == null || user.getFullName().isBlank()) ? user.getUsername() : user.getFullName()).toLowerCase(Locale.ROOT),
                Comparator.nullsLast(Comparator.naturalOrder())
            );
        } else if ("NAME_DESC".equals(sortMode)) {
            usersComparator = Comparator.comparing(
                user -> ((user.getFullName() == null || user.getFullName().isBlank()) ? user.getUsername() : user.getFullName()).toLowerCase(Locale.ROOT),
                Comparator.nullsLast(Comparator.reverseOrder())
            );
        } else if ("ORDERS_DESC".equals(sortMode)) {
            usersComparator = Comparator
                .comparing((ShopUser user) -> orderCountByUserId.getOrDefault(user.getId(), 0), Comparator.reverseOrder())
                .thenComparing(ShopUser::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        } else if ("SPENT_DESC".equals(sortMode)) {
            usersComparator = Comparator
                .comparing((ShopUser user) -> userTotalSpentById.getOrDefault(user.getId(), BigDecimal.ZERO), Comparator.reverseOrder())
                .thenComparing(ShopUser::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        filteredUsers = filteredUsers.stream()
            .sorted(usersComparator)
            .toList();

        int totalUsers = filteredUsers.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalUsers / (double) pageSize));
        int currentPage = Math.max(1, Math.min(currentPageRequested, totalPages));
        int fromIndex = Math.min((currentPage - 1) * pageSize, totalUsers);
        int toIndex = Math.min(fromIndex + pageSize, totalUsers);
        List<ShopUser> users = filteredUsers.subList(fromIndex, toIndex);

        Map<Long, Integer> pageOrderCountByUserId = new LinkedHashMap<>();
        Map<Long, BigDecimal> pageTotalSpentByUserId = new LinkedHashMap<>();
        for (ShopUser user : users) {
            pageOrderCountByUserId.put(user.getId(), orderCountByUserId.getOrDefault(user.getId(), 0));
            pageTotalSpentByUserId.put(user.getId(), userTotalSpentById.getOrDefault(user.getId(), BigDecimal.ZERO));
        }

        model.addAttribute("users", users);
        model.addAttribute("userOrderCountById", pageOrderCountByUserId);
        model.addAttribute("userTotalSpentById", pageTotalSpentByUserId);
        model.addAttribute("searchQuery", q == null ? "" : q.trim());
        model.addAttribute("userEnabledFilter", enabledFilter);
        model.addAttribute("userSort", sortMode);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("hasPrevPage", currentPage > 1);
        model.addAttribute("hasNextPage", currentPage < totalPages);
        model.addAttribute("prevPage", Math.max(1, currentPage - 1));
        model.addAttribute("nextPage", Math.min(totalPages, currentPage + 1));
        model.addAttribute("firstItemIndex", totalUsers == 0 ? 0 : fromIndex + 1);
        model.addAttribute("lastItemIndex", toIndex);
        return "admin/users";
    }

    @GetMapping("/admin/users/{id}")
    @TrackAnalyticsEvent(code = "USER_VIEW")
    public String userDetails(
        @PathVariable Long id,
        @RequestParam(required = false, defaultValue = "orders") String tab,
        @RequestParam(required = false, defaultValue = "ALL") String orderStatus,
        @RequestParam(required = false, defaultValue = "ALL") String orderType,
        @RequestParam(required = false, defaultValue = "NEWEST") String orderSort,
        @RequestParam(required = false, defaultValue = "") String orderQuery,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        ShopUser user;
        try {
            user = adminService.userById(id);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("userError", ex.getMessage());
            return "redirect:/admin/users";
        }
        if (Boolean.TRUE.equals(user.getIsAdmin())) {
            redirectAttributes.addFlashAttribute("userError", "Администратор не отображается в списке пользователей");
            return "redirect:/admin/users";
        }

        String activeUserTab = tab == null ? "orders" : tab.trim().toLowerCase(Locale.ROOT);
        if (!"orders".equals(activeUserTab) && !"reviews".equals(activeUserTab) && !"requests".equals(activeUserTab)) {
            activeUserTab = "orders";
        }

        List<ShopOrder> allUserOrders = adminService.ordersByUser(user);
        List<SupportRequest> supportRequests = supportService.requestsByUser(user);
        List<Review> userReviews = reviewService.reviewsByUser(user);
        Map<Long, List<Review>> userReviewsByProductId = new LinkedHashMap<>();
        for (Review review : userReviews) {
            if (review.getProduct() == null || review.getProduct().getId() == null) {
                continue;
            }
            userReviewsByProductId.computeIfAbsent(review.getProduct().getId(), key -> new ArrayList<>()).add(review);
        }
        String normalizedStatusRequested = orderStatus == null ? "ALL" : orderStatus.trim().toUpperCase(Locale.ROOT);
        OrderStatus selectedStatus = null;
        if (!"ALL".equals(normalizedStatusRequested)) {
            try {
                selectedStatus = OrderStatus.valueOf(normalizedStatusRequested);
            } catch (IllegalArgumentException ignored) {
                selectedStatus = null;
            }
        }
        String normalizedStatus = selectedStatus == null ? "ALL" : selectedStatus.name();
        String normalizedTypeRequested = orderType == null ? "ALL" : orderType.trim().toUpperCase(Locale.ROOT);
        String normalizedType = switch (normalizedTypeRequested) {
            case "DELIVERY", "PICKUP" -> normalizedTypeRequested;
            default -> "ALL";
        };
        String normalizedSortRequested = orderSort == null ? "NEWEST" : orderSort.trim().toUpperCase(Locale.ROOT);
        String normalizedSort = switch (normalizedSortRequested) {
            case "OLDEST", "AMOUNT_DESC", "AMOUNT_ASC", "STATUS_ASC" -> normalizedSortRequested;
            default -> "NEWEST";
        };
        String query = orderQuery == null ? "" : orderQuery.trim().toLowerCase(Locale.ROOT);

        final OrderStatus selectedStatusFilter = selectedStatus;
        List<ShopOrder> orders = allUserOrders.stream()
            .filter(order -> selectedStatusFilter == null || order.getStatus() == selectedStatusFilter)
            .filter(order -> {
                if ("ALL".equals(normalizedType)) {
                    return true;
                }
                return normalizedType.equalsIgnoreCase(order.getDeliveryType());
            })
            .filter(order -> {
                if (query.isBlank()) {
                    return true;
                }
                String orderId = String.valueOf(order.getId());
                String customerName = order.getCustomerName() == null ? "" : order.getCustomerName().toLowerCase(Locale.ROOT);
                String customerEmail = order.getCustomerEmail() == null ? "" : order.getCustomerEmail().toLowerCase(Locale.ROOT);
                return orderId.contains(query) || customerName.contains(query) || customerEmail.contains(query);
            })
            .toList();

        Comparator<ShopOrder> ordersComparator = Comparator.comparing(ShopOrder::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        if ("OLDEST".equals(normalizedSort)) {
            ordersComparator = Comparator.comparing(ShopOrder::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        } else if ("AMOUNT_DESC".equals(normalizedSort)) {
            ordersComparator = Comparator.comparing(
                order -> order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount(),
                Comparator.reverseOrder()
            );
        } else if ("AMOUNT_ASC".equals(normalizedSort)) {
            ordersComparator = Comparator.comparing(order -> order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount());
        } else if ("STATUS_ASC".equals(normalizedSort)) {
            ordersComparator = Comparator.comparing(order -> order.getStatus() == null ? "" : order.getStatus().name());
        }
        orders = orders.stream()
            .sorted(ordersComparator)
            .toList();

        Map<Long, List<OrderItem>> orderItemsByOrderId = new LinkedHashMap<>();
        Map<Long, Integer> orderQuantityByOrderId = new LinkedHashMap<>();
        Map<Long, List<com.example.gqw.shop.entity.OrderStatusHistory>> orderHistoryByOrderId = new LinkedHashMap<>();
        Map<Long, List<OrderReviewGroup>> orderReviewGroupsByOrderId = new LinkedHashMap<>();
        for (ShopOrder order : orders) {
            List<OrderItem> items = orderService.itemsForOrder(order);
            orderItemsByOrderId.put(order.getId(), items);
            int totalQuantity = items.stream()
                .map(OrderItem::getQuantity)
                .filter(quantity -> quantity != null)
                .mapToInt(Integer::intValue)
                .sum();
            orderQuantityByOrderId.put(order.getId(), totalQuantity);
            orderHistoryByOrderId.put(order.getId(), orderService.statusHistoryForOrder(order));

            Map<Long, List<Review>> orderReviewsByProductId = new LinkedHashMap<>();
            Map<Long, Product> orderProductsById = new LinkedHashMap<>();
            for (OrderItem item : items) {
                if (item.getProduct() == null || item.getProduct().getId() == null) {
                    continue;
                }
                Long productId = item.getProduct().getId();
                orderProductsById.putIfAbsent(productId, item.getProduct());
                List<Review> productReviews = userReviewsByProductId.get(item.getProduct().getId());
                if (productReviews != null) {
                    orderReviewsByProductId.putIfAbsent(productId, new ArrayList<>(productReviews));
                }
            }
            List<OrderReviewGroup> reviewGroups = new ArrayList<>();
            for (Map.Entry<Long, List<Review>> entry : orderReviewsByProductId.entrySet()) {
                reviewGroups.add(new OrderReviewGroup(orderProductsById.get(entry.getKey()), entry.getValue()));
            }
            orderReviewGroupsByOrderId.put(order.getId(), reviewGroups);
        }

        BigDecimal totalSpentAll = allUserOrders.stream()
            .map(order -> order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSpentFiltered = orders.stream()
            .map(order -> order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("user", user);
        model.addAttribute("orders", orders);
        model.addAttribute("orderItemsByOrderId", orderItemsByOrderId);
        model.addAttribute("orderQuantityByOrderId", orderQuantityByOrderId);
        model.addAttribute("orderHistoryByOrderId", orderHistoryByOrderId);
        model.addAttribute("orderReviewGroupsByOrderId", orderReviewGroupsByOrderId);
        model.addAttribute("allOrdersCount", allUserOrders.size());
        model.addAttribute("filteredOrdersCount", orders.size());
        model.addAttribute("totalSpentAll", totalSpentAll);
        model.addAttribute("totalSpentFiltered", totalSpentFiltered);
        model.addAttribute("orderStatusFilter", normalizedStatus);
        model.addAttribute("orderTypeFilter", normalizedType);
        model.addAttribute("orderSort", normalizedSort);
        model.addAttribute("orderQuery", orderQuery == null ? "" : orderQuery.trim());
        model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("orderStatusRuMap", controllerSupport.orderStatusRuMap());
        model.addAttribute("supportRequests", supportRequests);
        model.addAttribute("userReviews", userReviews);
        model.addAttribute("activeUserTab", activeUserTab);
        return "admin/user-details";
    }

    @PostMapping("/admin/users/{id}/status")
    @TrackAnalyticsEvent(code = "USER_UPDATE")
    public String updateUserStatus(
        @PathVariable Long id,
        @RequestParam boolean enabled,
        @RequestParam(required = false, defaultValue = "list") String returnTo,
        @RequestParam(required = false) String tab,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String userEnabled,
        @RequestParam(required = false) String sort,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) String orderStatus,
        @RequestParam(required = false) String orderType,
        @RequestParam(required = false) String orderSort,
        @RequestParam(required = false) String orderQuery,
        HttpServletRequest request,
        RedirectAttributes redirectAttributes
    ) {
        try {
            adminService.updateUserEnabled(id, enabled);
            redirectAttributes.addFlashAttribute("userSuccess", enabled ? "Пользователь разблокирован" : "Пользователь заблокирован");
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("userError", ex.getMessage());
        }
        if ("detail".equalsIgnoreCase(returnTo)) {
            if (tab != null) {
                redirectAttributes.addAttribute("tab", tab);
            }
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
            return "redirect:/admin/users/" + id;
        }
        if (q != null) {
            redirectAttributes.addAttribute("q", q);
        }
        if (userEnabled != null) {
            redirectAttributes.addAttribute("userEnabled", userEnabled);
        }
        if (sort != null) {
            redirectAttributes.addAttribute("sort", sort);
        }
        if (page != null) {
            redirectAttributes.addAttribute("page", page);
        }
        return "redirect:/admin/users";
    }
}
