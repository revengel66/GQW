package com.example.gqw.shop.facade;

import com.example.gqw.shop.entity.Category;
import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.service.CatalogService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CatalogFacade {

    public record HomePageData(
        List<Category> featuredCategories,
        List<Product> products,
        Map<Long, List<com.example.gqw.shop.entity.ProductCharacteristic>> productCardFeatures
    ) {
    }

    public record CategoryPageData(
        Category category,
        CatalogService.CategoryCatalogData categoryData,
        CatalogService.PriceBounds priceBounds,
        Map<Long, List<com.example.gqw.shop.entity.ProductCharacteristic>> productCardFeatures,
        List<CatalogService.CategoryTreeNode> categoryTree
    ) {
    }

    private final CatalogService catalogService;

    public CatalogFacade(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    public HomePageData homePage() {
        List<Category> featuredCategories = catalogService.featuredTopCategories(4);
        List<Product> products = catalogService.latestProducts();
        return new HomePageData(
            featuredCategories,
            products,
            catalogService.cardCharacteristics(products, 3)
        );
    }

    public CategoryPageData categoryPage(
        String slug,
        int page,
        int size,
        String sort,
        String query,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        List<Long> optionIds,
        boolean inStockOnly
    ) {
        Category category = catalogService.categoryBySlug(slug);
        CatalogService.CategoryCatalogData categoryData = catalogService.categoryCatalogData(
            slug,
            page,
            size,
            sort,
            query,
            minPrice,
            maxPrice,
            optionIds,
            inStockOnly
        );
        CatalogService.PriceBounds priceBounds = catalogService.categoryPriceBounds(slug, query, optionIds, inStockOnly);
        return new CategoryPageData(
            category,
            categoryData,
            priceBounds,
            catalogService.cardCharacteristics(categoryData.pageData().getContent(), 3),
            catalogService.categoryTree()
        );
    }

    public String staticShopPage(String pageName) {
        return catalogService.staticShopPage(pageName);
    }
}
