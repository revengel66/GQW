package com.example.gqw.analytics.source;

import com.example.gqw.analytics.entity.EventType;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "app.analytics.demo-dictionary-source-enabled", havingValue = "true", matchIfMissing = false)
public class DemoEventTypeSource implements EventTypeSource {

    @Override
    public List<EventType> eventTypes() {
        EventType viewCatalog = new EventType();
        viewCatalog.setCode("CATALOG_VIEW");
        viewCatalog.setName("Просмотр каталога");
        viewCatalog.setDescription("Открытие страницы каталога");
        viewCatalog.setModuleCode(EventType.DEFAULT_MODULE_CODE);
        viewCatalog.setIsActive(true);

        EventType viewProduct = new EventType();
        viewProduct.setCode("PRODUCT_VIEW");
        viewProduct.setName("Просмотр товара");
        viewProduct.setDescription("Открытие страницы товара");
        viewProduct.setModuleCode(EventType.DEFAULT_MODULE_CODE);
        viewProduct.setIsActive(true);

        EventType viewCategory = new EventType();
        viewCategory.setCode("CATEGORY_VIEW");
        viewCategory.setName("Просмотр категории");
        viewCategory.setDescription("Открытие страницы категории");
        viewCategory.setModuleCode(EventType.DEFAULT_MODULE_CODE);
        viewCategory.setIsActive(true);

        EventType addToCart = new EventType();
        addToCart.setCode("ADD_TO_CART");
        addToCart.setName("Добавление в корзину");
        addToCart.setDescription("Пользователь добавил товар в корзину");
        addToCart.setModuleCode(EventType.DEFAULT_MODULE_CODE);
        addToCart.setIsActive(true);

        EventType addToWishlist = new EventType();
        addToWishlist.setCode("ADD_TO_WISHLIST");
        addToWishlist.setName("Добавление в избранное");
        addToWishlist.setDescription("Пользователь добавил товар в избранное");
        addToWishlist.setModuleCode(EventType.DEFAULT_MODULE_CODE);
        addToWishlist.setIsActive(true);

        EventType checkoutSubmit = new EventType();
        checkoutSubmit.setCode("CHECKOUT_SUBMIT");
        checkoutSubmit.setName("Оформление заказа");
        checkoutSubmit.setDescription("Подтверждение заказа пользователем");
        checkoutSubmit.setModuleCode(EventType.DEFAULT_MODULE_CODE);
        checkoutSubmit.setIsActive(true);

        EventType login = new EventType();
        login.setCode("LOGIN");
        login.setName("Вход в систему");
        login.setDescription("Попытка входа пользователя в систему");
        login.setModuleCode(EventType.DEFAULT_MODULE_CODE);
        login.setIsActive(true);

        EventType register = new EventType();
        register.setCode("REGISTER");
        register.setName("Регистрация");
        register.setDescription("Регистрация нового пользователя");
        register.setModuleCode(EventType.DEFAULT_MODULE_CODE);
        register.setIsActive(true);

        EventType supportRequest = new EventType();
        supportRequest.setCode("SUPPORT_REQUEST");
        supportRequest.setName("Заявка в поддержку");
        supportRequest.setDescription("Отправка заявки через форму обратной связи");
        supportRequest.setModuleCode(EventType.DEFAULT_MODULE_CODE);
        supportRequest.setIsActive(true);

        EventType reviewAdd = new EventType();
        reviewAdd.setCode("REVIEW_ADD");
        reviewAdd.setName("Добавление отзыва");
        reviewAdd.setDescription("Пользователь отправил новый отзыв");
        reviewAdd.setModuleCode(EventType.DEFAULT_MODULE_CODE);
        reviewAdd.setIsActive(true);

        EventType frontendJsError = new EventType();
        frontendJsError.setCode("FRONTEND_JS_ERROR");
        frontendJsError.setName("JavaScript error");
        frontendJsError.setDescription("Client-side JavaScript error captured by frontend analytics.");
        frontendJsError.setModuleCode(EventType.DEFAULT_MODULE_CODE);
        frontendJsError.setIsSystem(true);
        frontendJsError.setIsActive(true);

        EventType httpRequestError = new EventType();
        httpRequestError.setCode("HTTP_REQUEST_ERROR");
        httpRequestError.setName("HTTP request error");
        httpRequestError.setDescription("Technical HTTP request error captured by fallback tracking.");
        httpRequestError.setModuleCode(EventType.DEFAULT_MODULE_CODE);
        httpRequestError.setIsSystem(true);
        httpRequestError.setIsActive(true);

        return List.of(
            viewCatalog,
            viewCategory,
            viewProduct,
            addToCart,
            addToWishlist,
            checkoutSubmit,
            login,
            register,
            supportRequest,
            reviewAdd,
            frontendJsError,
            httpRequestError
        );
    }
}
