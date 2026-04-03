package com.example.gqw.shop.service;

import com.example.gqw.shop.dto.CheckoutRequest;
import com.example.gqw.shop.entity.CartItem;
import com.example.gqw.shop.entity.OrderItem;
import com.example.gqw.shop.entity.OrderStatus;
import com.example.gqw.shop.entity.ShopOrder;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.repository.OrderItemRepository;
import com.example.gqw.shop.repository.ShopOrderRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final ShopOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartService cartService;
    private final CurrentUserService currentUserService;
    private final EmailNotificationService emailNotificationService;

    public OrderService(
        ShopOrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        CartService cartService,
        CurrentUserService currentUserService,
        EmailNotificationService emailNotificationService
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartService = cartService;
        this.currentUserService = currentUserService;
        this.emailNotificationService = emailNotificationService;
    }

    @Transactional
    public ShopOrder checkout(CheckoutRequest request, String sessionId) {
        List<CartItem> cartItems = cartService.items(sessionId);
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Корзина пуста");
        }

        ShopOrder order = new ShopOrder();
        ShopUser user = currentUserService.findCurrentUser().orElse(null);
        order.setUser(user);
        order.setCustomerName(request.customerName());
        order.setCustomerEmail(request.customerEmail());
        order.setCustomerPhone(request.customerPhone());
        order.setStatus(OrderStatus.ACCEPTED);
        order.setPickupAddress("Главный филиал");

        BigDecimal total = cartItems.stream()
            .map(item -> item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(total);
        order = orderRepository.save(order);

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

    @Transactional(readOnly = true)
    public List<ShopOrder> userOrders() {
        ShopUser user = currentUserService.findCurrentUser()
            .orElseThrow(() -> new IllegalStateException("Требуется авторизация"));
        return orderRepository.findByUserOrderByCreatedAtDesc(user);
    }

    @Transactional
    public ShopOrder changeStatus(Long orderId, OrderStatus status) {
        ShopOrder order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Заказ не найден"));
        order.setStatus(status);
        order = orderRepository.save(order);
        emailNotificationService.notifyOrderStatus(order);
        return order;
    }
}

