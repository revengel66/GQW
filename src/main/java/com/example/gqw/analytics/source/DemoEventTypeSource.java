package com.example.gqw.analytics.source;

import com.example.gqw.analytics.entity.EventType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DemoEventTypeSource implements EventTypeSource {

    @Override
    public List<EventType> eventTypes() {
        EventType viewProduct = new EventType();
        viewProduct.setCode("VIEW_PRODUCT");
        viewProduct.setName("Просмотр товара");
        viewProduct.setDescription("Открытие страницы товара");
        viewProduct.setIsActive(true);

        EventType addToCart = new EventType();
        addToCart.setCode("ADD_TO_CART");
        addToCart.setName("Добавление в корзину");
        addToCart.setDescription("Пользователь добавил товар в корзину");
        addToCart.setIsActive(true);

        EventType addToWishlist = new EventType();
        addToWishlist.setCode("ADD_TO_WISHLIST");
        addToWishlist.setName("Добавление в избранное");
        addToWishlist.setDescription("Пользователь добавил товар в избранное");
        addToWishlist.setIsActive(true);

        EventType checkoutSubmit = new EventType();
        checkoutSubmit.setCode("CHECKOUT_SUBMIT");
        checkoutSubmit.setName("Оформление заказа");
        checkoutSubmit.setDescription("Подтверждение заказа пользователем");
        checkoutSubmit.setIsActive(true);

        return List.of(viewProduct, addToCart, addToWishlist, checkoutSubmit);
    }
}

