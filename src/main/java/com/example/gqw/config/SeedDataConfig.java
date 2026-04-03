package com.example.gqw.config;

import com.example.gqw.shop.entity.Category;
import com.example.gqw.shop.entity.FilterOption;
import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.ProductCharacteristic;
import com.example.gqw.shop.entity.ProductFilter;
import com.example.gqw.shop.entity.ProductFilterOption;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.repository.CategoryRepository;
import com.example.gqw.shop.repository.FilterOptionRepository;
import com.example.gqw.shop.repository.ProductCharacteristicRepository;
import com.example.gqw.shop.repository.ProductFilterOptionRepository;
import com.example.gqw.shop.repository.ProductFilterRepository;
import com.example.gqw.shop.repository.ProductRepository;
import com.example.gqw.shop.repository.ShopUserRepository;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SeedDataConfig {

    @Bean
    CommandLineRunner seedData(
        ShopUserRepository userRepository,
        CategoryRepository categoryRepository,
        ProductRepository productRepository,
        ProductCharacteristicRepository characteristicRepository,
        ProductFilterRepository filterRepository,
        FilterOptionRepository filterOptionRepository,
        ProductFilterOptionRepository productFilterOptionRepository,
        PasswordEncoder passwordEncoder
    ) {
        return args -> {
            if (userRepository.findByUsername("admin").isEmpty()) {
                ShopUser admin = new ShopUser();
                admin.setUsername("admin");
                admin.setEmail("admin@gqw.local");
                admin.setFullName("Администратор");
                admin.setPhone("+79990000000");
                admin.setPasswordHash(passwordEncoder.encode("admin"));
                admin.setIsAdmin(true);
                admin.setIsEnabled(true);
                userRepository.save(admin);
            }

            if (userRepository.findByUsername("user").isEmpty()) {
                ShopUser user = new ShopUser();
                user.setUsername("user");
                user.setEmail("user@gqw.local");
                user.setFullName("Тестовый пользователь");
                user.setPhone("+79990001111");
                user.setPasswordHash(passwordEncoder.encode("user"));
                user.setIsAdmin(false);
                user.setIsEnabled(true);
                userRepository.save(user);
            }

            Category laptops = upsertCategory(categoryRepository, "Ноутбуки", "laptops",
                "Мощные ноутбуки для работы и игр", "https://images.unsplash.com/photo-1517336714739-489689fd1ca8");
            Category smartphones = upsertCategory(categoryRepository, "Смартфоны", "smartphones",
                "Современные смартфоны с отличной камерой", "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9");
            Category accessories = upsertCategory(categoryRepository, "Аксессуары", "accessories",
                "Мышки, клавиатуры, зарядки и полезные аксессуары", "https://images.unsplash.com/photo-1527864550417-7fd91fc51a46");

            if (productRepository.count() == 0) {
                Product p1 = upsertProduct(productRepository, "AeroBook Pro 14", "aerobook-pro-14",
                    "Легкий ноутбук 14''", "Ультрабук с алюминиевым корпусом и длительной автономностью",
                    new BigDecimal("129990.00"), new BigDecimal("149990.00"), true, true, true);
                Product p2 = upsertProduct(productRepository, "Photon X9", "photon-x9",
                    "Флагманский смартфон", "Смартфон с OLED-дисплеем и мощной камерой",
                    new BigDecimal("89990.00"), null, true, false, false);
                Product p3 = upsertProduct(productRepository, "ClickMouse RGB", "clickmouse-rgb",
                    "Игровая мышь", "Эргономичная мышь с RGB подсветкой",
                    new BigDecimal("4990.00"), new BigDecimal("6990.00"), false, true, true);
                Product p4 = upsertProduct(productRepository, "PowerCharge 65W", "powercharge-65w",
                    "Быстрое зарядное устройство", "Компактная GaN зарядка для ноутбуков и смартфонов",
                    new BigDecimal("5990.00"), null, false, false, false);

                p1.setCategories(new HashSet<>(List.of(laptops)));
                p2.setCategories(new HashSet<>(List.of(smartphones)));
                p3.setCategories(new HashSet<>(List.of(accessories)));
                p4.setCategories(new HashSet<>(List.of(accessories, smartphones)));
                productRepository.saveAll(List.of(p1, p2, p3, p4));

                addCharacteristic(characteristicRepository, p1, "Процессор", "Intel Core Ultra 7", 1);
                addCharacteristic(characteristicRepository, p1, "ОЗУ", "16 GB", 2);
                addCharacteristic(characteristicRepository, p1, "SSD", "1 TB", 3);

                addCharacteristic(characteristicRepository, p2, "Экран", "6.7'' OLED", 1);
                addCharacteristic(characteristicRepository, p2, "Камера", "108 MP", 2);
                addCharacteristic(characteristicRepository, p2, "Память", "256 GB", 3);

                addCharacteristic(characteristicRepository, p3, "DPI", "32000", 1);
                addCharacteristic(characteristicRepository, p3, "Подключение", "USB", 2);

                ProductFilter brandFilter = upsertFilter(filterRepository, "brand", "Бренд");
                ProductFilter colorFilter = upsertFilter(filterRepository, "color", "Цвет");

                FilterOption brandAero = upsertOption(filterOptionRepository, brandFilter, "aero", "Aero");
                FilterOption brandPhoton = upsertOption(filterOptionRepository, brandFilter, "photon", "Photon");
                FilterOption colorBlack = upsertOption(filterOptionRepository, colorFilter, "black", "Черный");
                FilterOption colorSilver = upsertOption(filterOptionRepository, colorFilter, "silver", "Серебристый");

                linkOption(productFilterOptionRepository, p1, brandAero);
                linkOption(productFilterOptionRepository, p1, colorSilver);
                linkOption(productFilterOptionRepository, p2, brandPhoton);
                linkOption(productFilterOptionRepository, p2, colorBlack);
                linkOption(productFilterOptionRepository, p3, colorBlack);
            }
        };
    }

    private static Category upsertCategory(
        CategoryRepository repository,
        String name,
        String slug,
        String description,
        String imageUrl
    ) {
        Category category = repository.findBySlug(slug).orElseGet(Category::new);
        category.setName(name);
        category.setSlug(slug);
        category.setDescription(description);
        category.setImageUrl(imageUrl);
        return repository.save(category);
    }

    private static Product upsertProduct(
        ProductRepository repository,
        String name,
        String slug,
        String shortDescription,
        String description,
        BigDecimal price,
        BigDecimal oldPrice,
        boolean isNew,
        boolean isHit,
        boolean isDiscount
    ) {
        Product product = repository.findBySlug(slug).orElseGet(Product::new);
        product.setName(name);
        product.setSlug(slug);
        product.setShortDescription(shortDescription);
        product.setDescription(description);
        product.setPrice(price);
        product.setOldPrice(oldPrice);
        product.setIsNew(isNew);
        product.setIsHit(isHit);
        product.setIsDiscount(isDiscount);
        return repository.save(product);
    }

    private static void addCharacteristic(
        ProductCharacteristicRepository repository,
        Product product,
        String name,
        String value,
        int order
    ) {
        ProductCharacteristic characteristic = new ProductCharacteristic();
        characteristic.setProduct(product);
        characteristic.setName(name);
        characteristic.setValue(value);
        characteristic.setSortOrder(order);
        repository.save(characteristic);
    }

    private static ProductFilter upsertFilter(ProductFilterRepository repository, String code, String name) {
        ProductFilter filter = repository.findByCode(code).orElseGet(ProductFilter::new);
        filter.setCode(code);
        filter.setName(name);
        return repository.save(filter);
    }

    private static FilterOption upsertOption(
        FilterOptionRepository repository,
        ProductFilter filter,
        String code,
        String value
    ) {
        FilterOption option = new FilterOption();
        option.setFilter(filter);
        option.setCode(code);
        option.setValue(value);
        return repository.save(option);
    }

    private static void linkOption(
        ProductFilterOptionRepository repository,
        Product product,
        FilterOption option
    ) {
        ProductFilterOption relation = new ProductFilterOption();
        relation.setProduct(product);
        relation.setFilterOption(option);
        repository.save(relation);
    }
}

