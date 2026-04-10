package com.example.gqw.shop.service;

import com.example.gqw.shop.entity.CartItem;
import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.repository.CartItemRepository;
import com.example.gqw.shop.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final ConcurrentMap<String, Object> cartLocks = new ConcurrentHashMap<>();

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final CurrentUserService currentUserService;

    public CartService(
        CartItemRepository cartItemRepository,
        ProductRepository productRepository,
        CurrentUserService currentUserService
    ) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public void addProduct(Long productId, int quantity, String sessionId) {
        ShopUser user = currentUserService.findCurrentUser().orElse(null);
        withCartLock(user, sessionId, productId, () -> {
            Product product = resolveProduct(productId);
            Optional<CartItem> existing = findExistingItem(user, sessionId, product);

            CartItem item = existing.orElseGet(CartItem::new);
            item.setProduct(product);
            item.setQuantity(Math.max(1, quantity));
            if (user != null) {
                item.setUser(user);
                item.setSessionId(null);
            } else {
                item.setSessionId(sessionId);
                item.setUser(null);
            }
            cartItemRepository.save(item);
            return null;
        });
    }

    @Transactional
    public void removeItem(Long itemId, String sessionId) {
        ShopUser user = currentUserService.findCurrentUser().orElse(null);
        CartItem item = user != null
            ? cartItemRepository.findByIdAndUser(itemId, user)
                .orElseThrow(() -> new IllegalArgumentException("Позиция корзины не найдена"))
            : cartItemRepository.findByIdAndSessionId(itemId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Позиция корзины не найдена"));
        cartItemRepository.delete(item);
    }

    @Transactional
    public void updateQuantity(Long itemId, int quantity, String sessionId) {
        ShopUser user = currentUserService.findCurrentUser().orElse(null);
        CartItem item = user != null
            ? cartItemRepository.findByIdAndUser(itemId, user)
                .orElseThrow(() -> new IllegalArgumentException("Позиция корзины не найдена"))
            : cartItemRepository.findByIdAndSessionId(itemId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Позиция корзины не найдена"));
        item.setQuantity(Math.max(1, quantity));
        cartItemRepository.save(item);
    }

    @Transactional
    public int incrementProduct(Long productId, String sessionId) {
        ShopUser user = currentUserService.findCurrentUser().orElse(null);
        return withCartLock(user, sessionId, productId, () -> {
            Product product = resolveProduct(productId);
            Optional<CartItem> existing = findExistingItem(user, sessionId, product);
            CartItem item = existing.orElseGet(CartItem::new);
            item.setProduct(product);
            item.setQuantity((item.getQuantity() == null ? 0 : item.getQuantity()) + 1);
            if (user != null) {
                item.setUser(user);
                item.setSessionId(null);
            } else {
                item.setUser(null);
                item.setSessionId(sessionId);
            }
            cartItemRepository.save(item);
            return item.getQuantity();
        });
    }

    @Transactional
    public int decrementProduct(Long productId, String sessionId) {
        ShopUser user = currentUserService.findCurrentUser().orElse(null);
        return withCartLock(user, sessionId, productId, () -> {
            Product product = resolveProduct(productId);
            Optional<CartItem> existing = findExistingItem(user, sessionId, product);
            if (existing.isEmpty()) {
                return 0;
            }

            CartItem item = existing.get();
            int nextQuantity = Math.max(0, (item.getQuantity() == null ? 0 : item.getQuantity()) - 1);
            if (nextQuantity == 0) {
                cartItemRepository.delete(item);
                return 0;
            }
            item.setQuantity(nextQuantity);
            cartItemRepository.save(item);
            return nextQuantity;
        });
    }

    @Transactional(readOnly = true)
    public int quantityForProduct(Long productId, String sessionId) {
        ShopUser user = currentUserService.findCurrentUser().orElse(null);
        return withCartLock(user, sessionId, productId, () -> {
            Product product = resolveProduct(productId);
            return findScopedItems(user, sessionId, product).stream()
                .map(CartItem::getQuantity)
                .filter(qty -> qty != null && qty > 0)
                .mapToInt(Integer::intValue)
                .sum();
        });
    }

    @Transactional
    public int toggleOne(Long productId, String sessionId, Integer expectedQuantity) {
        ShopUser user = currentUserService.findCurrentUser().orElse(null);
        return withCartLock(user, sessionId, productId, () -> {
            Product product = resolveProduct(productId);
            Optional<CartItem> existing = findExistingItem(user, sessionId, product);
            int actualQuantity = existing.map(CartItem::getQuantity).orElse(0);

            // Ignore stale duplicate click/request.
            if (expectedQuantity != null && expectedQuantity != actualQuantity) {
                return actualQuantity;
            }

            if (actualQuantity > 0) {
                CartItem item = existing.get();
                int nextQuantity = actualQuantity - 1;
                if (nextQuantity <= 0) {
                    cartItemRepository.delete(item);
                    return 0;
                }
                item.setQuantity(nextQuantity);
                cartItemRepository.save(item);
                return nextQuantity;
            }

            CartItem item = new CartItem();
            item.setProduct(product);
            item.setQuantity(1);
            if (user != null) {
                item.setUser(user);
                item.setSessionId(null);
            } else {
                item.setUser(null);
                item.setSessionId(sessionId);
            }
            cartItemRepository.save(item);
            return 1;
        });
    }

    @Transactional(readOnly = true)
    public List<CartItem> items(String sessionId) {
        return currentUserService.findCurrentUser()
            .map(cartItemRepository::findByUser)
            .orElseGet(() -> cartItemRepository.findBySessionId(sessionId));
    }

    @Transactional(readOnly = true)
    public BigDecimal totalAmount(String sessionId) {
        return items(sessionId).stream()
            .map(item -> item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public void clear(String sessionId) {
        List<CartItem> items = items(sessionId);
        cartItemRepository.deleteAll(items);
    }

    @Transactional
    public void mergeSessionCartToUser(String sessionId) {
        ShopUser user = currentUserService.findCurrentUser().orElse(null);
        if (user == null || sessionId == null || sessionId.isBlank()) {
            return;
        }

        List<CartItem> sessionItems = cartItemRepository.findBySessionId(sessionId);
        for (CartItem sessionItem : sessionItems) {
            Optional<CartItem> existingUserItem = cartItemRepository.findByUserAndProduct(user, sessionItem.getProduct());
            if (existingUserItem.isPresent()) {
                CartItem userItem = existingUserItem.get();
                userItem.setQuantity(userItem.getQuantity() + sessionItem.getQuantity());
                cartItemRepository.save(userItem);
                cartItemRepository.delete(sessionItem);
                continue;
            }
            sessionItem.setUser(user);
            sessionItem.setSessionId(null);
            cartItemRepository.save(sessionItem);
        }
    }

    private Product resolveProduct(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));
        if (!Boolean.TRUE.equals(product.getIsPublished())) {
            throw new IllegalArgumentException("Товар снят с публикации");
        }
        if (!Boolean.TRUE.equals(product.getInStock())) {
            throw new IllegalArgumentException("Товара сейчас нет в наличии");
        }
        return product;
    }

    private Optional<CartItem> findExistingItem(ShopUser user, String sessionId, Product product) {
        List<CartItem> scopedItems = findScopedItems(user, sessionId, product);
        if (scopedItems.isEmpty()) {
            return Optional.empty();
        }
        if (scopedItems.size() == 1) {
            return Optional.of(scopedItems.getFirst());
        }

        CartItem primary = scopedItems.getFirst();
        int maxQuantity = scopedItems.stream()
            .map(CartItem::getQuantity)
            .filter(qty -> qty != null && qty > 0)
            .mapToInt(Integer::intValue)
            .max()
            .orElse(1);
        primary.setQuantity(Math.max(1, maxQuantity));
        cartItemRepository.save(primary);
        for (int i = 1; i < scopedItems.size(); i++) {
            cartItemRepository.delete(scopedItems.get(i));
        }
        return Optional.of(primary);
    }

    private List<CartItem> findScopedItems(ShopUser user, String sessionId, Product product) {
        if (user != null) {
            return cartItemRepository.findAllByUserAndProduct(user, product);
        }
        return cartItemRepository.findAllBySessionIdAndProduct(sessionId, product);
    }

    private String cartLockKey(ShopUser user, String sessionId, Long productId) {
        String owner = user != null ? "u:" + user.getId() : "s:" + sessionId;
        return owner + "|p:" + productId;
    }

    private <T> T withCartLock(ShopUser user, String sessionId, Long productId, Supplier<T> action) {
        Object lock = cartLocks.computeIfAbsent(cartLockKey(user, sessionId, productId), key -> new Object());
        synchronized (lock) {
            return action.get();
        }
    }
}

