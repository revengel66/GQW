package com.example.gqw.shop.service;

import com.example.gqw.shop.dto.CheckoutRequest;
import com.example.gqw.shop.entity.CartItem;
import com.example.gqw.shop.entity.OrderItem;
import com.example.gqw.shop.entity.OrderStatus;
import com.example.gqw.shop.entity.OrderStatusHistory;
import com.example.gqw.shop.entity.ShopOrder;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.repository.OrderItemRepository;
import com.example.gqw.shop.repository.OrderStatusHistoryRepository;
import com.example.gqw.shop.repository.ShopOrderRepository;
import com.example.gqw.shop.repository.SupportRequestRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final LocalTime DELIVERY_TIME_MIN = LocalTime.of(10, 0);
    private static final LocalTime DELIVERY_TIME_MAX = LocalTime.of(21, 0);

    private final ShopOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final SupportRequestRepository supportRequestRepository;
    private final CartService cartService;
    private final CurrentUserService currentUserService;
    private final EmailNotificationService emailNotificationService;

    public OrderService(
        ShopOrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        OrderStatusHistoryRepository orderStatusHistoryRepository,
        SupportRequestRepository supportRequestRepository,
        CartService cartService,
        CurrentUserService currentUserService,
        EmailNotificationService emailNotificationService
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.supportRequestRepository = supportRequestRepository;
        this.cartService = cartService;
        this.currentUserService = currentUserService;
        this.emailNotificationService = emailNotificationService;
    }

    @Transactional
    public ShopOrder checkout(CheckoutRequest request, String sessionId) {
        return checkout(request, sessionId, false);
    }

    @Transactional
    public ShopOrder checkout(CheckoutRequest request, String sessionId, boolean forceDemoReservationFailure) {
        List<CartItem> cartItems = cartService.items(sessionId);
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Корзина пуста");
        }
        validateCartItemsAvailability(cartItems);

        ShopOrder order = new ShopOrder();
        ShopUser user = currentUserService.findCurrentUser().orElse(null);
        order.setUser(user);
        order.setCustomerName(request.customerName().trim());
        order.setCustomerEmail(request.customerEmail().trim());
        order.setCustomerPhone(request.customerPhone() == null ? null : request.customerPhone().trim());
        String deliveryType = request.deliveryType() == null ? "PICKUP" : request.deliveryType().trim().toUpperCase();
        if (!"PICKUP".equals(deliveryType) && !"DELIVERY".equals(deliveryType)) {
            deliveryType = "PICKUP";
        }
        order.setDeliveryType(deliveryType);
        boolean isDelivery = "DELIVERY".equals(deliveryType);
        if (isDelivery && request.deliveryDate() == null) {
            throw new IllegalStateException("Укажите дату доставки");
        }
        String deliveryStreet = isDelivery ? normalizeNullable(request.deliveryStreet()) : null;
        String deliveryHouse = isDelivery ? normalizeNullable(request.deliveryHouse()) : null;
        String deliveryApartment = isDelivery ? normalizeNullable(request.deliveryApartment()) : null;
        String deliveryEntrance = isDelivery ? normalizeNullable(request.deliveryEntrance()) : null;
        String deliveryFloor = isDelivery ? normalizeNullable(request.deliveryFloor()) : null;
        String deliveryIntercom = isDelivery ? normalizeNullable(request.deliveryIntercom()) : null;
        if (isDelivery && deliveryStreet == null) {
            throw new IllegalStateException("Укажите улицу доставки");
        }
        LocalTime deliveryTime = isDelivery ? validateDeliveryTime(request.deliveryTime()) : null;
        order.setDeliveryStreet(deliveryStreet);
        order.setDeliveryHouse(deliveryHouse);
        order.setDeliveryApartment(deliveryApartment);
        order.setDeliveryEntrance(deliveryEntrance);
        order.setDeliveryFloor(deliveryFloor);
        order.setDeliveryIntercom(deliveryIntercom);
        order.setDeliveryAddress(buildLegacyAddress(
            deliveryStreet,
            deliveryHouse,
            deliveryApartment,
            deliveryEntrance,
            deliveryFloor,
            deliveryIntercom
        ));
        order.setPickupDate(request.pickupDate());
        order.setDeliveryDate(request.deliveryDate());
        order.setDeliveryTime(deliveryTime);
        order.setStatus(OrderStatus.NEW);
        order.setPickupAddress("Главный филиал");
        if (forceDemoReservationFailure) {
            throw new IllegalStateException("Не удалось подтвердить резерв товара при оформлении заказа");
        }

        BigDecimal total = cartItems.stream()
            .map(item -> item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(total);
        order = orderRepository.save(order);
        appendStatusHistory(order, OrderStatus.NEW, "Заказ создан", "SYSTEM");

        for (CartItem cartItem : cartItems) {
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProduct(cartItem.getProduct());
            item.setProductName(cartItem.getProduct().getName());
            item.setUnitPrice(cartItem.getProduct().getPrice());
            item.setQuantity(cartItem.getQuantity());
            orderItemRepository.save(item);
        }

        cartService.clear(sessionId);
        emailNotificationService.notifyOrderStatus(order);
        return order;
    }

    private static void validateCartItemsAvailability(List<CartItem> cartItems) {
        for (CartItem cartItem : cartItems) {
            if (cartItem == null || cartItem.getProduct() == null) {
                throw new IllegalStateException("В корзине обнаружен некорректный товар");
            }
            if (!Boolean.TRUE.equals(cartItem.getProduct().getIsPublished())) {
                throw new IllegalStateException("Товар снят с публикации: " + cartItem.getProduct().getName());
            }
            if (!Boolean.TRUE.equals(cartItem.getProduct().getInStock())) {
                throw new IllegalStateException("Товар отсутствует в наличии: " + cartItem.getProduct().getName());
            }
        }
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String buildLegacyAddress(
        String street,
        String house,
        String apartment,
        String entrance,
        String floor,
        String intercom
    ) {
        StringBuilder sb = new StringBuilder();
        appendAddressPart(sb, street, "");
        appendAddressPart(sb, house, "д. ");
        appendAddressPart(sb, apartment, "кв. ");
        appendAddressPart(sb, entrance, "подъезд ");
        appendAddressPart(sb, floor, "этаж ");
        appendAddressPart(sb, intercom, "домофон ");
        return sb.isEmpty() ? null : sb.toString();
    }

    private static void appendAddressPart(StringBuilder sb, String value, String prefix) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(", ");
        }
        sb.append(prefix).append(value);
    }

    @Transactional(readOnly = true)
    public List<ShopOrder> userOrders() {
        ShopUser user = currentUserService.findCurrentUser()
            .orElseThrow(() -> new IllegalStateException("Требуется авторизация"));
        return orderRepository.findByUserOrderByCreatedAtDesc(user);
    }

    @Transactional(readOnly = true)
    public List<OrderItem> itemsForOrder(ShopOrder order) {
        return orderItemRepository.findByOrder(order);
    }

    @Transactional(readOnly = true)
    public List<OrderStatusHistory> statusHistoryForOrder(ShopOrder order) {
        return orderStatusHistoryRepository.findByOrderOrderByChangedAtAsc(order);
    }

    @Transactional(readOnly = true)
    public ShopOrder orderById(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Заказ не найден"));
    }

    @Transactional
    public ShopOrder changeStatus(Long orderId, OrderStatus status) {
        ShopOrder order = orderById(orderId);
        OrderStatus normalizedStatus = normalizeStatusForDeliveryType(order.getDeliveryType(), status);
        OrderStatus prevStatus = order.getStatus();
        order.setStatus(normalizedStatus);
        order = orderRepository.save(order);
        if (prevStatus != normalizedStatus) {
            appendStatusHistory(order, normalizedStatus, "Статус обновлён администратором", "ADMIN");
        }
        emailNotificationService.notifyOrderStatus(order);
        return order;
    }

    @Transactional
    public void deleteByAdmin(Long orderId) {
        ShopOrder order = orderById(orderId);
        supportRequestRepository.findByOrder(order).forEach(request -> {
            request.setOrder(null);
            supportRequestRepository.save(request);
        });
        orderStatusHistoryRepository.deleteByOrder(order);
        orderItemRepository.deleteByOrder(order);
        orderRepository.delete(order);
    }

    @Transactional
    public ShopOrder cancelForCurrentUser(Long orderId) {
        ShopUser user = currentUserService.findCurrentUser()
            .orElseThrow(() -> new IllegalStateException("Требуется авторизация"));
        ShopOrder order = orderRepository.findByIdAndUser(orderId, user)
            .orElseThrow(() -> new IllegalArgumentException("Заказ не найден"));
        if (!canCancelByCustomer(order)) {
            throw new IllegalArgumentException("Этот заказ уже нельзя отменить");
        }
        if (order.getStatus() == OrderStatus.REJECTED) {
            return order;
        }
        order.setStatus(OrderStatus.REJECTED);
        order = orderRepository.save(order);
        appendStatusHistory(order, OrderStatus.REJECTED, "Отменён пользователем", user.getUsername());
        emailNotificationService.notifyOrderStatus(order);
        return order;
    }

    @Transactional
    public ShopOrder updateByCurrentUser(
        Long orderId,
        String customerName,
        String customerEmail,
        String customerPhone,
        String deliveryType,
        LocalDate pickupDate,
        LocalDate deliveryDate,
        LocalTime deliveryTime,
        String deliveryStreet,
        String deliveryHouse,
        String deliveryApartment,
        String deliveryEntrance,
        String deliveryFloor,
        String deliveryIntercom,
        List<Long> itemIds,
        List<Integer> itemQuantities
    ) {
        ShopUser user = currentUserService.findCurrentUser()
            .orElseThrow(() -> new IllegalStateException("Требуется авторизация"));
        ShopOrder order = orderRepository.findByIdAndUser(orderId, user)
            .orElseThrow(() -> new IllegalArgumentException("Заказ не найден"));
        if (!canEditByCustomer(order)) {
            throw new IllegalArgumentException("Этот заказ уже нельзя изменить");
        }
        String normalizedName = customerName == null ? "" : customerName.trim();
        String normalizedEmail = customerEmail == null ? "" : customerEmail.trim();
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException("Укажите получателя");
        }
        if (normalizedEmail.isBlank()) {
            throw new IllegalArgumentException("Укажите email получателя");
        }
        order.setCustomerName(normalizedName);
        order.setCustomerEmail(normalizedEmail);
        order.setCustomerPhone(normalizeNullable(customerPhone));

        String normalizedType = deliveryType == null ? "PICKUP" : deliveryType.trim().toUpperCase();
        if (!"PICKUP".equals(normalizedType) && !"DELIVERY".equals(normalizedType)) {
            normalizedType = "PICKUP";
        }
        boolean isDelivery = "DELIVERY".equals(normalizedType);
        if (isDelivery && deliveryDate == null) {
            throw new IllegalArgumentException("Укажите дату доставки");
        }
        if (isDelivery && deliveryTime == null) {
            throw new IllegalArgumentException("Укажите время доставки");
        }
        String normalizedStreet = isDelivery ? normalizeNullable(deliveryStreet) : null;
        if (isDelivery && normalizedStreet == null) {
            throw new IllegalArgumentException("Укажите улицу доставки");
        }

        order.setDeliveryType(normalizedType);
        order.setPickupDate(isDelivery ? null : pickupDate);
        order.setDeliveryDate(isDelivery ? deliveryDate : null);
        order.setDeliveryTime(isDelivery ? validateDeliveryTime(deliveryTime) : null);
        order.setDeliveryStreet(normalizedStreet);
        order.setDeliveryHouse(isDelivery ? normalizeNullable(deliveryHouse) : null);
        order.setDeliveryApartment(isDelivery ? normalizeNullable(deliveryApartment) : null);
        order.setDeliveryEntrance(isDelivery ? normalizeNullable(deliveryEntrance) : null);
        order.setDeliveryFloor(isDelivery ? normalizeNullable(deliveryFloor) : null);
        order.setDeliveryIntercom(isDelivery ? normalizeNullable(deliveryIntercom) : null);
        order.setDeliveryAddress(buildLegacyAddress(
            order.getDeliveryStreet(),
            order.getDeliveryHouse(),
            order.getDeliveryApartment(),
            order.getDeliveryEntrance(),
            order.getDeliveryFloor(),
            order.getDeliveryIntercom()
        ));
        applyOrderItemUpdates(order, itemIds, itemQuantities);
        order = orderRepository.save(order);
        appendStatusHistory(order, order.getStatus(), "Параметры заказа обновлены пользователем", user.getUsername());
        return order;
    }

    @Transactional(readOnly = true)
    public boolean hasPurchasedProduct(ShopUser user, Long productId) {
        if (user == null || productId == null) {
            return false;
        }
        return orderRepository.findByUserOrderByCreatedAtDesc(user).stream()
            .filter(order -> order.getStatus() == OrderStatus.WAITING_PICKUP || order.getStatus() == OrderStatus.DELIVERED)
            .flatMap(order -> orderItemRepository.findByOrder(order).stream())
            .anyMatch(item -> item.getProduct() != null && productId.equals(item.getProduct().getId()));
    }

    @Transactional(readOnly = true)
    public boolean canEditByCustomer(ShopOrder order) {
        if (order == null || order.getStatus() == null) {
            return false;
        }
        return order.getStatus() == OrderStatus.NEW;
    }

    @Transactional(readOnly = true)
    public boolean canCancelByCustomer(ShopOrder order) {
        if (order == null || order.getStatus() == null) {
            return false;
        }
        if (order.getStatus() == OrderStatus.REJECTED
            || order.getStatus() == OrderStatus.WAITING_PICKUP
            || order.getStatus() == OrderStatus.DELIVERED) {
            return false;
        }
        if ("DELIVERY".equalsIgnoreCase(order.getDeliveryType())) {
            LocalDateTime deadline = resolveDeliveryDeadline(order);
            if (deadline == null) {
                return false;
            }
            return !LocalDateTime.now().isAfter(deadline);
        }
        return true;
    }

    @Transactional
    public void ensureStatusHistoryForAllOrders() {
        List<ShopOrder> orders = orderRepository.findAll();
        for (ShopOrder order : orders) {
            if (orderStatusHistoryRepository.existsByOrder(order)) {
                continue;
            }
            appendStatusHistory(order, order.getStatus(), "Статус синхронизирован", "SYSTEM");
        }
    }

    private static LocalTime validateDeliveryTime(LocalTime deliveryTime) {
        if (deliveryTime == null) {
            throw new IllegalStateException("Укажите время доставки");
        }
        if (deliveryTime.isBefore(DELIVERY_TIME_MIN) || deliveryTime.isAfter(DELIVERY_TIME_MAX)) {
            throw new IllegalStateException("Время доставки должно быть в диапазоне 10:00-21:00");
        }
        return deliveryTime.withSecond(0).withNano(0);
    }

    private static LocalDateTime resolveDeliveryDeadline(ShopOrder order) {
        if (order.getDeliveryDate() == null || order.getDeliveryTime() == null) {
            return null;
        }
        return LocalDateTime.of(order.getDeliveryDate(), order.getDeliveryTime());
    }

    private static OrderStatus normalizeStatusForDeliveryType(String deliveryType, OrderStatus status) {
        if (status == null) {
            return null;
        }
        boolean delivery = "DELIVERY".equalsIgnoreCase(deliveryType);
        if (delivery && status == OrderStatus.WAITING_PICKUP) {
            return OrderStatus.DELIVERED;
        }
        if (!delivery && status == OrderStatus.DELIVERED) {
            return OrderStatus.WAITING_PICKUP;
        }
        return status;
    }

    private void applyOrderItemUpdates(ShopOrder order, List<Long> itemIds, List<Integer> itemQuantities) {
        List<OrderItem> currentItems = orderItemRepository.findByOrder(order);
        if (currentItems.isEmpty()) {
            throw new IllegalArgumentException("В заказе нет товаров для редактирования");
        }
        if (itemIds == null || itemQuantities == null || itemIds.size() != itemQuantities.size()) {
            throw new IllegalArgumentException("Некорректные данные по товарам заказа");
        }

        Map<Long, Integer> requestedQuantities = new LinkedHashMap<>();
        for (int i = 0; i < itemIds.size(); i++) {
            Long itemId = itemIds.get(i);
            Integer quantity = itemQuantities.get(i);
            if (itemId == null) {
                throw new IllegalArgumentException("Некорректный товар заказа");
            }
            int safeQuantity = quantity == null ? 0 : quantity;
            if (safeQuantity < 0) {
                throw new IllegalArgumentException("Количество не может быть отрицательным");
            }
            if (safeQuantity > 999) {
                throw new IllegalArgumentException("Слишком большое количество товара");
            }
            requestedQuantities.put(itemId, safeQuantity);
        }

        int keepItemsCount = 0;
        BigDecimal newTotalAmount = BigDecimal.ZERO;
        for (OrderItem item : currentItems) {
            Integer qty = requestedQuantities.get(item.getId());
            if (qty == null) {
                throw new IllegalArgumentException("Передан неполный список товаров заказа");
            }
            if (qty > 0) {
                keepItemsCount++;
                newTotalAmount = newTotalAmount.add(item.getUnitPrice().multiply(BigDecimal.valueOf(qty)));
            }
        }
        if (keepItemsCount == 0) {
            throw new IllegalArgumentException("В заказе должен остаться хотя бы один товар");
        }

        for (OrderItem item : currentItems) {
            int qty = requestedQuantities.get(item.getId());
            if (qty <= 0) {
                orderItemRepository.delete(item);
                continue;
            }
            item.setQuantity(qty);
            orderItemRepository.save(item);
        }
        order.setTotalAmount(newTotalAmount);
    }

    private void appendStatusHistory(ShopOrder order, OrderStatus status, String comment, String changedBy) {
        if (order == null || status == null) {
            return;
        }
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setStatus(status);
        history.setComment(comment);
        history.setChangedBy(changedBy);
        orderStatusHistoryRepository.save(history);
    }
}

