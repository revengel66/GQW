package com.example.gqw.analytics.source;

import com.example.gqw.analytics.entity.EventAttributeType;
import com.example.gqw.analytics.entity.MetricValueKind;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DemoEventAttributeTypeSource implements EventAttributeTypeSource {

    @Override
    public List<EventAttributeType> eventAttributeTypes() {
        EventAttributeType productId = new EventAttributeType();
        productId.setCode("PRODUCT_ID");
        productId.setName("Product id");
        productId.setDescription("Идентификатор товара");
        productId.setValueKind(MetricValueKind.TEXT);
        productId.setUnitDefault(null);
        productId.setIsActive(true);

        EventAttributeType categoryId = new EventAttributeType();
        categoryId.setCode("CATEGORY_ID");
        categoryId.setName("Category id");
        categoryId.setDescription("Идентификатор категории");
        categoryId.setValueKind(MetricValueKind.TEXT);
        categoryId.setUnitDefault(null);
        categoryId.setIsActive(true);

        EventAttributeType orderId = new EventAttributeType();
        orderId.setCode("ORDER_ID");
        orderId.setName("Order id");
        orderId.setDescription("Идентификатор заказа");
        orderId.setValueKind(MetricValueKind.TEXT);
        orderId.setUnitDefault(null);
        orderId.setIsActive(true);

        EventAttributeType sortType = new EventAttributeType();
        sortType.setCode("SORT_TYPE");
        sortType.setName("Sort type");
        sortType.setDescription("Тип сортировки");
        sortType.setValueKind(MetricValueKind.TEXT);
        sortType.setUnitDefault(null);
        sortType.setIsActive(true);

        return List.of(productId, categoryId, orderId, sortType);
    }
}

