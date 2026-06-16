package com.example.gqw.config;

import com.example.gqw.shop.entity.Category;
import com.example.gqw.shop.entity.CategoryFilter;
import com.example.gqw.shop.entity.CartItem;
import com.example.gqw.shop.entity.FilterOption;
import com.example.gqw.shop.entity.OrderItem;
import com.example.gqw.shop.entity.OrderStatus;
import com.example.gqw.shop.entity.OrderStatusHistory;
import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.ProductCharacteristic;
import com.example.gqw.shop.entity.ProductFilter;
import com.example.gqw.shop.entity.ProductFilterOption;
import com.example.gqw.shop.entity.ProductImage;
import com.example.gqw.shop.entity.Review;
import com.example.gqw.shop.entity.ShopOrder;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.entity.SupportRequest;
import com.example.gqw.shop.entity.WishlistItem;
import com.example.gqw.shop.repository.CartItemRepository;
import com.example.gqw.shop.repository.CategoryFilterRepository;
import com.example.gqw.shop.repository.CategoryRepository;
import com.example.gqw.shop.repository.FilterOptionRepository;
import com.example.gqw.shop.repository.OrderItemRepository;
import com.example.gqw.shop.repository.OrderStatusHistoryRepository;
import com.example.gqw.shop.repository.ProductCharacteristicRepository;
import com.example.gqw.shop.repository.ProductFilterOptionRepository;
import com.example.gqw.shop.repository.ProductFilterRepository;
import com.example.gqw.shop.repository.ProductImageRepository;
import com.example.gqw.shop.repository.ProductRepository;
import com.example.gqw.shop.repository.ReviewRepository;
import com.example.gqw.shop.repository.ShopOrderRepository;
import com.example.gqw.shop.repository.ShopUserRepository;
import com.example.gqw.shop.repository.SupportRequestRepository;
import com.example.gqw.shop.repository.WishlistItemRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class SeedDataConfig {

    @Bean
    @Order(600)
    CommandLineRunner sanitizeProductTextsOnStartup(
        ProductRepository productRepository,
        CategoryRepository categoryRepository,
        PlatformTransactionManager transactionManager
    ) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return args -> transactionTemplate.executeWithoutResult(status -> {
            normalizeAccessoryWordInProductDescriptions(productRepository);
            normalizeCategoryImages(categoryRepository);
        });
    }

    @Bean
    @Order(700)
    @ConditionalOnProperty(value = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
    CommandLineRunner seedData(
        ShopUserRepository userRepository,
        CategoryRepository categoryRepository,
        CategoryFilterRepository categoryFilterRepository,
        ProductRepository productRepository,
        ProductCharacteristicRepository characteristicRepository,
        ProductImageRepository productImageRepository,
        CartItemRepository cartItemRepository,
        WishlistItemRepository wishlistItemRepository,
        ProductFilterRepository filterRepository,
        FilterOptionRepository filterOptionRepository,
        ProductFilterOptionRepository productFilterOptionRepository,
        ShopOrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        OrderStatusHistoryRepository orderStatusHistoryRepository,
        ReviewRepository reviewRepository,
        SupportRequestRepository supportRequestRepository,
        PasswordEncoder passwordEncoder,
        PlatformTransactionManager transactionManager
    ) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return args -> transactionTemplate.executeWithoutResult(status -> {
            ShopUser admin = userRepository.findByUsernameIgnoreCase("admin")
                .or(() -> userRepository.findFirstByIsAdminTrue())
                .orElseGet(ShopUser::new);
            admin.setUsername("admin");
            if (admin.getEmail() == null || admin.getEmail().isBlank()) {
                admin.setEmail("admin@nexora.local");
            }
            if (admin.getFullName() == null || admin.getFullName().isBlank()) {
                admin.setFullName("Администратор");
            }
            if (admin.getPhone() == null || admin.getPhone().isBlank()) {
                admin.setPhone("+79990000000");
            }
            admin.setPasswordHash(passwordEncoder.encode("admin"));
            admin.setIsAdmin(true);
            admin.setIsEnabled(true);
            ensureUserAddressParts(admin, 1);
            userRepository.save(admin);

            if (userRepository.findByUsername("user").isEmpty()) {
                ShopUser user = new ShopUser();
                user.setUsername("user");
                user.setEmail("user@nexora.local");
                user.setFullName("Тестовый пользователь");
                user.setPhone("+79990001111");
                user.setPasswordHash(passwordEncoder.encode("user"));
                user.setIsAdmin(false);
                user.setIsEnabled(true);
                ensureUserAddressParts(user, 2);
                userRepository.save(user);
            }

            Category smartphones = upsertCategory(
                categoryRepository,
                null,
                "Смартфоны",
                "smartphones",
                "Флагманские и доступные модели для связи, фото и контента",
                categoryImage("smartphones")
            );
            Category laptops = upsertCategory(
                categoryRepository,
                null,
                "Ноутбуки",
                "laptops",
                "Ультрабуки и игровые решения для работы и развлечений",
                categoryImage("laptops")
            );
            Category tablets = upsertCategory(
                categoryRepository,
                null,
                "Планшеты",
                "tablets",
                "Планшеты для учёбы, чтения и мультимедиа",
                categoryImage("tablets")
            );
            Category audio = upsertCategory(
                categoryRepository,
                null,
                "Аудио",
                "audio",
                "Наушники, колонки и аудиосистемы",
                categoryImage("audio")
            );
            Category gaming = upsertCategory(
                categoryRepository,
                null,
                "Гейминг",
                "gaming",
                "Консоли, периферия и аксессуары для игр",
                categoryImage("gaming")
            );
            Category wearables = upsertCategory(
                categoryRepository,
                null,
                "Носимая электроника",
                "wearables",
                "Умные часы и фитнес-трекеры",
                categoryImage("wearables")
            );
            Category tv = upsertCategory(
                categoryRepository,
                null,
                "Телевизоры",
                "tv",
                "Современные 4K и OLED телевизоры",
                categoryImage("tv")
            );
            Category accessories = upsertCategory(
                categoryRepository,
                null,
                "Аксессуары",
                "accessories",
                "Кабели, зарядки, хабы и полезные мелочи",
                categoryImage("accessories")
            );

            Category smartphonesAndroid = upsertCategory(
                categoryRepository,
                smartphones,
                "Android-смартфоны",
                "smartphones-android",
                "Устройства на Android",
                categoryImage("smartphones-android")
            );
            Category smartphonesIphone = upsertCategory(
                categoryRepository,
                smartphones,
                "iPhone",
                "smartphones-iphone",
                "Линейка смартфонов Apple",
                categoryImage("smartphones-iphone")
            );
            Category laptopsUltrabooks = upsertCategory(
                categoryRepository,
                laptops,
                "Ультрабуки",
                "laptops-ultrabooks",
                "Лёгкие и мобильные ноутбуки",
                categoryImage("laptops-ultrabooks")
            );
            Category laptopsGaming = upsertCategory(
                categoryRepository,
                laptops,
                "Игровые ноутбуки",
                "laptops-gaming",
                "Производительные модели для игр",
                categoryImage("laptops-gaming")
            );
            Category tabletsAndroid = upsertCategory(
                categoryRepository,
                tablets,
                "Android-планшеты",
                "tablets-android",
                "Планшеты на Android",
                categoryImage("tablets-android")
            );
            Category tabletsIpad = upsertCategory(
                categoryRepository,
                tablets,
                "iPad",
                "tablets-ipad",
                "Планшеты Apple iPad",
                categoryImage("tablets-ipad")
            );
            Category audioHeadphones = upsertCategory(
                categoryRepository,
                audio,
                "Наушники",
                "audio-headphones",
                "Проводные и беспроводные наушники",
                categoryImage("audio-headphones")
            );
            Category audioSpeakers = upsertCategory(
                categoryRepository,
                audio,
                "Колонки",
                "audio-speakers",
                "Портативные и стационарные колонки",
                categoryImage("audio-speakers")
            );
            Category gamingConsoles = upsertCategory(
                categoryRepository,
                gaming,
                "Консоли",
                "gaming-consoles",
                "Игровые приставки и аксессуары",
                categoryImage("gaming-consoles")
            );
            Category gamingPeripherals = upsertCategory(
                categoryRepository,
                gaming,
                "Игровая периферия",
                "gaming-peripherals",
                "Клавиатуры, мыши, геймпады",
                categoryImage("gaming-peripherals")
            );
            Category wearablesSmartwatch = upsertCategory(
                categoryRepository,
                wearables,
                "Умные часы",
                "wearables-smartwatch",
                "Часы с уведомлениями и трекингом",
                categoryImage("wearables-smartwatch")
            );
            Category wearablesFitness = upsertCategory(
                categoryRepository,
                wearables,
                "Фитнес-браслеты",
                "wearables-fitness",
                "Лёгкие трекеры активности",
                categoryImage("wearables-fitness")
            );
            Category tvOled = upsertCategory(
                categoryRepository,
                tv,
                "OLED телевизоры",
                "tv-oled",
                "Премиальное качество изображения",
                categoryImage("tv-oled")
            );
            Category tvQled = upsertCategory(
                categoryRepository,
                tv,
                "QLED телевизоры",
                "tv-qled",
                "Яркие модели для домашнего кинотеатра",
                categoryImage("tv-qled")
            );
            Category accessoriesChargers = upsertCategory(
                categoryRepository,
                accessories,
                "Зарядные устройства",
                "accessories-chargers",
                "Быстрые сетевые и автомобильные зарядки",
                categoryImage("accessories-chargers")
            );
            Category accessoriesCables = upsertCategory(
                categoryRepository,
                accessories,
                "Кабели и переходники",
                "accessories-cables",
                "USB-C, HDMI, Lightning и адаптеры",
                categoryImage("accessories-cables")
            );

            ProductFilter brandFilter = upsertFilter(filterRepository, "brand", "Бренд");
            ProductFilter colorFilter = upsertFilter(filterRepository, "color", "Цвет");

            FilterOption brandNexora = upsertOption(filterOptionRepository, brandFilter, "nexora", "NEXORA");
            FilterOption brandAero = upsertOption(filterOptionRepository, brandFilter, "aero", "Aero");
            FilterOption brandVoltic = upsertOption(filterOptionRepository, brandFilter, "voltic", "Voltic");
            FilterOption brandSonic = upsertOption(filterOptionRepository, brandFilter, "sonic", "Sonic");
            FilterOption brandGameX = upsertOption(filterOptionRepository, brandFilter, "gamex", "GameX");
            FilterOption brandPulse = upsertOption(filterOptionRepository, brandFilter, "pulse", "Pulse");
            FilterOption brandView = upsertOption(filterOptionRepository, brandFilter, "view", "ViewMax");
            FilterOption brandCore = upsertOption(filterOptionRepository, brandFilter, "core", "CoreLink");

            FilterOption colorBlack = upsertOption(filterOptionRepository, colorFilter, "black", "Черный");
            FilterOption colorGray = upsertOption(filterOptionRepository, colorFilter, "gray", "Графит");
            FilterOption colorSilver = upsertOption(filterOptionRepository, colorFilter, "silver", "Серебристый");

            seedCategoryProducts(
                productRepository,
                characteristicRepository,
                productImageRepository,
                productFilterOptionRepository,
                smartphones,
                List.of(smartphonesAndroid, smartphonesIphone),
                new String[]{"Nova S", "Nova S Pro", "Photon X", "Photon X Pro", "Astra One", "Astra One Max", "Pulse 9", "Pulse 9 Pro", "Orbit 11", "Orbit 11 Ultra"},
                "smartphone",
                "Смартфон",
                "Смартфоны с фокусом на камеру, скорость интерфейса и автономность",
                new BigDecimal("44990"),
                new BigDecimal("4500"),
                new String[]{
                    "/img/products/smartphone-1.webp",
                    "/img/products/smartphone-2.webp",
                    "/img/products/smartphone-3.webp"
                },
                "Экран",
                "Память",
                "Камера",
                new String[]{"6.1\" OLED", "6.3\" OLED", "6.5\" AMOLED", "6.7\" OLED", "6.8\" AMOLED", "6.2\" OLED", "6.4\" AMOLED", "6.6\" OLED", "6.7\" AMOLED", "6.9\" OLED"},
                new String[]{"8/128 ГБ", "8/256 ГБ", "12/256 ГБ", "12/512 ГБ", "12/512 ГБ", "8/128 ГБ", "8/256 ГБ", "12/256 ГБ", "12/512 ГБ", "16/512 ГБ"},
                new String[]{"50 МП", "64 МП", "108 МП", "50 МП + теле", "200 МП", "50 МП", "64 МП", "108 МП", "50 МП + теле", "200 МП"},
                brandNexora,
                colorBlack,
                colorGray
            );

            seedCategoryProducts(
                productRepository,
                characteristicRepository,
                productImageRepository,
                productFilterOptionRepository,
                laptops,
                List.of(laptopsUltrabooks, laptopsGaming),
                new String[]{"AeroBook 14", "AeroBook 15", "CoreBook Lite", "CoreBook Pro", "GameForce 16", "GameForce 17", "UltraNote Air", "UltraNote Max", "WorkStation Z", "WorkStation Z Pro"},
                "laptop",
                "Ноутбук",
                "Ноутбуки для работы, учебы и игр в едином минималистичном стиле",
                new BigDecimal("69990"),
                new BigDecimal("6500"),
                new String[]{
                    "/img/products/laptop-1.webp",
                    "/img/products/laptop-2.webp",
                    "/img/products/laptop-3.webp"
                },
                "Процессор",
                "ОЗУ",
                "SSD",
                new String[]{"Intel Core i5", "Intel Core i7", "AMD Ryzen 7", "Intel Core Ultra 7", "AMD Ryzen 9", "Intel Core i5", "Intel Core i7", "AMD Ryzen 7", "Intel Core Ultra 9", "AMD Ryzen 9"},
                new String[]{"16 ГБ", "16 ГБ", "16 ГБ", "32 ГБ", "32 ГБ", "16 ГБ", "16 ГБ", "32 ГБ", "32 ГБ", "64 ГБ"},
                new String[]{"512 ГБ", "512 ГБ", "1 ТБ", "1 ТБ", "1 ТБ", "512 ГБ", "1 ТБ", "1 ТБ", "2 ТБ", "2 ТБ"},
                brandAero,
                colorGray,
                colorSilver
            );

            seedCategoryProducts(
                productRepository,
                characteristicRepository,
                productImageRepository,
                productFilterOptionRepository,
                tablets,
                List.of(tabletsAndroid, tabletsIpad),
                new String[]{"Tab One", "Tab One Plus", "Tab Air", "Tab Air Pro", "Pad Neo", "Pad Neo Max", "Slate 10", "Slate 11", "VisionPad", "VisionPad Pro"},
                "tablet",
                "Планшет",
                "Планшеты для учёбы, заметок, медиа и повседневных задач",
                new BigDecimal("32990"),
                new BigDecimal("3500"),
                new String[]{
                    "/img/products/tablet-1.webp",
                    "/img/products/tablet-2.webp",
                    "/img/products/tablet-3.webp"
                },
                "Экран",
                "Память",
                "Батарея",
                new String[]{"10.9\" IPS", "11\" IPS", "11\" OLED", "12.1\" IPS", "12.1\" OLED", "10.9\" IPS", "11\" IPS", "11\" OLED", "12.9\" IPS", "12.9\" OLED"},
                new String[]{"6/128 ГБ", "8/128 ГБ", "8/256 ГБ", "8/256 ГБ", "12/256 ГБ", "6/128 ГБ", "8/128 ГБ", "8/256 ГБ", "12/256 ГБ", "12/512 ГБ"},
                new String[]{"7000 мАч", "7600 мАч", "8000 мАч", "8200 мАч", "9000 мАч", "7000 мАч", "7600 мАч", "8000 мАч", "9000 мАч", "9800 мАч"},
                brandVoltic,
                colorSilver,
                colorGray
            );

            seedCategoryProducts(
                productRepository,
                characteristicRepository,
                productImageRepository,
                productFilterOptionRepository,
                audio,
                List.of(audioHeadphones, audioSpeakers),
                new String[]{"WavePods Lite", "WavePods Pro", "SoundBar Mini", "SoundBar Max", "BassBeam 2", "BassBeam 3", "AirTune", "AirTune Pro", "StudioSound", "StudioSound XL"},
                "audio",
                "Аудиоустройство",
                "Персональное и домашнее аудио с акцентом на чистый звук и комфорт",
                new BigDecimal("6990"),
                new BigDecimal("1200"),
                new String[]{
                    "/img/products/audio-1.webp",
                    "/img/products/audio-2.webp",
                    "/img/products/audio-3.webp"
                },
                "Тип",
                "Подключение",
                "Автономность",
                new String[]{"TWS наушники", "TWS наушники", "Саундбар", "Саундбар", "Портативная колонка", "Портативная колонка", "Накладные наушники", "Накладные наушники", "Мониторные наушники", "Мониторные наушники"},
                new String[]{"Bluetooth 5.3", "Bluetooth 5.4", "HDMI ARC", "HDMI eARC", "Bluetooth 5.2", "Bluetooth 5.3", "Bluetooth 5.2", "Bluetooth 5.3", "3.5 мм + USB-C", "3.5 мм + USB-C"},
                new String[]{"до 24 ч", "до 30 ч", "до 10 ч", "до 12 ч", "до 16 ч", "до 18 ч", "до 40 ч", "до 50 ч", "до 32 ч", "до 36 ч"},
                brandSonic,
                colorBlack,
                colorSilver
            );

            seedCategoryProducts(
                productRepository,
                characteristicRepository,
                productImageRepository,
                productFilterOptionRepository,
                gaming,
                List.of(gamingConsoles, gamingPeripherals),
                new String[]{"PlayBox S", "PlayBox X", "GamePad Pro", "GamePad Pro 2", "StrikeMouse", "StrikeMouse X", "MechaKeys TKL", "MechaKeys Full", "PulseHeadset", "PulseHeadset Pro"},
                "gaming",
                "Игровое устройство",
                "Гейминг-устройства для высокой отзывчивости и стабильной производительности",
                new BigDecimal("15990"),
                new BigDecimal("2800"),
                new String[]{
                    "/img/products/gaming-1.webp",
                    "/img/products/gaming-2.webp",
                    "/img/products/gaming-3.webp"
                },
                "Тип",
                "Ключевая характеристика",
                "Подключение",
                new String[]{"Консоль", "Консоль", "Геймпад", "Геймпад", "Игровая мышь", "Игровая мышь", "Клавиатура TKL", "Клавиатура Full", "Гарнитура", "Гарнитура"},
                new String[]{"1 ТБ SSD", "2 ТБ SSD", "Частота 1000 Гц", "Частота 2000 Гц", "DPI 26000", "DPI 32000", "HotSwap", "HotSwap", "7.1 звук", "7.1 звук + ANC"},
                new String[]{"Wi-Fi 6", "Wi-Fi 6E", "2.4G + BT", "2.4G + BT", "USB", "USB + BT", "USB-C", "USB-C", "USB-C", "USB-C"},
                brandGameX,
                colorBlack,
                colorGray
            );

            seedCategoryProducts(
                productRepository,
                characteristicRepository,
                productImageRepository,
                productFilterOptionRepository,
                wearables,
                List.of(wearablesSmartwatch, wearablesFitness),
                new String[]{"Pulse Watch", "Pulse Watch Pro", "FitBand A", "FitBand A+", "Active One", "Active One Pro", "TrackGo", "TrackGo Max", "RunSense", "RunSense Pro"},
                "wearable",
                "Носимое устройство",
                "Смарт-часы и браслеты для уведомлений, спорта и повседневного контроля активности",
                new BigDecimal("9990"),
                new BigDecimal("1300"),
                new String[]{
                    "/img/products/wearable-1.webp",
                    "/img/products/wearable-2.webp",
                    "/img/products/wearable-3.webp"
                },
                "Экран",
                "Автономность",
                "Защита",
                new String[]{"1.6\" AMOLED", "1.8\" AMOLED", "1.3\" OLED", "1.5\" OLED", "1.7\" AMOLED", "1.9\" AMOLED", "1.4\" OLED", "1.6\" OLED", "1.8\" AMOLED", "1.9\" AMOLED"},
                new String[]{"до 7 дней", "до 10 дней", "до 12 дней", "до 14 дней", "до 8 дней", "до 10 дней", "до 12 дней", "до 14 дней", "до 9 дней", "до 12 дней"},
                new String[]{"5 ATM", "5 ATM", "IP68", "IP68", "5 ATM", "5 ATM", "IP68", "IP68", "5 ATM", "5 ATM"},
                brandPulse,
                colorGray,
                colorBlack
            );

            seedCategoryProducts(
                productRepository,
                characteristicRepository,
                productImageRepository,
                productFilterOptionRepository,
                tv,
                List.of(tvOled, tvQled),
                new String[]{"View 50", "View 55", "View 65", "View 75", "Cine OLED 55", "Cine OLED 65", "QVision 55", "QVision 65", "QVision 75", "Cine OLED 77"},
                "tv",
                "Телевизор",
                "Телевизоры для домашнего кинотеатра с современными панелями и высокой яркостью",
                new BigDecimal("54990"),
                new BigDecimal("8500"),
                new String[]{
                    "/img/products/tv-1.webp",
                    "/img/products/tv-2.webp",
                    "/img/products/tv-3.webp"
                },
                "Диагональ",
                "Матрица",
                "Частота",
                new String[]{"50\"", "55\"", "65\"", "75\"", "55\"", "65\"", "55\"", "65\"", "75\"", "77\""},
                new String[]{"QLED", "QLED", "QLED", "QLED", "OLED", "OLED", "QLED", "QLED", "QLED", "OLED"},
                new String[]{"120 Гц", "120 Гц", "120 Гц", "120 Гц", "120 Гц", "120 Гц", "144 Гц", "144 Гц", "144 Гц", "120 Гц"},
                brandView,
                colorBlack,
                colorGray
            );

            seedCategoryProducts(
                productRepository,
                characteristicRepository,
                productImageRepository,
                productFilterOptionRepository,
                accessories,
                List.of(accessoriesChargers, accessoriesCables),
                new String[]{"Charge 35W", "Charge 65W", "Charge 100W", "Cable C-C", "Cable C-L", "Dock USB-C", "Hub 7-in-1", "Stand Mag", "Case Flex", "PowerBank 20K"},
                "accessory",
                "Устройство",
                "Практичные решения для зарядки, подключения и защиты техники",
                new BigDecimal("1990"),
                new BigDecimal("450"),
                new String[]{
                    "/img/products/accessory-1.webp",
                    "/img/products/accessory-2.webp",
                    "/img/products/accessory-3.webp"
                },
                "Тип",
                "Мощность/скорость",
                "Стандарт",
                new String[]{"СЗУ", "СЗУ", "СЗУ", "Кабель", "Кабель", "Док-станция", "USB-хаб", "Подставка", "Чехол", "Повербанк"},
                new String[]{"35 Вт", "65 Вт", "100 Вт", "60 Вт", "27 Вт", "10 Гбит/с", "10 Гбит/с", "MagSafe", "ShockProof", "22.5 Вт"},
                new String[]{"USB-C PD", "USB-C PD", "USB-C PD", "USB 2.0", "Lightning", "USB-C", "USB-C", "Qi2", "TPU", "Li-Pol"},
                brandCore,
                colorBlack,
                colorSilver
            );

            reassignLegacyProductCategories(productRepository, "aerobook-pro-14", List.of(laptopsUltrabooks));
            reassignLegacyProductCategories(productRepository, "photon-x9", List.of(smartphonesIphone));
            reassignLegacyProductCategories(productRepository, "clickmouse-rgb", List.of(gamingPeripherals));
            reassignLegacyProductCategories(productRepository, "powercharge-65w", List.of(accessoriesChargers));
            normalizeLegacyProductImage(productRepository, productImageRepository, "aerobook-pro-14", "/img/products/laptop-1.webp");
            normalizeLegacyProductImage(productRepository, productImageRepository, "photon-x9", "/img/products/smartphone-1.webp");
            normalizeLegacyProductImage(productRepository, productImageRepository, "clickmouse-rgb", "/img/products/gaming-2.webp");
            normalizeLegacyProductImage(productRepository, productImageRepository, "powercharge-65w", "/img/products/accessory-1.webp");

            List<Category> smartphoneGroup = List.of(smartphones, smartphonesAndroid, smartphonesIphone);
            List<Category> laptopGroup = List.of(laptops, laptopsUltrabooks, laptopsGaming);
            List<Category> tabletGroup = List.of(tablets, tabletsAndroid, tabletsIpad);
            List<Category> audioGroup = List.of(audio, audioHeadphones, audioSpeakers);
            List<Category> gamingGroup = List.of(gaming, gamingConsoles, gamingPeripherals);
            List<Category> wearableGroup = List.of(wearables, wearablesSmartwatch, wearablesFitness);
            List<Category> tvGroup = List.of(tv, tvOled, tvQled);
            List<Category> accessoryGroup = List.of(accessories, accessoriesChargers, accessoriesCables);
            List<Category> allCategories = List.of(
                smartphones, smartphonesAndroid, smartphonesIphone,
                laptops, laptopsUltrabooks, laptopsGaming,
                tablets, tabletsAndroid, tabletsIpad,
                audio, audioHeadphones, audioSpeakers,
                gaming, gamingConsoles, gamingPeripherals,
                wearables, wearablesSmartwatch, wearablesFitness,
                tv, tvOled, tvQled,
                accessories, accessoriesChargers, accessoriesCables
            );

            ProductFilter priceFilter = upsertFilter(filterRepository, "price", "Цена", "NUMBER", "SLIDER", false, true);
            ProductFilter isNewFilter = upsertFilter(filterRepository, "is_new", "Новинки", "LIST", "CHECKBOX", false, true);
            ProductFilter isHitFilter = upsertFilter(filterRepository, "is_hit", "Хит продаж", "LIST", "CHECKBOX", false, true);
            ProductFilter isDiscountFilter = upsertFilter(filterRepository, "is_discount", "Со скидкой", "LIST", "CHECKBOX", false, true);

            ProductFilter screenFilter = upsertFilter(filterRepository, "screen", "Экран", "LIST", "CHECKBOX", false, false);
            ProductFilter memoryFilter = upsertFilter(filterRepository, "memory", "Память", "LIST", "CHECKBOX", false, false);
            ProductFilter cameraFilter = upsertFilter(filterRepository, "camera", "Камера", "LIST", "CHECKBOX", false, false);
            ProductFilter processorFilter = upsertFilter(filterRepository, "processor", "Процессор", "LIST", "CHECKBOX", false, false);
            ProductFilter ramFilter = upsertFilter(filterRepository, "ram", "ОЗУ", "LIST", "CHECKBOX", false, false);
            ProductFilter ssdFilter = upsertFilter(filterRepository, "ssd", "SSD", "LIST", "CHECKBOX", false, false);
            ProductFilter batteryFilter = upsertFilter(filterRepository, "battery", "Автономность / батарея", "LIST", "CHECKBOX", false, false);
            ProductFilter matrixFilter = upsertFilter(filterRepository, "matrix", "Матрица", "LIST", "CHECKBOX", false, false);
            ProductFilter refreshFilter = upsertFilter(filterRepository, "refresh_rate", "Частота", "LIST", "CHECKBOX", false, false);
            ProductFilter typeFilter = upsertFilter(filterRepository, "device_type", "Тип устройства", "LIST", "CHECKBOX", true, false);
            ProductFilter connectivityFilter = upsertFilter(filterRepository, "connectivity", "Подключение / стандарт", "LIST", "CHECKBOX", true, false);

            linkFiltersToCategories(categoryFilterRepository, allCategories, priceFilter, brandFilter, colorFilter, isNewFilter, isHitFilter, isDiscountFilter);
            linkFiltersToCategories(categoryFilterRepository, smartphoneGroup, screenFilter, memoryFilter, cameraFilter);
            linkFiltersToCategories(categoryFilterRepository, laptopGroup, processorFilter, ramFilter, ssdFilter);
            linkFiltersToCategories(categoryFilterRepository, tabletGroup, screenFilter, memoryFilter, batteryFilter);
            linkFiltersToCategories(categoryFilterRepository, audioGroup, typeFilter, connectivityFilter, batteryFilter);
            linkFiltersToCategories(categoryFilterRepository, gamingGroup, typeFilter, connectivityFilter);
            linkFiltersToCategories(categoryFilterRepository, wearableGroup, screenFilter, batteryFilter);
            linkFiltersToCategories(categoryFilterRepository, tvGroup, matrixFilter, refreshFilter, screenFilter);
            linkFiltersToCategories(categoryFilterRepository, accessoryGroup, typeFilter, connectivityFilter);

            syncFilterByCharacteristic(productRepository, characteristicRepository, filterOptionRepository, productFilterOptionRepository, screenFilter, smartphoneGroup, "Экран");
            syncFilterByCharacteristic(productRepository, characteristicRepository, filterOptionRepository, productFilterOptionRepository, memoryFilter, smartphoneGroup, "Память");
            syncFilterByCharacteristic(productRepository, characteristicRepository, filterOptionRepository, productFilterOptionRepository, cameraFilter, smartphoneGroup, "Камера");

            syncFilterByCharacteristic(productRepository, characteristicRepository, filterOptionRepository, productFilterOptionRepository, processorFilter, laptopGroup, "Процессор");
            syncFilterByCharacteristic(productRepository, characteristicRepository, filterOptionRepository, productFilterOptionRepository, ramFilter, laptopGroup, "ОЗУ");
            syncFilterByCharacteristic(productRepository, characteristicRepository, filterOptionRepository, productFilterOptionRepository, ssdFilter, laptopGroup, "SSD");

            syncFilterByCharacteristic(productRepository, characteristicRepository, filterOptionRepository, productFilterOptionRepository, screenFilter, tabletGroup, "Экран");
            syncFilterByCharacteristic(productRepository, characteristicRepository, filterOptionRepository, productFilterOptionRepository, memoryFilter, tabletGroup, "Память");
            syncFilterByCharacteristic(productRepository, characteristicRepository, filterOptionRepository, productFilterOptionRepository, batteryFilter, tabletGroup, "Батарея");

            syncFilterByCharacteristic(productRepository, characteristicRepository, filterOptionRepository, productFilterOptionRepository, typeFilter, audioGroup, "Тип");
            syncFilterByCharacteristic(productRepository, characteristicRepository, filterOptionRepository, productFilterOptionRepository, connectivityFilter, audioGroup, "Подключение");
            syncFilterByCharacteristic(productRepository, characteristicRepository, filterOptionRepository, productFilterOptionRepository, batteryFilter, audioGroup, "Автономность");

            syncFilterByCharacteristic(productRepository, characteristicRepository, filterOptionRepository, productFilterOptionRepository, typeFilter, gamingGroup, "Тип");
            syncFilterByCharacteristic(productRepository, characteristicRepository, filterOptionRepository, productFilterOptionRepository, connectivityFilter, gamingGroup, "Подключение");

            syncFilterByCharacteristic(productRepository, characteristicRepository, filterOptionRepository, productFilterOptionRepository, screenFilter, wearableGroup, "Экран");
            syncFilterByCharacteristic(productRepository, characteristicRepository, filterOptionRepository, productFilterOptionRepository, batteryFilter, wearableGroup, "Автономность");

            syncFilterByCharacteristic(productRepository, characteristicRepository, filterOptionRepository, productFilterOptionRepository, screenFilter, tvGroup, "Диагональ");
            syncFilterByCharacteristic(productRepository, characteristicRepository, filterOptionRepository, productFilterOptionRepository, matrixFilter, tvGroup, "Матрица");
            syncFilterByCharacteristic(productRepository, characteristicRepository, filterOptionRepository, productFilterOptionRepository, refreshFilter, tvGroup, "Частота");

            syncFilterByCharacteristic(productRepository, characteristicRepository, filterOptionRepository, productFilterOptionRepository, typeFilter, accessoryGroup, "Тип");
            syncFilterByCharacteristic(productRepository, characteristicRepository, filterOptionRepository, productFilterOptionRepository, connectivityFilter, accessoryGroup, "Стандарт");

            FilterOption flagYes = upsertOption(filterOptionRepository, isNewFilter, "yes", "Да");
            FilterOption hitYes = upsertOption(filterOptionRepository, isHitFilter, "yes", "Да");
            FilterOption discountYes = upsertOption(filterOptionRepository, isDiscountFilter, "yes", "Да");
            linkFlagOption(productRepository, productFilterOptionRepository, flagYes, "NEW");
            linkFlagOption(productRepository, productFilterOptionRepository, hitYes, "HIT");
            linkFlagOption(productRepository, productFilterOptionRepository, discountYes, "DISCOUNT");

            ShopUser user = upsertUser(
                userRepository,
                passwordEncoder,
                "user",
                "user@nexora.local",
                "Тестовый пользователь",
                "+79990001111",
                false
            );
            ShopUser elena = upsertUser(
                userRepository,
                passwordEncoder,
                "elena",
                "elena@nexora.local",
                "Елена Смирнова",
                "+79995551122",
                false
            );
            ShopUser ivan = upsertUser(
                userRepository,
                passwordEncoder,
                "ivan",
                "ivan@nexora.local",
                "Иван Петров",
                "+79995553344",
                false
            );
            ShopUser anna = upsertUser(
                userRepository,
                passwordEncoder,
                "anna",
                "anna@nexora.local",
                "Анна Волкова",
                "+79995554433",
                false
            );
            ShopUser ivanov = upsertUser(
                userRepository,
                passwordEncoder,
                "ivanov",
                "ivanov@nexora.local",
                "Иванов Иван",
                "+79995556677",
                false
            );
            ivanov.setPasswordHash(passwordEncoder.encode("ivanov"));
            ivanov = userRepository.save(ivanov);

            populateMissingUserAddressParts(userRepository);

            List<ShopUser> demoUsers = List.of(user, elena, ivan, anna);
            seedDemoOrders(orderRepository, orderItemRepository, productRepository.findAll(), demoUsers);
            synchronizeOrderStatusHistory(orderRepository, orderStatusHistoryRepository);
            seedDemoReviews(reviewRepository, productRepository.findAll(), demoUsers, admin);
            seedIvanovShowcaseScenario(
                orderRepository,
                orderItemRepository,
                orderStatusHistoryRepository,
                reviewRepository,
                userRepository,
                productRepository.findAll(),
                ivanov,
                List.of(admin, user, elena, ivan, anna)
            );
            recalculateProductRatings(reviewRepository, productRepository);
            seedSupportRequests(supportRequestRepository, demoUsers);
            normalizeAccessoryWordInProductDescriptions(productRepository);
            seedDemoCartAndWishlist(cartItemRepository, wishlistItemRepository, productRepository.findAll(), demoUsers);
        });
    }

    private static Category upsertCategory(
        CategoryRepository repository,
        Category parent,
        String name,
        String slug,
        String description,
        String imageUrl
    ) {
        Category category = repository.findBySlug(slug)
            .or(() -> repository.findByName(name))
            .orElseGet(Category::new);
        category.setParent(parent);
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
        String imageUrl,
        BigDecimal price,
        BigDecimal oldPrice,
        boolean isNew,
        boolean isHit,
        boolean isDiscount
    ) {
        Product product = repository.findBySlug(slug)
            .or(() -> repository.findByName(name))
            .orElseGet(Product::new);
        product.setName(name);
        product.setSlug(slug);
        product.setShortDescription(shortDescription);
        product.setDescription(description);
        product.setImageUrl(imageUrl);
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
        return upsertFilter(repository, code, name, "LIST", "CHECKBOX", true, false);
    }

    private static ProductFilter upsertFilter(
        ProductFilterRepository repository,
        String code,
        String name,
        String valueType,
        String viewType,
        boolean multiValue,
        boolean systemFilter
    ) {
        ProductFilter filter = repository.findByCode(code).orElseGet(ProductFilter::new);
        filter.setCode(code);
        filter.setName(name);
        filter.setValueType(valueType);
        filter.setViewType(viewType);
        filter.setMultiValue(multiValue);
        filter.setSystemFilter(systemFilter);
        return repository.save(filter);
    }

    private static FilterOption upsertOption(
        FilterOptionRepository repository,
        ProductFilter filter,
        String code,
        String value
    ) {
        FilterOption option = repository.findByFilterAndCode(filter, code).orElseGet(FilterOption::new);
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
        if (repository.existsByProductAndFilterOption(product, option)) {
            return;
        }
        ProductFilterOption relation = new ProductFilterOption();
        relation.setProduct(product);
        relation.setFilterOption(option);
        repository.save(relation);
    }

    private static void seedCategoryProducts(
        ProductRepository productRepository,
        ProductCharacteristicRepository characteristicRepository,
        ProductImageRepository productImageRepository,
        ProductFilterOptionRepository productFilterOptionRepository,
        Category topCategory,
        List<Category> subcategories,
        String[] modelNames,
        String slugPrefix,
        String shortTitlePrefix,
        String commonDescription,
        BigDecimal basePrice,
        BigDecimal stepPrice,
        String[] imageUrls,
        String characteristic1Name,
        String characteristic2Name,
        String characteristic3Name,
        String[] characteristic1Values,
        String[] characteristic2Values,
        String[] characteristic3Values,
        FilterOption brandOption,
        FilterOption primaryColor,
        FilterOption secondaryColor
    ) {
        for (int i = 0; i < 10; i++) {
            BigDecimal price = basePrice
                .add(stepPrice.multiply(BigDecimal.valueOf(i)))
                .setScale(2, RoundingMode.HALF_UP);
            BigDecimal oldPrice = i % 3 == 0
                ? price.multiply(new BigDecimal("1.18")).setScale(2, RoundingMode.HALF_UP)
                : null;

            String model = modelNames[i];
            String slug = slugPrefix + "-" + (i + 1);
            String imageUrl = imageUrls[i % imageUrls.length];
            String c1 = characteristic1Values[i];
            String c2 = characteristic2Values[i];
            String c3 = characteristic3Values[i];

            Product product = upsertProduct(
                productRepository,
                model,
                slug,
                buildRichShortDescription(
                    shortTitlePrefix,
                    model,
                    characteristic1Name,
                    c1,
                    characteristic2Name,
                    c2
                ),
                buildDetailedProductDescription(
                    shortTitlePrefix,
                    model,
                    commonDescription,
                    characteristic1Name,
                    c1,
                    characteristic2Name,
                    c2,
                    characteristic3Name,
                    c3
                ),
                imageUrl,
                price,
                oldPrice,
                i < 3,
                i % 2 == 0,
                oldPrice != null
            );

            HashSet<Category> categories = new HashSet<>();
            categories.add(topCategory);
            if (subcategories != null && !subcategories.isEmpty()) {
                categories.add(subcategories.get(i % subcategories.size()));
            }
            product.setCategories(categories);
            productRepository.save(product);
            replaceProductImages(productImageRepository, product, List.of(imageUrls));

            replaceCharacteristics(
                characteristicRepository,
                product,
                characteristic1Name,
                c1,
                characteristic2Name,
                c2,
                characteristic3Name,
                c3
            );

            linkOption(productFilterOptionRepository, product, brandOption);
            linkOption(productFilterOptionRepository, product, i % 2 == 0 ? primaryColor : secondaryColor);
        }
    }

    private static String buildRichShortDescription(
        String shortTitlePrefix,
        String model,
        String characteristic1Name,
        String characteristic1Value,
        String characteristic2Name,
        String characteristic2Value
    ) {
        return ("%s %s для повседневных и профессиональных задач: %s %s, %s %s, стабильная работа, быстрый отклик и продуманная эргономика.")
            .formatted(
                shortTitlePrefix,
                model,
                characteristic1Name,
                characteristic1Value,
                characteristic2Name,
                characteristic2Value
            );
    }

    private static String buildDetailedProductDescription(
        String shortTitlePrefix,
        String model,
        String commonDescription,
        String characteristic1Name,
        String characteristic1Value,
        String characteristic2Name,
        String characteristic2Value,
        String characteristic3Name,
        String characteristic3Value
    ) {
        return """
            <p><strong>%s %s</strong> — %s. Модель ориентирована на пользователей, которым важны производительность, надежность и комфорт при ежедневном использовании.</p>
            <p>Устройство хорошо сбалансировано для разных сценариев: работа, учеба, мультимедиа, общение и длительные сессии без заметных просадок по скорости. Конструкция корпуса и внутренние компоненты подобраны так, чтобы устройство сохраняло стабильность даже при повышенной нагрузке и оставалось удобным в реальной эксплуатации.</p>
            <p>В этой конфигурации вы получаете:</p>
            <ul>
              <li><strong>%s:</strong> %s</li>
              <li><strong>%s:</strong> %s</li>
              <li><strong>%s:</strong> %s</li>
            </ul>
            <p>Дополнительно производитель сделал акцент на оптимизации энергопотребления, быстрой реакции интерфейса и долговечности ключевых узлов. Это особенно важно для пользователей, которые планируют использовать устройство в интенсивном режиме и ожидают прогнозируемого результата в течение всего срока службы.</p>
            <p>Устройство поддерживает актуальные стандарты подключения и совместимость с популярными сервисами, поэтому его легко встроить в уже существующую экосистему техники. За счёт продуманной конфигурации модель остается универсальным выбором как для домашнего, так и для рабочего использования.</p>
            <p>Официальная гарантия составляет 12 месяцев. На всех этапах доступна поддержка магазина: помощь с подбором, консультации по настройке и сопровождение по вопросам сервиса.</p>
            """
            .formatted(
                shortTitlePrefix,
                model,
                commonDescription,
                characteristic1Name,
                characteristic1Value,
                characteristic2Name,
                characteristic2Value,
                characteristic3Name,
                characteristic3Value
            )
            .trim();
    }

    private static void normalizeAccessoryWordInProductDescriptions(ProductRepository productRepository) {
        List<Product> products = productRepository.findAll();
        for (Product product : products) {
            String originalName = product.getName();
            String originalShort = product.getShortDescription();
            String originalDescription = product.getDescription();
            String normalizedName = sanitizeProductName(originalName);
            String normalizedShort = replaceAccessoryWord(originalShort);
            String normalizedDescription = replaceAccessoryWord(originalDescription);
            if (
                !equalsNullable(originalName, normalizedName) ||
                !equalsNullable(originalShort, normalizedShort) ||
                !equalsNullable(originalDescription, normalizedDescription)
            ) {
                product.setName(normalizedName);
                product.setShortDescription(normalizedShort);
                product.setDescription(normalizedDescription);
                productRepository.save(product);
            }
        }
    }

    private static void normalizeCategoryImages(CategoryRepository categoryRepository) {
        List<Category> categories = categoryRepository.findAll();
        for (Category category : categories) {
            if (category.getSlug() == null || category.getSlug().isBlank()) {
                continue;
            }
            String normalizedImage = categoryImage(category.getSlug());
            if (normalizedImage == null || normalizedImage.isBlank()) {
                continue;
            }
            if (!equalsNullable(category.getImageUrl(), normalizedImage)) {
                category.setImageUrl(normalizedImage);
                categoryRepository.save(category);
            }
        }
    }

    private static String categoryImage(String slug) {
        if (slug == null || slug.isBlank()) {
            return null;
        }
        return switch (slug) {
            case "smartphones", "smartphones-android", "smartphones-iphone" ->
                "/img/categories/smartphones.jpg";
            case "laptops", "laptops-ultrabooks", "laptops-gaming" ->
                "/img/categories/laptops.jpg";
            case "tablets", "tablets-android", "tablets-ipad" ->
                "/img/categories/tablets.png";
            case "audio", "audio-headphones", "audio-speakers" ->
                "/img/categories/audio.jpg";
            case "gaming", "gaming-consoles", "gaming-peripherals" ->
                "/img/categories/gaming.jpg";
            case "wearables", "wearables-smartwatch", "wearables-fitness" ->
                "/img/categories/wearables.jpg";
            case "tv", "tv-oled", "tv-qled" ->
                "/img/categories/tv.png";
            case "accessories", "accessories-chargers", "accessories-cables" ->
                "/img/categories/accessories.jpg";
            default -> null;
        };
    }

    private static String sanitizeProductName(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String normalized = value;
        normalized = normalized.replaceFirst("(?iu)^\\s*акссессуар\\s+", "");
        normalized = normalized.replaceFirst("(?iu)^\\s*аксессуар\\s+", "");
        return normalized.trim();
    }

    private static String replaceAccessoryWord(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String normalized = value;
        normalized = normalized.replaceAll("(?u)Акссессуары", "Устройства");
        normalized = normalized.replaceAll("(?u)Акссессуаров", "Устройств");
        normalized = normalized.replaceAll("(?u)Акссессуарами", "Устройствами");
        normalized = normalized.replaceAll("(?u)Акссессуара", "Устройства");
        normalized = normalized.replaceAll("(?u)Акссессуару", "Устройству");
        normalized = normalized.replaceAll("(?u)Акссессуаром", "Устройством");
        normalized = normalized.replaceAll("(?u)Акссессуаре", "Устройстве");
        normalized = normalized.replaceAll("(?u)Акссессуар", "Устройство");
        normalized = normalized.replaceAll("(?u)Аксессуары", "Устройства");
        normalized = normalized.replaceAll("(?u)Аксессуаров", "Устройств");
        normalized = normalized.replaceAll("(?u)Аксессуарами", "Устройствами");
        normalized = normalized.replaceAll("(?u)Аксессуара", "Устройства");
        normalized = normalized.replaceAll("(?u)Аксессуару", "Устройству");
        normalized = normalized.replaceAll("(?u)Аксессуаром", "Устройством");
        normalized = normalized.replaceAll("(?u)Аксессуаре", "Устройстве");
        normalized = normalized.replaceAll("(?u)Аксессуар", "Устройство");
        normalized = normalized.replaceAll("(?iu)акссессуары", "устройства");
        normalized = normalized.replaceAll("(?iu)акссессуаров", "устройств");
        normalized = normalized.replaceAll("(?iu)акссессуарами", "устройствами");
        normalized = normalized.replaceAll("(?iu)акссессуара", "устройства");
        normalized = normalized.replaceAll("(?iu)акссессуару", "устройству");
        normalized = normalized.replaceAll("(?iu)акссессуаром", "устройством");
        normalized = normalized.replaceAll("(?iu)акссессуаре", "устройстве");
        normalized = normalized.replaceAll("(?iu)акссессуар", "устройство");
        normalized = normalized.replaceAll("(?iu)аксессуары", "устройства");
        normalized = normalized.replaceAll("(?iu)аксессуаров", "устройств");
        normalized = normalized.replaceAll("(?iu)аксессуарами", "устройствами");
        normalized = normalized.replaceAll("(?iu)аксессуара", "устройства");
        normalized = normalized.replaceAll("(?iu)аксессуару", "устройству");
        normalized = normalized.replaceAll("(?iu)аксессуаром", "устройством");
        normalized = normalized.replaceAll("(?iu)аксессуаре", "устройстве");
        normalized = normalized.replaceAll("(?iu)аксессуар", "устройство");
        return normalized;
    }

    private static boolean equalsNullable(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private static void replaceCharacteristics(
        ProductCharacteristicRepository repository,
        Product product,
        String firstName,
        String firstValue,
        String secondName,
        String secondValue,
        String thirdName,
        String thirdValue
    ) {
        repository.deleteByProduct(product);
        addCharacteristic(repository, product, firstName, firstValue, 1);
        addCharacteristic(repository, product, secondName, secondValue, 2);
        addCharacteristic(repository, product, thirdName, thirdValue, 3);
    }

    private static void replaceProductImages(
        ProductImageRepository repository,
        Product product,
        List<String> imageUrls
    ) {
        repository.deleteByProduct(product);
        for (int i = 0; i < imageUrls.size(); i++) {
            String url = imageUrls.get(i);
            if (url == null || url.isBlank()) {
                continue;
            }
            ProductImage image = new ProductImage();
            image.setProduct(product);
            image.setImageUrl(url);
            image.setSortOrder(i);
            repository.save(image);
        }
    }

    private static void reassignLegacyProductCategories(
        ProductRepository productRepository,
        String slug,
        List<Category> categories
    ) {
        productRepository.findBySlug(slug).ifPresent(product -> {
            product.setCategories(new HashSet<>(categories));
            productRepository.save(product);
        });
    }

    private static void normalizeLegacyProductImage(
        ProductRepository productRepository,
        ProductImageRepository productImageRepository,
        String slug,
        String imageUrl
    ) {
        productRepository.findBySlug(slug).ifPresent(product -> {
            product.setImageUrl(imageUrl);
            productRepository.save(product);
            replaceProductImages(productImageRepository, product, List.of(imageUrl));
        });
    }

    private static ShopUser upsertUser(
        ShopUserRepository userRepository,
        PasswordEncoder passwordEncoder,
        String username,
        String email,
        String fullName,
        String phone,
        boolean isAdmin
    ) {
        ShopUser user = userRepository.findByUsernameIgnoreCase(username)
            .or(() -> userRepository.findByEmailIgnoreCase(email))
            .orElseGet(ShopUser::new);
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setIsAdmin(isAdmin);
        user.setIsEnabled(true);
        int index = Math.floorMod(username == null ? 0 : username.hashCode(), 997) + 1;
        ensureUserAddressParts(user, index);
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(username));
        }
        return userRepository.save(user);
    }

    private static void populateMissingUserAddressParts(ShopUserRepository userRepository) {
        List<ShopUser> users = userRepository.findAll().stream()
            .sorted(Comparator.comparing(ShopUser::getId, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
        for (int i = 0; i < users.size(); i++) {
            ShopUser user = users.get(i);
            ensureUserAddressParts(user, i + 1);
            userRepository.save(user);
        }
    }

    private static void ensureUserAddressParts(ShopUser user, int index) {
        if (user == null) {
            return;
        }
        String street = firstNonBlank(user.getAddressStreet(), user.getAddress(), "ул. Савушкина");
        String house = firstNonBlank(user.getAddressHouse(), String.valueOf((index % 40) + 1));
        String apartment = firstNonBlank(user.getAddressApartment(), String.valueOf((index % 60) + 1));
        String entrance = firstNonBlank(user.getAddressEntrance(), String.valueOf((index % 6) + 1));
        String floor = firstNonBlank(user.getAddressFloor(), String.valueOf((index % 14) + 1));
        String intercom = firstNonBlank(user.getAddressIntercom(), String.valueOf(100 + index));

        user.setAddressStreet(street);
        user.setAddressHouse(house);
        user.setAddressApartment(apartment);
        user.setAddressEntrance(entrance);
        user.setAddressFloor(floor);
        user.setAddressIntercom(intercom);
        user.setAddress("г. Астрахань, " + street + ", д. " + house + ", кв. " + apartment);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return null;
    }

    private static void linkFiltersToCategories(
        CategoryFilterRepository categoryFilterRepository,
        List<Category> categories,
        ProductFilter... filters
    ) {
        if (categories == null || categories.isEmpty() || filters == null || filters.length == 0) {
            return;
        }
        for (Category category : categories) {
            for (ProductFilter filter : filters) {
                linkCategoryFilter(categoryFilterRepository, category, filter);
            }
        }
    }

    private static void linkCategoryFilter(
        CategoryFilterRepository categoryFilterRepository,
        Category category,
        ProductFilter filter
    ) {
        if (category == null || filter == null) {
            return;
        }
        if (categoryFilterRepository.findByCategoryAndFilter(category, filter).isPresent()) {
            return;
        }
        CategoryFilter categoryFilter = new CategoryFilter();
        categoryFilter.setCategory(category);
        categoryFilter.setFilter(filter);
        categoryFilterRepository.save(categoryFilter);
    }

    private static void syncFilterByCharacteristic(
        ProductRepository productRepository,
        ProductCharacteristicRepository characteristicRepository,
        FilterOptionRepository filterOptionRepository,
        ProductFilterOptionRepository productFilterOptionRepository,
        ProductFilter filter,
        List<Category> categories,
        String characteristicName
    ) {
        if (filter == null || categories == null || categories.isEmpty() || characteristicName == null || characteristicName.isBlank()) {
            return;
        }
        List<Product> products = productRepository.findDistinctByCategoriesIn(categories, Pageable.unpaged()).getContent();
        for (Product product : products) {
            String value = findCharacteristicValue(characteristicRepository, product, characteristicName);
            if (value == null || value.isBlank()) {
                continue;
            }
            FilterOption option = upsertOption(filterOptionRepository, filter, normalizeCode(value), value.trim());
            linkOption(productFilterOptionRepository, product, option);
        }
    }

    private static String findCharacteristicValue(
        ProductCharacteristicRepository characteristicRepository,
        Product product,
        String characteristicName
    ) {
        return characteristicRepository.findByProductOrderBySortOrderAsc(product).stream()
            .filter(ch -> ch.getName() != null && ch.getName().equalsIgnoreCase(characteristicName))
            .map(ProductCharacteristic::getValue)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse(null);
    }

    private static String normalizeCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return "value-" + Math.abs((raw == null ? "" : raw).hashCode());
        }
        Map<Character, String> map = new LinkedHashMap<>();
        map.put('а', "a"); map.put('б', "b"); map.put('в', "v"); map.put('г', "g"); map.put('д', "d");
        map.put('е', "e"); map.put('ё', "e"); map.put('ж', "zh"); map.put('з', "z"); map.put('и', "i");
        map.put('й', "y"); map.put('к', "k"); map.put('л', "l"); map.put('м', "m"); map.put('н', "n");
        map.put('о', "o"); map.put('п', "p"); map.put('р', "r"); map.put('с', "s"); map.put('т', "t");
        map.put('у', "u"); map.put('ф', "f"); map.put('х', "h"); map.put('ц', "c"); map.put('ч', "ch");
        map.put('ш', "sh"); map.put('щ', "sch"); map.put('ъ', ""); map.put('ы', "y"); map.put('ь', "");
        map.put('э', "e"); map.put('ю', "yu"); map.put('я', "ya");

        StringBuilder sb = new StringBuilder();
        String lowered = raw.trim().toLowerCase(Locale.ROOT);
        for (char ch : lowered.toCharArray()) {
            sb.append(map.getOrDefault(ch, String.valueOf(ch)));
        }
        String normalized = sb.toString()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
        if (normalized.isBlank()) {
            return "value-" + Math.abs(raw.hashCode());
        }
        return normalized;
    }

    private static void linkFlagOption(
        ProductRepository productRepository,
        ProductFilterOptionRepository productFilterOptionRepository,
        FilterOption option,
        String flag
    ) {
        if (option == null || flag == null || flag.isBlank()) {
            return;
        }
        for (Product product : productRepository.findAll()) {
            boolean enabled = switch (flag.toUpperCase(Locale.ROOT)) {
                case "NEW" -> Boolean.TRUE.equals(product.getIsNew());
                case "HIT" -> Boolean.TRUE.equals(product.getIsHit());
                case "DISCOUNT" -> Boolean.TRUE.equals(product.getIsDiscount());
                default -> false;
            };
            if (enabled) {
                linkOption(productFilterOptionRepository, product, option);
            }
        }
    }

    private static void seedDemoOrders(
        ShopOrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        List<Product> products,
        List<ShopUser> users
    ) {
        int targetOrders = 36;
        long existingOrders = orderRepository.count();
        if (existingOrders >= targetOrders || products == null || products.size() < 2 || users == null || users.isEmpty()) {
            return;
        }
        List<Product> sortedProducts = new ArrayList<>(products);
        sortedProducts.sort(Comparator.comparing(Product::getId));
        OrderStatus[] statuses = OrderStatus.values();

        for (int i = (int) existingOrders; i < targetOrders; i++) {
            ShopUser user = users.get(i % users.size());
            Product first = sortedProducts.get(i % sortedProducts.size());
            Product second = sortedProducts.get((i * 3 + 5) % sortedProducts.size());
            if (first.getId().equals(second.getId())) {
                second = sortedProducts.get((i * 5 + 7) % sortedProducts.size());
            }

            ShopOrder order = new ShopOrder();
            order.setUser(user);
            order.setCustomerName(user.getFullName());
            order.setCustomerEmail(user.getEmail());
            order.setCustomerPhone(user.getPhone());
            boolean delivery = i % 3 == 0;
            order.setDeliveryType(delivery ? "DELIVERY" : "PICKUP");
            order.setDeliveryStreet(delivery ? "ул. Софьи Перовской" : null);
            order.setDeliveryHouse(delivery ? String.valueOf(i + 10) : null);
            order.setDeliveryApartment(delivery ? String.valueOf((i % 45) + 1) : null);
            order.setDeliveryEntrance(delivery ? String.valueOf((i % 6) + 1) : null);
            order.setDeliveryFloor(delivery ? String.valueOf((i % 12) + 1) : null);
            order.setDeliveryIntercom(delivery ? String.valueOf(200 + i) : null);
            order.setDeliveryAddress(delivery ? "ул. Софьи Перовской, д. " + (i + 10) : null);
            order.setPickupDate(delivery ? null : LocalDate.now().plusDays((i % 4) + 1));
            order.setDeliveryDate(delivery ? LocalDate.now().plusDays((i % 5) + 1) : null);
            order.setStatus(statuses[i % statuses.length]);
            order.setPickupAddress("г. Астрахань, ул. Савушкина, 12");
            Instant createdAt = Instant.now().minusSeconds((long) (targetOrders - i) * 86_400L);
            order.setCreatedAt(createdAt);
            order.setUpdatedAt(createdAt.plusSeconds(7_200L));
            order.setTotalAmount(BigDecimal.ZERO);
            order = orderRepository.save(order);

            BigDecimal total = BigDecimal.ZERO;
            OrderItem firstItem = new OrderItem();
            firstItem.setOrder(order);
            firstItem.setProduct(first);
            firstItem.setProductName(first.getName());
            firstItem.setUnitPrice(first.getPrice());
            firstItem.setQuantity((i % 2) + 1);
            orderItemRepository.save(firstItem);
            total = total.add(first.getPrice().multiply(BigDecimal.valueOf(firstItem.getQuantity())));

            OrderItem secondItem = new OrderItem();
            secondItem.setOrder(order);
            secondItem.setProduct(second);
            secondItem.setProductName(second.getName());
            secondItem.setUnitPrice(second.getPrice());
            secondItem.setQuantity(((i + 1) % 3) + 1);
            orderItemRepository.save(secondItem);
            total = total.add(second.getPrice().multiply(BigDecimal.valueOf(secondItem.getQuantity())));

            order.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));
            orderRepository.save(order);
        }
    }

    private static void synchronizeOrderStatusHistory(
        ShopOrderRepository orderRepository,
        OrderStatusHistoryRepository orderStatusHistoryRepository
    ) {
        List<ShopOrder> orders = orderRepository.findAll();
        for (ShopOrder order : orders) {
            if (orderStatusHistoryRepository.existsByOrder(order)) {
                continue;
            }
            OrderStatusHistory history = new OrderStatusHistory();
            history.setOrder(order);
            history.setStatus(order.getStatus());
            history.setComment("Статус синхронизирован");
            history.setChangedBy("SYSTEM");
            if (order.getCreatedAt() != null) {
                history.setChangedAt(order.getCreatedAt());
            }
            orderStatusHistoryRepository.save(history);
        }
    }

    private static void seedDemoReviews(
        ReviewRepository reviewRepository,
        List<Product> products,
        List<ShopUser> users,
        ShopUser admin
    ) {
        if (products == null || products.isEmpty()) {
            return;
        }
        List<ShopUser> safeUsers = users == null ? List.of() : users;
        List<Product> sortedProducts = new ArrayList<>(products);
        sortedProducts.sort(Comparator.comparing(Product::getId));
        Instant now = Instant.now();

        for (int productIndex = 0; productIndex < sortedProducts.size(); productIndex++) {
            Product product = sortedProducts.get(productIndex);
            if (isCopiedProduct(product)) {
                continue;
            }
            long productId = product.getId() == null ? productIndex + 1L : product.getId();
            String categoryHint = resolvePrimaryCategoryName(product);
            int targetPerProduct = 10 + Math.floorMod((int) productId, 11); // 10..20

            List<Review> topLevelReviews = new ArrayList<>(reviewRepository.findByProductAndParentIsNullOrderByCreatedAtDesc(product));
            topLevelReviews.sort(Comparator.comparing(Review::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));

            for (int existingIndex = 0; existingIndex < topLevelReviews.size(); existingIndex++) {
                Review review = topLevelReviews.get(existingIndex);
                int rating = normalizeRating(review.getRating(), ratingForPosition(productId, existingIndex));
                review.setRating(rating);
                review.setText(nonBlankOrFallback(
                    review.getText(),
                    buildReviewText(product.getName(), categoryHint, rating, existingIndex)
                ));
                review.setPros(nonBlankOrFallback(
                    review.getPros(),
                    buildProsText(categoryHint, existingIndex, rating)
                ));
                review.setCons(nonBlankOrFallback(
                    review.getCons(),
                    buildConsText(categoryHint, existingIndex, rating)
                ));
                review.setUsagePeriod(normalizeUsagePeriod(review.getUsagePeriod(), existingIndex));

                boolean guest = review.getUser() == null;
                if (guest) {
                    review.setGuestName(nonBlankOrFallback(
                        review.getGuestName(),
                        "Покупатель " + productId + "-" + (existingIndex + 1)
                    ));
                    review.setGuestEmail(nonBlankOrFallback(
                        review.getGuestEmail(),
                        "buyer-" + productId + "-" + (existingIndex + 1) + "@mail.local"
                    ));
                    review.setPurchased(false);
                } else if (review.getPurchased() == null) {
                    review.setPurchased(existingIndex % 4 != 0);
                }

                review.setModerated(true);
                review.setApproved(true);
                if (review.getCreatedAt() == null) {
                    review.setCreatedAt(now.minusSeconds(reviewAgeSeconds(productIndex, existingIndex)));
                }
                reviewRepository.save(review);
            }

            int reviewsToCreate = Math.max(0, targetPerProduct - topLevelReviews.size());
            for (int createIdx = 0; createIdx < reviewsToCreate; createIdx++) {
                int ordinal = topLevelReviews.size() + createIdx;
                int rating = ratingForPosition(productId, ordinal);

                Review review = new Review();
                review.setProduct(product);
                review.setParent(null);
                review.setRating(rating);
                review.setText(buildReviewText(product.getName(), categoryHint, rating, ordinal));
                review.setPros(buildProsText(categoryHint, ordinal, rating));
                review.setCons(buildConsText(categoryHint, ordinal, rating));
                review.setUsagePeriod(usagePeriodForIndex(ordinal));
                review.setModerated(true);
                review.setApproved(true);
                review.setCreatedAt(now.minusSeconds(reviewAgeSeconds(productIndex, ordinal)));

                boolean guest = safeUsers.isEmpty() || ordinal % 5 == 0;
                if (guest) {
                    review.setUser(null);
                    review.setGuestName("Покупатель " + productId + "-" + (ordinal + 1));
                    review.setGuestEmail("buyer-" + productId + "-" + (ordinal + 1) + "@mail.local");
                    review.setPurchased(false);
                } else {
                    ShopUser reviewer = safeUsers.get(Math.floorMod(ordinal + (int) productId, safeUsers.size()));
                    review.setUser(reviewer);
                    review.setGuestName(null);
                    review.setGuestEmail(null);
                    review.setPurchased(ordinal % 4 != 0);
                }

                Review saved = reviewRepository.save(review);
                topLevelReviews.add(saved);
            }

            if (admin != null && !topLevelReviews.isEmpty()) {
                int replyTarget = Math.min(3, Math.max(1, targetPerProduct / 8));
                for (int replyIdx = 0; replyIdx < replyTarget; replyIdx++) {
                    int parentIndex = Math.floorMod(replyIdx * 3 + (int) productId, topLevelReviews.size());
                    Review parent = topLevelReviews.get(parentIndex);
                    if (!reviewRepository.findByParent(parent).isEmpty()) {
                        continue;
                    }
                    Review reply = new Review();
                    reply.setProduct(product);
                    reply.setUser(admin);
                    reply.setParent(parent);
                    reply.setRating(null);
                    reply.setText(buildReplyText(product.getName(), replyIdx));
                    reply.setGuestName(null);
                    reply.setGuestEmail(null);
                    reply.setPros(null);
                    reply.setCons(null);
                    reply.setUsagePeriod(null);
                    reply.setPurchased(false);
                    reply.setModerated(true);
                    reply.setApproved(true);
                    Instant parentCreatedAt = parent.getCreatedAt() == null
                        ? now.minusSeconds(3_600L)
                        : parent.getCreatedAt();
                    reply.setCreatedAt(parentCreatedAt.plusSeconds(1_800L + (replyIdx * 900L)));
                    reviewRepository.save(reply);
                }
            }
        }
    }

    private static boolean isCopiedProduct(Product product) {
        if (product == null) {
            return false;
        }
        String slug = product.getSlug() == null ? "" : product.getSlug().toLowerCase(Locale.ROOT);
        if (slug.contains("-copy")) {
            return true;
        }
        String name = product.getName() == null ? "" : product.getName().toLowerCase(Locale.ROOT);
        return name.contains("(копия");
    }

    private static void seedIvanovShowcaseScenario(
        ShopOrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        OrderStatusHistoryRepository orderStatusHistoryRepository,
        ReviewRepository reviewRepository,
        ShopUserRepository userRepository,
        List<Product> products,
        ShopUser ivanov,
        List<ShopUser> responseUsers
    ) {
        if (ivanov == null || ivanov.getId() == null || products == null || products.isEmpty()) {
            return;
        }
        List<Product> sortedProducts = new ArrayList<>(products);
        sortedProducts.sort(Comparator.comparing(Product::getId));
        if (sortedProducts.size() < 4) {
            return;
        }

        List<OrderStatus> showcaseStatuses = List.of(
            OrderStatus.NEW,
            OrderStatus.ACCEPTED,
            OrderStatus.ASSEMBLED,
            OrderStatus.WAITING_PICKUP,
            OrderStatus.DELIVERED,
            OrderStatus.REJECTED
        );

        List<ShopOrder> ivanovOrders = new ArrayList<>(orderRepository.findByUserOrderByCreatedAtDesc(ivanov));
        Map<OrderStatus, ShopOrder> orderByStatus = new LinkedHashMap<>();
        for (ShopOrder order : ivanovOrders) {
            if (order.getStatus() != null) {
                orderByStatus.putIfAbsent(order.getStatus(), order);
            }
        }

        for (int statusIndex = 0; statusIndex < showcaseStatuses.size(); statusIndex++) {
            OrderStatus status = showcaseStatuses.get(statusIndex);
            if (orderByStatus.containsKey(status)) {
                continue;
            }
            ShopOrder created = createIvanovShowcaseOrder(
                orderRepository,
                orderItemRepository,
                orderStatusHistoryRepository,
                ivanov,
                sortedProducts,
                status,
                statusIndex
            );
            ivanovOrders.add(created);
            orderByStatus.put(status, created);
        }

        Map<Long, Product> orderedProducts = new LinkedHashMap<>();
        ivanovOrders.stream()
            .sorted(Comparator.comparing(ShopOrder::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .forEach(order -> {
                for (OrderItem item : orderItemRepository.findByOrder(order)) {
                    Product product = item.getProduct();
                    if (product != null && product.getId() != null) {
                        orderedProducts.putIfAbsent(product.getId(), product);
                    }
                }
            });

        if (orderedProducts.size() < 3) {
            for (Product product : sortedProducts) {
                if (product.getId() == null) {
                    continue;
                }
                orderedProducts.putIfAbsent(product.getId(), product);
                if (orderedProducts.size() >= 3) {
                    break;
                }
            }
        }

        List<Product> reviewProducts = new ArrayList<>(orderedProducts.values());
        int reviewTarget = Math.min(4, reviewProducts.size());
        if (reviewTarget <= 0) {
            return;
        }

        List<Review> ivanovTopReviews = new ArrayList<>(reviewRepository.findByUserAndParentIsNullOrderByCreatedAtDesc(ivanov));
        Map<Long, Review> ivanovReviewByProductId = new LinkedHashMap<>();
        for (Review review : ivanovTopReviews) {
            if (review.getProduct() != null && review.getProduct().getId() != null) {
                ivanovReviewByProductId.putIfAbsent(review.getProduct().getId(), review);
            }
        }

        Instant now = Instant.now();
        int[] ratingPattern = {5, 4, 5, 3};
        for (int index = 0; index < reviewTarget; index++) {
            Product product = reviewProducts.get(index);
            if (product.getId() == null || ivanovReviewByProductId.containsKey(product.getId())) {
                continue;
            }
            int rating = ratingPattern[index % ratingPattern.length];
            Review review = new Review();
            review.setProduct(product);
            review.setUser(ivanov);
            review.setParent(null);
            review.setRating(rating);
            review.setText(buildIvanovReviewText(product.getName(), rating, index));
            review.setPros(buildIvanovReviewPros(index));
            review.setCons(buildIvanovReviewCons(rating, index));
            review.setUsagePeriod(index % 3 == 0 ? "LT_MONTH" : (index % 3 == 1 ? "UP_TO_YEAR" : "GT_YEAR"));
            review.setGuestName(null);
            review.setGuestEmail(null);
            review.setPurchased(true);
            review.setModerated(true);
            review.setApproved(true);
            review.setCreatedAt(now.minusSeconds((long) (reviewTarget - index) * 172_800L));
            Review saved = reviewRepository.save(review);
            ivanovReviewByProductId.put(product.getId(), saved);
            ivanovTopReviews.add(saved);
        }

        ivanovTopReviews = new ArrayList<>(reviewRepository.findByUserAndParentIsNullOrderByCreatedAtDesc(ivanov));

        List<Review> allReviews = reviewRepository.findAllByOrderByCreatedAtDesc();

        List<Review> topLevelReviews = allReviews.stream()
            .filter(review -> review.getParent() == null)
            .filter(review -> Boolean.TRUE.equals(review.getApproved()))
            .filter(review -> review.getUser() != null && !ivanov.getId().equals(review.getUser().getId()))
            .toList();

        long existingIvanReplies = allReviews.stream()
            .filter(review -> review.getParent() != null)
            .filter(review -> review.getUser() != null && ivanov.getId().equals(review.getUser().getId()))
            .count();
        int ivanReplyCreated = 0;
        int ivanReplyTarget = Math.max(0, 3 - (int) existingIvanReplies);
        for (Review parent : topLevelReviews) {
            if (ivanReplyCreated >= ivanReplyTarget) {
                break;
            }
            if (parent.getProduct() == null) {
                continue;
            }
            boolean hasIvanReply = reviewRepository.findByParent(parent).stream()
                .anyMatch(reply -> reply.getUser() != null && ivanov.getId().equals(reply.getUser().getId()));
            if (hasIvanReply) {
                continue;
            }
            Review reply = new Review();
            reply.setProduct(parent.getProduct());
            reply.setUser(ivanov);
            reply.setParent(parent);
            reply.setRating(null);
            reply.setText(buildIvanovReplyText(parent.getProduct().getName(), ivanReplyCreated));
            reply.setPros(null);
            reply.setCons(null);
            reply.setUsagePeriod(null);
            reply.setGuestName(null);
            reply.setGuestEmail(null);
            reply.setPurchased(true);
            reply.setModerated(true);
            reply.setApproved(true);
            reply.setCreatedAt(now.minusSeconds(64_800L - (long) ivanReplyCreated * 1_200L));
            reviewRepository.save(reply);
            ivanReplyCreated++;
        }

        List<ShopUser> responders = responseUsers == null
            ? List.of()
            : responseUsers.stream()
                .filter(user -> user != null && user.getId() != null && !ivanov.getId().equals(user.getId()))
                .toList();

        if (!responders.isEmpty() && !ivanovTopReviews.isEmpty()) {
            int inboundTarget = Math.min(3, ivanovTopReviews.size());
            for (int idx = 0; idx < inboundTarget; idx++) {
                Review parent = ivanovTopReviews.get(idx);
                if (parent.getProduct() == null) {
                    continue;
                }
                boolean hasOtherReply = reviewRepository.findByParent(parent).stream()
                    .anyMatch(reply -> reply.getUser() != null && !ivanov.getId().equals(reply.getUser().getId()));
                if (hasOtherReply) {
                    continue;
                }
                ShopUser responder = responders.get(idx % responders.size());
                Review reply = new Review();
                reply.setProduct(parent.getProduct());
                reply.setUser(responder);
                reply.setParent(parent);
                reply.setRating(null);
                reply.setText(buildReplyToIvanovText(parent.getProduct().getName(), responder.getFullName(), idx));
                reply.setPros(null);
                reply.setCons(null);
                reply.setUsagePeriod(null);
                reply.setGuestName(null);
                reply.setGuestEmail(null);
                reply.setPurchased(false);
                reply.setModerated(true);
                reply.setApproved(true);
                reply.setCreatedAt(now.minusSeconds((long) (inboundTarget - idx) * 900L));
                reviewRepository.save(reply);
            }
        }

        ivanov.setReviewRepliesSeenAt(now.minusSeconds(30L * 86_400L));
        userRepository.save(ivanov);
    }

    private static ShopOrder createIvanovShowcaseOrder(
        ShopOrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        OrderStatusHistoryRepository orderStatusHistoryRepository,
        ShopUser user,
        List<Product> products,
        OrderStatus status,
        int statusIndex
    ) {
        Instant createdAt = Instant.now().minusSeconds((long) (12 - statusIndex) * 86_400L);
        boolean delivery = status == OrderStatus.NEW
            || status == OrderStatus.ACCEPTED
            || status == OrderStatus.ASSEMBLED
            || status == OrderStatus.DELIVERED
            || status == OrderStatus.REJECTED;

        ShopOrder order = new ShopOrder();
        order.setUser(user);
        order.setCustomerName(user.getFullName());
        order.setCustomerEmail(user.getEmail());
        order.setCustomerPhone(user.getPhone());
        order.setDeliveryType(delivery ? "DELIVERY" : "PICKUP");
        order.setDeliveryStreet(delivery ? firstNonBlank(user.getAddressStreet(), "ул. Савушкина") : null);
        order.setDeliveryHouse(delivery ? firstNonBlank(user.getAddressHouse(), String.valueOf(20 + statusIndex)) : null);
        order.setDeliveryApartment(delivery ? firstNonBlank(user.getAddressApartment(), String.valueOf(10 + statusIndex)) : null);
        order.setDeliveryEntrance(delivery ? firstNonBlank(user.getAddressEntrance(), "1") : null);
        order.setDeliveryFloor(delivery ? firstNonBlank(user.getAddressFloor(), "3") : null);
        order.setDeliveryIntercom(delivery ? firstNonBlank(user.getAddressIntercom(), String.valueOf(200 + statusIndex)) : null);
        order.setDeliveryAddress(delivery ? user.getAddress() : null);
        order.setDeliveryDate(delivery ? LocalDate.now().plusDays(statusIndex + 1L) : null);
        order.setDeliveryTime(delivery ? LocalTime.of(11 + Math.floorMod(statusIndex, 7), Math.floorMod(statusIndex, 2) * 30) : null);
        order.setPickupDate(delivery ? null : LocalDate.now().plusDays(statusIndex + 2L));
        order.setPickupAddress("г. Астрахань, ул. Савушкина, 12");
        order.setStatus(status);
        order.setCreatedAt(createdAt);
        order.setUpdatedAt(createdAt.plusSeconds(10_800L));
        order.setTotalAmount(BigDecimal.ZERO);
        order = orderRepository.save(order);

        int base = statusIndex * 5;
        Product first = products.get(Math.floorMod(base, products.size()));
        Product second = products.get(Math.floorMod(base + 2, products.size()));
        Product third = products.get(Math.floorMod(base + 4, products.size()));
        if (second.getId() != null && second.getId().equals(first.getId())) {
            second = products.get(Math.floorMod(base + 3, products.size()));
        }
        if (third.getId() != null && (third.getId().equals(first.getId()) || third.getId().equals(second.getId()))) {
            third = products.get(Math.floorMod(base + 1, products.size()));
        }

        List<Product> basket = new ArrayList<>();
        basket.add(first);
        basket.add(second);
        if (statusIndex % 2 == 1) {
            basket.add(third);
        }

        BigDecimal total = BigDecimal.ZERO;
        for (int idx = 0; idx < basket.size(); idx++) {
            Product product = basket.get(idx);
            int quantity = 1 + Math.floorMod(statusIndex + idx, 3);
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProduct(product);
            item.setProductName(product.getName());
            item.setUnitPrice(product.getPrice());
            item.setQuantity(quantity);
            orderItemRepository.save(item);
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        }

        order.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));
        order = orderRepository.save(order);
        replaceOrderStatusHistory(orderStatusHistoryRepository, order, status);
        return order;
    }

    private static void replaceOrderStatusHistory(
        OrderStatusHistoryRepository orderStatusHistoryRepository,
        ShopOrder order,
        OrderStatus finalStatus
    ) {
        List<OrderStatusHistory> existing = orderStatusHistoryRepository.findByOrderOrderByChangedAtAsc(order);
        if (!existing.isEmpty()) {
            orderStatusHistoryRepository.deleteAll(existing);
        }

        List<OrderStatus> chain = switch (finalStatus) {
            case NEW -> List.of(OrderStatus.NEW);
            case ACCEPTED -> List.of(OrderStatus.NEW, OrderStatus.ACCEPTED);
            case ASSEMBLED -> List.of(OrderStatus.NEW, OrderStatus.ACCEPTED, OrderStatus.ASSEMBLED);
            case WAITING_PICKUP -> List.of(OrderStatus.NEW, OrderStatus.ACCEPTED, OrderStatus.ASSEMBLED, OrderStatus.WAITING_PICKUP);
            case DELIVERED -> List.of(OrderStatus.NEW, OrderStatus.ACCEPTED, OrderStatus.ASSEMBLED, OrderStatus.DELIVERED);
            case REJECTED -> List.of(OrderStatus.NEW, OrderStatus.ACCEPTED, OrderStatus.REJECTED);
        };

        Instant base = order.getCreatedAt() != null ? order.getCreatedAt() : Instant.now().minusSeconds(86_400L);
        for (int idx = 0; idx < chain.size(); idx++) {
            OrderStatus status = chain.get(idx);
            OrderStatusHistory history = new OrderStatusHistory();
            history.setOrder(order);
            history.setStatus(status);
            history.setComment(switch (status) {
                case NEW -> "Заказ создан";
                case ACCEPTED -> "Заказ подтверждён менеджером";
                case ASSEMBLED -> "Заказ передан на сборку";
                case WAITING_PICKUP -> "Заказ готов к выдаче";
                case DELIVERED -> "Заказ доставлен";
                case REJECTED -> "Заказ отменён";
            });
            history.setChangedBy("SEED_IVANOV");
            history.setChangedAt(base.plusSeconds((long) (idx + 1) * 7_200L));
            orderStatusHistoryRepository.save(history);
        }
    }

    private static String buildIvanovReviewText(String productName, int rating, int index) {
        String[] starts = {
            "Покупал для ежедневного использования, проверил в реальных задачах.",
            "Выбирал между несколькими моделями, остановился на этом варианте.",
            "Пользуюсь уже некоторое время, делюсь личными наблюдениями.",
            "Брал под рабочие сценарии и домашние задачи, впечатления уже сформировались."
        };
        String ending = switch (rating) {
            case 5 -> "В целом очень доволен, " + productName + " работает стабильно и без сюрпризов.";
            case 4 -> "Хороший вариант за свои деньги, но есть мелкие нюансы.";
            case 3 -> "Впечатления смешанные: пользоваться можно, но без вау-эффекта.";
            default -> "Ожидания были выше, но для базовых задач всё же подходит.";
        };
        return starts[Math.floorMod(index, starts.length)] + " " + ending;
    }

    private static String buildIvanovReviewPros(int index) {
        String[] pros = {
            "Плавная работа, хорошая автономность, удобный интерфейс.",
            "Качественная сборка, достойный экран, приятные материалы.",
            "Быстрый отклик, тихая работа, адекватная цена.",
            "Хороший баланс производительности и комфорта."
        };
        return pros[Math.floorMod(index, pros.length)];
    }

    private static String buildIvanovReviewCons(int rating, int index) {
        if (rating >= 5) {
            return "Существенных минусов не заметил, есть только мелкие придирки.";
        }
        String[] cons = {
            "Хотелось бы чуть лучше автономность в нагрузке.",
            "Под максимальной нагрузкой есть заметный нагрев.",
            "Комплектация базовая, пришлось докупать часть аксессуаров."
        };
        return cons[Math.floorMod(index, cons.length)];
    }

    private static String buildIvanovReplyText(String productName, int index) {
        String[] replies = {
            "Подтверждаю по " + productName + ": у меня похожий опыт, особенно по скорости работы.",
            "Согласен с вашим мнением по " + productName + ", тоже заметил те же моменты.",
            "Спасибо за отзыв по " + productName + ", полезно сравнить с моими наблюдениями."
        };
        return replies[Math.floorMod(index, replies.length)];
    }

    private static String buildReplyToIvanovText(String productName, String responderName, int index) {
        String author = responderName == null || responderName.isBlank() ? "Пользователь" : responderName;
        String[] replies = {
            author + ": спасибо за подробный отзыв по " + productName + ", помог с выбором.",
            author + ": полезный комментарий, совпало с моим опытом использования.",
            author + ": подтверждаю, по " + productName + " всё очень похоже в реальном использовании."
        };
        return replies[Math.floorMod(index, replies.length)];
    }

    private static String resolvePrimaryCategoryName(Product product) {
        if (product.getCategories() == null || product.getCategories().isEmpty()) {
            return "техники";
        }
        return product.getCategories().stream()
            .map(Category::getName)
            .filter(name -> name != null && !name.isBlank())
            .sorted()
            .findFirst()
            .orElse("техники");
    }

    private static int normalizeRating(Integer rating, int fallback) {
        if (rating == null || rating < 1 || rating > 5) {
            return fallback;
        }
        return rating;
    }

    private static int ratingForPosition(long productId, int index) {
        int[] ratingPattern = {5, 4, 5, 3, 4, 5, 2, 4, 5, 1, 4, 3, 5, 4, 2, 5};
        return ratingPattern[Math.floorMod((int) productId + index, ratingPattern.length)];
    }

    private static long reviewAgeSeconds(int productIndex, int reviewIndex) {
        long daysOffset = (long) productIndex * 5L + reviewIndex;
        return 86_400L + (daysOffset * 14_400L);
    }

    private static String usagePeriodForIndex(int index) {
        return switch (Math.floorMod(index, 3)) {
            case 0 -> "LT_MONTH";
            case 1 -> "UP_TO_YEAR";
            default -> "GT_YEAR";
        };
    }

    private static String normalizeUsagePeriod(String current, int index) {
        if ("LT_MONTH".equals(current) || "UP_TO_YEAR".equals(current) || "GT_YEAR".equals(current)) {
            return current;
        }
        return usagePeriodForIndex(index);
    }

    private static String nonBlankOrFallback(String current, String fallback) {
        if (current == null || current.isBlank()) {
            return fallback;
        }
        return current;
    }

    private static String buildReviewText(String productName, String categoryHint, int rating, int index) {
        String[] positiveOpeners = {
            "Пользуюсь каждый день, устройство стабильно работает.",
            "Брал(а) для дома и работы, в целом впечатления хорошие.",
            "После нескольких недель использования мнение уже сформировалось.",
            "Сравнивал(а) с похожими моделями, этот вариант оказался удачным."
        };
        String[] neutralOpeners = {
            "Обычный рабочий вариант без явных сюрпризов.",
            "Модель неплохая, но есть моменты, к которым пришлось привыкнуть.",
            "Функциональность достойная, однако не всё идеально."
        };
        String[] negativeOpeners = {
            "Ожидал(а) большего от этой модели.",
            "Есть несколько заметных минусов в повседневном использовании.",
            "На практике устройство показало себя слабее, чем в описании."
        };

        String opener;
        if (rating >= 4) {
            opener = positiveOpeners[Math.floorMod(index, positiveOpeners.length)];
        } else if (rating == 3) {
            opener = neutralOpeners[Math.floorMod(index, neutralOpeners.length)];
        } else {
            opener = negativeOpeners[Math.floorMod(index, negativeOpeners.length)];
        }

        String ending = switch (rating) {
            case 5 -> "Покупкой доволен(а), " + productName + " точно могу рекомендовать в категории \"" + categoryHint + "\".";
            case 4 -> "В целом хороший вариант, " + productName + " оправдывает цену.";
            case 3 -> "Средний результат: пользоваться можно, но есть компромиссы.";
            case 2 -> "Если важен комфорт в ежедневной эксплуатации, лучше посмотреть альтернативы.";
            default -> "Для себя отметил(а), что в этой категории есть более удачные варианты.";
        };

        return opener + " " + ending;
    }

    private static String buildProsText(String categoryHint, int index, int rating) {
        String[] commonPros = {
            "Качественная сборка, аккуратный дизайн, понятная настройка.",
            "Стабильная работа, приятные материалы, удобное управление.",
            "Хорошая производительность, быстрый отклик, удобство в повседневных задачах.",
            "Тихая работа, корректная работа интерфейса, достойная комплектация."
        };
        String[] moderatePros = {
            "Нормальная эргономика, базовые функции выполняет без проблем.",
            "Достаточно удобный интерфейс и предсказуемая работа.",
            "Для своей категории работает приемлемо, без критичных сбоев."
        };

        if (rating >= 4) {
            return commonPros[Math.floorMod(index, commonPros.length)];
        }
        return moderatePros[Math.floorMod(index + categoryHint.length(), moderatePros.length)];
    }

    private static String buildConsText(String categoryHint, int index, int rating) {
        String[] lightCons = {
            "Маркий корпус, хотелось бы чуть лучше автономность.",
            "Под нагрузкой немного греется, не хватает второго варианта цвета.",
            "Иногда не хватает запаса яркости на улице.",
            "Комплект можно было сделать богаче."
        };
        String[] mediumCons = {
            "Автономность средняя, под длительной нагрузкой заметно падает производительность.",
            "Есть мелкие недочёты в интерфейсе, к которым нужно привыкнуть.",
            "Не самая тихая работа в пиковых сценариях."
        };
        String[] strictCons = {
            "Скорость работы нестабильная, нагрев выше ожидаемого.",
            "По ощущениям качество исполнения не соответствует цене.",
            "Нужна доработка оптимизации, в текущем виде есть заметные ограничения."
        };

        if (rating >= 4) {
            return lightCons[Math.floorMod(index, lightCons.length)];
        }
        if (rating == 3) {
            return mediumCons[Math.floorMod(index + categoryHint.length(), mediumCons.length)];
        }
        return strictCons[Math.floorMod(index + categoryHint.length(), strictCons.length)];
    }

    private static String buildReplyText(String productName, int index) {
        String[] replyMessages = {
            "Спасибо за подробный отзыв по " + productName + ". Это поможет другим покупателям с выбором.",
            "Благодарим за обратную связь, информацию передали команде магазина.",
            "Спасибо за комментарий. Учтём ваш опыт в дальнейшей работе с ассортиментом."
        };
        return replyMessages[Math.floorMod(index, replyMessages.length)];
    }

    private static void recalculateProductRatings(
        ReviewRepository reviewRepository,
        ProductRepository productRepository
    ) {
        for (Product product : productRepository.findAll()) {
            List<Review> approved = reviewRepository.findByProductAndApprovedTrueAndRatingIsNotNull(product);
            int count = approved.size();
            double avg = approved.stream()
                .map(Review::getRating)
                .filter(rating -> rating != null)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
            product.setReviewCount(count);
            product.setRatingAvg(avg);
            productRepository.save(product);
        }
    }

    private static void seedSupportRequests(
        SupportRequestRepository supportRequestRepository,
        List<ShopUser> users
    ) {
        int targetRequests = 32;
        long existing = supportRequestRepository.count();
        if (existing >= targetRequests) {
            return;
        }
        List<String> messages = List.of(
            "Подскажите, есть ли товар в наличии в магазине?",
            "Нужна консультация по выбору ноутбука для работы.",
            "Хочу уточнить сроки самовывоза по заказу.",
            "Можно ли поменять дату получения заказа?",
            "Нужна помощь по возврату товара."
        );
        for (int i = (int) existing; i < targetRequests; i++) {
            SupportRequest request = new SupportRequest();
            boolean guest = users == null || users.isEmpty() || i % 4 == 0;
            if (guest) {
                request.setUser(null);
                request.setName("Посетитель " + (i + 1));
                request.setEmail("visitor" + (i + 1) + "@mail.local");
                request.setPhone("+7999888" + String.format("%04d", i + 1));
            } else {
                ShopUser user = users.get(i % users.size());
                request.setUser(user);
                request.setName(user.getFullName());
                request.setEmail(user.getEmail());
                request.setPhone(user.getPhone());
            }
            request.setSubject("Общий вопрос");
            request.setMessage(messages.get(i % messages.size()));
            request.setProcessed(i % 3 == 0);
            request.setCreatedAt(Instant.now().minusSeconds((long) (targetRequests - i) * 10_800L));
            supportRequestRepository.save(request);
        }
    }

    private static void seedDemoCartAndWishlist(
        CartItemRepository cartItemRepository,
        WishlistItemRepository wishlistItemRepository,
        List<Product> products,
        List<ShopUser> users
    ) {
        if (products == null || products.size() < 8 || users == null || users.isEmpty()) {
            return;
        }
        List<Product> sortedProducts = new ArrayList<>(products);
        sortedProducts.sort(Comparator.comparing(Product::getId));

        for (int i = 0; i < users.size(); i++) {
            ShopUser user = users.get(i);
            Product cartFirst = sortedProducts.get((i * 2) % sortedProducts.size());
            Product cartSecond = sortedProducts.get((i * 2 + 1) % sortedProducts.size());

            upsertUserCartItem(cartItemRepository, user, cartFirst, (i % 2) + 1);
            upsertUserCartItem(cartItemRepository, user, cartSecond, (i % 3) + 1);

            Product wishFirst = sortedProducts.get((i * 3 + 2) % sortedProducts.size());
            Product wishSecond = sortedProducts.get((i * 3 + 3) % sortedProducts.size());
            Product wishThird = sortedProducts.get((i * 3 + 4) % sortedProducts.size());

            upsertUserWishlistItem(wishlistItemRepository, user, wishFirst);
            upsertUserWishlistItem(wishlistItemRepository, user, wishSecond);
            upsertUserWishlistItem(wishlistItemRepository, user, wishThird);
        }

        String guestSession = "DEMO-GUEST-SESSION";

        for (int i = 0; i < 3; i++) {
            Product guestProduct = sortedProducts.get((i + 7) % sortedProducts.size());

            upsertSessionCartItem(cartItemRepository, guestSession, guestProduct, i + 1);
            upsertSessionWishlistItem(wishlistItemRepository, guestSession, guestProduct);
        }
    }

    private static void upsertUserCartItem(
        CartItemRepository cartItemRepository,
        ShopUser user,
        Product product,
        int quantity
    ) {
        CartItem cartItem = cartItemRepository.findByUserAndProduct(user, product)
            .orElseGet(CartItem::new);
        cartItem.setUser(user);
        cartItem.setSessionId(null);
        cartItem.setProduct(product);
        cartItem.setQuantity(Math.max(1, quantity));
        cartItemRepository.save(cartItem);
    }

    private static void upsertSessionCartItem(
        CartItemRepository cartItemRepository,
        String sessionId,
        Product product,
        int quantity
    ) {
        CartItem cartItem = cartItemRepository.findBySessionIdAndProduct(sessionId, product)
            .orElseGet(CartItem::new);
        cartItem.setUser(null);
        cartItem.setSessionId(sessionId);
        cartItem.setProduct(product);
        cartItem.setQuantity(Math.max(1, quantity));
        cartItemRepository.save(cartItem);
    }

    private static void upsertUserWishlistItem(
        WishlistItemRepository wishlistItemRepository,
        ShopUser user,
        Product product
    ) {
        WishlistItem item = wishlistItemRepository.findByUserAndProduct(user, product)
            .orElseGet(WishlistItem::new);
        item.setUser(user);
        item.setSessionId(null);
        item.setProduct(product);
        wishlistItemRepository.save(item);
    }

    private static void upsertSessionWishlistItem(
        WishlistItemRepository wishlistItemRepository,
        String sessionId,
        Product product
    ) {
        WishlistItem item = wishlistItemRepository.findBySessionIdAndProduct(sessionId, product)
            .orElseGet(WishlistItem::new);
        item.setUser(null);
        item.setSessionId(sessionId);
        item.setProduct(product);
        wishlistItemRepository.save(item);
    }
}

