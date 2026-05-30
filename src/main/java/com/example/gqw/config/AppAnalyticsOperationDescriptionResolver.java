package com.example.gqw.config;

import com.example.gqw.analytics.logging.AnalyticsOperationDescriptionResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AppAnalyticsOperationDescriptionResolver implements AnalyticsOperationDescriptionResolver {

    @Override
    public String resolve(String className, String methodName, String layer) {
        String key = className + "." + methodName;
        return switch (key) {
            case "CartController.addToCartApi", "CartController.addToCart" ->
                "добавление товара в корзину";
            case "CartController.toggleCartItemApi" ->
                "переключение состояния товара в корзине через API";
            case "CartController.incrementCartItemApi" ->
                "увеличение количества товара в корзине";
            case "CartController.decrementCartItemApi" ->
                "уменьшение количества товара в корзине";
            case "CartController.removeFromCart" ->
                "удаление позиции из корзины";
            case "CartController.updateCart" ->
                "обновление количества позиции корзины";
            case "CartService.addProduct" ->
                "добавление товара в корзину пользователя";
            case "CartService.toggleOne" ->
                "поштучное переключение наличия товара в корзине";
            case "CartService.incrementProduct" ->
                "поштучное увеличение товара в корзине";
            case "CartService.decrementProduct" ->
                "поштучное уменьшение товара в корзине";
            case "CartService.removeItem" ->
                "удаление позиции корзины по идентификатору";
            case "CartService.updateQuantity" ->
                "обновление количества товара в позиции корзины";
            case "OrderService.checkout" ->
                "оформление заказа";
            case "WishlistService.toggle" ->
                "переключение состояния избранного";
            case "CatalogService.productBySlug" ->
                "получение карточки товара по slug";
            case "CatalogService.categoryBySlug" ->
                "получение категории по slug";
            case "SupportService.createRequest" ->
                "создание обращения в поддержку";
            case "ReviewService.addReview" ->
                "добавление отзыва";
            default -> null;
        };
    }
}
