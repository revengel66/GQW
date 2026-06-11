insert into analytics.module_type(code, name, description, is_active) values
('DEFAULT', 'Общий', 'Общий модуль', true),
('SHOP', 'Интернет-магазин', 'События пользовательской части магазина', true),
('ADMIN', 'Админ-панель', 'События административной панели', true)
on conflict (code) do update
set name = excluded.name,
    description = excluded.description,
    is_active = true;

drop table if exists tmp_rename_map;
create temporary table tmp_rename_map (
    old_code varchar(64) primary key,
    new_code varchar(64) not null
);

insert into tmp_rename_map(old_code, new_code) values
('ADMIN_CREDENTIALS_UPDATE','CREDENTIALS_UPDATE'),
('CREDENTIALS_ACTION','CREDENTIALS_UPDATE'),
('ADMINDASHBOARD_VIEW','DASHBOARD_VIEW'),
('CART_ITEM_UPDATE','CART_UPDATE'),
('CATEGORIES_VIEW','CATEGORY_LIST_VIEW'),
('CATEGORIES_CREATE','CATEGORY_CREATE'),
('CATEGORIES_UPDATE','CATEGORY_UPDATE'),
('CATEGORIES_DELETE','CATEGORY_DELETE'),
('FILES_VIEW','FILE_LIST_VIEW'),
('FILES_UPLOAD','FILE_CREATE'),
('FILTERS_VIEW','FILTER_LIST_VIEW'),
('FILTERS_SAVE','FILTER_UPDATE'),
('FILTER_SAVE','FILTER_UPDATE'),
('FILTERS_DELETE','FILTER_DELETE'),
('ORDERS_VIEW','ORDER_LIST_VIEW'),
('ORDERS_UPDATE','ORDER_UPDATE'),
('ORDERS_DELETE','ORDER_DELETE'),
('ORDER_DETAIL_VIEW','ORDER_VIEW'),
('USERS_VIEW','USER_LIST_VIEW'),
('USERS_UPDATE','USER_UPDATE'),
('USER_DETAIL_VIEW','USER_VIEW'),
('PRODUCTS_VIEW','PRODUCT_LIST_VIEW'),
('PRODUCTS_CREATE','PRODUCT_CREATE'),
('PRODUCTS_UPDATE','PRODUCT_UPDATE'),
('PRODUCTS_DELETE','PRODUCT_DELETE'),
('PRODUCTS_SAVE','PRODUCT_CREATE'),
('PRODUCTS_ACTION','PRODUCT_DUPLICATE'),
('PRODUCT_CHARACTERISTICS_SAVE','PRODUCT_CHARACTERISTIC_CREATE'),
('PRODUCT_CHARACTERISTICS_UPDATE','PRODUCT_CHARACTERISTIC_UPDATE'),
('PRODUCT_CHARACTERISTICS_DELETE','PRODUCT_CHARACTERISTIC_DELETE'),
('PRODUCT_FILTER_OPTIONS_SAVE','PRODUCT_FILTER_OPTION_CREATE'),
('PRODUCT_FILTER_OPTIONS_UPDATE','PRODUCT_FILTER_OPTION_UPDATE'),
('PRODUCT_FILTER_OPTIONS_DELETE','PRODUCT_FILTER_OPTION_DELETE'),
('PRODUCT_REVIEW_REPLY','PRODUCT_REVIEW_REPLY_CREATE'),
('REVIEWS_VIEW','REVIEW_LIST_VIEW'),
('REVIEWS_MODERATE','REVIEW_MODERATE'),
('REVIEWS_DELETE','REVIEW_DELETE'),
('REVIEW_UPDATE','REVIEW_MODERATE'),
('SUPPORT_VIEW','SUPPORT_LIST_VIEW'),
('SUPPORT_DETAILS_VIEW','SUPPORT_DETAIL_VIEW'),
('SUPPORT_UPDATE','SUPPORT_STATUS_UPDATE'),
('VIEW_CATALOG','CATALOG_VIEW'),
('VIEW_CATEGORY','CATEGORY_VIEW'),
('VIEW_PRODUCT','PRODUCT_VIEW'),
('REMOVE_FROM_WISHLIST','WISHLIST_REMOVE'),
('WISHLIST_DELETE','WISHLIST_REMOVE'),
('ACCOUNT_CREATE','ACCOUNT_SUPPORT_CREATE')
on conflict (old_code) do update
set new_code = excluded.new_code;

drop table if exists tmp_event_type_target;
create temporary table tmp_event_type_target (
    code varchar(64) primary key,
    name varchar(128) not null,
    description varchar(512),
    module_code varchar(64) not null
);

insert into tmp_event_type_target(code, name, description, module_code) values
('ABOUT_VIEW','Страница о компании','Пользователь открыл страницу «О компании».','SHOP'),
('ACCOUNT_ADDRESS_UPDATE','Обновление адреса в ЛК','Пользователь обновил адрес в личном кабинете.','SHOP'),
('ACCOUNT_DELETE','Удаление аккаунта','Пользователь удалил свой аккаунт.','SHOP'),
('ACCOUNT_ORDER_CANCEL','Отмена заказа в ЛК','Пользователь отменил заказ из личного кабинета.','SHOP'),
('ACCOUNT_ORDER_UPDATE','Обновление заказа в ЛК','Пользователь изменил параметры заказа из личного кабинета.','SHOP'),
('ACCOUNT_PROFILE_UPDATE','Обновление профиля в ЛК','Пользователь обновил профиль (ФИО, email, телефон) в личном кабинете.','SHOP'),
('ACCOUNT_SUPPORT_CREATE','Обращение в поддержку из ЛК','Пользователь создал обращение в поддержку из личного кабинета.','SHOP'),
('ACCOUNT_VIEW','Страница личного кабинета','Пользователь открыл страницу личного кабинета.','SHOP'),
('ADD_TO_CART','Добавление в корзину','Пользователь добавил товар в корзину.','SHOP'),
('ADD_TO_WISHLIST','Добавление в избранное','Пользователь добавил товар в избранное.','SHOP'),
('CART_UPDATE','Изменение количества в корзине','Пользователь изменил количество товара в корзине.','SHOP'),
('CART_VIEW','Страница корзины','Пользователь открыл страницу корзины.','SHOP'),
('CATALOG_VIEW','Страница каталога','Пользователь открыл страницу каталога.','SHOP'),
('CATEGORY_VIEW','Страница категории','Пользователь открыл страницу категории каталога.','SHOP'),
('CHECKOUT_SUBMIT','Оформление заказа','Пользователь подтвердил оформление заказа.','SHOP'),
('CHECKOUT_VIEW','Страница оформления заказа','Пользователь открыл страницу оформления заказа.','SHOP'),
('CONTACTS_VIEW','Страница контактов','Пользователь открыл страницу контактов.','SHOP'),
('DELIVERY_VIEW','Страница доставки и оплаты','Пользователь открыл страницу «Доставка и оплата».','SHOP'),
('HOME_VIEW','Главная страница','Пользователь открыл главную страницу магазина.','SHOP'),
('LOGIN','Вход','Выполнена попытка входа пользователя в систему.','SHOP'),
('LOGIN_VIEW','Страница входа','Пользователь открыл страницу входа.','SHOP'),
('PRODUCT_VIEW','Страница товара','Пользователь открыл страницу товара.','SHOP'),
('REGISTER','Регистрация','Пользователь зарегистрировал новый аккаунт.','SHOP'),
('REGISTER_VIEW','Страница регистрации','Пользователь открыл страницу регистрации.','SHOP'),
('REMOVE_TO_CART','Удаление из корзины','Пользователь удалил товар из корзины.','SHOP'),
('REVIEW_ADD','Добавление отзыва','Пользователь добавил отзыв к товару.','SHOP'),
('REVIEW_REPLY','Ответ на отзыв','Пользователь добавил ответ на отзыв.','SHOP'),
('REVIEWS_PAGE_VIEW','Страница отзывов','Пользователь открыл страницу отзывов.','SHOP'),
('SUPPORT_PAGE_VIEW','Страница поддержки','Пользователь открыл страницу поддержки.','SHOP'),
('SUPPORT_REQUEST','Заявка в поддержку','Пользователь отправил заявку через форму поддержки.','SHOP'),
('WISHLIST_REMOVE','Удаление из избранного','Пользователь удалил товар из избранного.','SHOP'),
('WISHLIST_VIEW','Страница избранного','Пользователь открыл страницу избранного.','SHOP'),
('CREDENTIALS_UPDATE','Обновление учётных данных админа','Администратор изменил логин или пароль админ-панели.','ADMIN'),
('DASHBOARD_VIEW','Страница дашборда','Администратор открыл дашборд админ-панели.','ADMIN'),
('CATEGORY_LIST_VIEW','Список категорий','Администратор открыл страницу списка категорий.','ADMIN'),
('CATEGORY_CREATE_VIEW','Форма создания категории','Администратор открыл форму создания категории.','ADMIN'),
('CATEGORY_EDIT_VIEW','Форма редактирования категории','Администратор открыл форму редактирования категории.','ADMIN'),
('CATEGORY_CREATE','Создание категории','Администратор создал категорию.','ADMIN'),
('CATEGORY_UPDATE','Обновление категории','Администратор обновил категорию.','ADMIN'),
('CATEGORY_DELETE','Удаление категории','Администратор удалил категорию.','ADMIN'),
('FILE_LIST_VIEW','Страница файлового менеджера','Администратор открыл страницу файлового менеджера.','ADMIN'),
('FILE_CREATE','Загрузка файла','Администратор загрузил файл.','ADMIN'),
('FILE_DELETE','Удаление файла','Администратор удалил файл.','ADMIN'),
('FILTER_LIST_VIEW','Список фильтров','Администратор открыл страницу списка фильтров.','ADMIN'),
('FILTER_CREATE_VIEW','Форма создания фильтра','Администратор открыл форму создания фильтра.','ADMIN'),
('FILTER_EDIT_VIEW','Форма редактирования фильтра','Администратор открыл форму редактирования фильтра.','ADMIN'),
('FILTER_CREATE','Создание фильтра','Администратор создал фильтр.','ADMIN'),
('FILTER_UPDATE','Обновление фильтра','Администратор обновил фильтр.','ADMIN'),
('FILTER_DELETE','Удаление фильтра','Администратор удалил фильтр.','ADMIN'),
('ORDER_LIST_VIEW','Список заказов','Администратор открыл страницу списка заказов.','ADMIN'),
('ORDER_VIEW','Карточка заказа','Администратор открыл карточку заказа.','ADMIN'),
('ORDER_UPDATE','Обновление заказа','Администратор обновил заказ.','ADMIN'),
('ORDER_DELETE','Удаление заказа','Администратор удалил заказ.','ADMIN'),
('PRODUCT_LIST_VIEW','Список товаров','Администратор открыл страницу списка товаров.','ADMIN'),
('PRODUCT_CREATE_VIEW','Форма создания товара','Администратор открыл форму создания товара.','ADMIN'),
('PRODUCT_EDIT_VIEW','Форма редактирования товара','Администратор открыл форму редактирования товара.','ADMIN'),
('PRODUCT_CREATE','Создание товара','Администратор создал товар.','ADMIN'),
('PRODUCT_UPDATE','Обновление товара','Администратор обновил товар.','ADMIN'),
('PRODUCT_DELETE','Удаление товара','Администратор удалил товар.','ADMIN'),
('PRODUCT_DUPLICATE','Дублирование товара','Администратор создал копию товара.','ADMIN'),
('PRODUCT_IMAGE_DELETE','Удаление изображения товара','Администратор удалил изображение товара.','ADMIN'),
('PRODUCT_CHARACTERISTIC_CREATE','Создание характеристики товара','Администратор добавил характеристику товара.','ADMIN'),
('PRODUCT_CHARACTERISTIC_UPDATE','Обновление характеристики товара','Администратор изменил характеристику товара.','ADMIN'),
('PRODUCT_CHARACTERISTIC_DELETE','Удаление характеристики товара','Администратор удалил характеристику товара.','ADMIN'),
('PRODUCT_FILTER_OPTION_CREATE','Создание опции фильтра товара','Администратор добавил опцию фильтра товара.','ADMIN'),
('PRODUCT_FILTER_OPTION_UPDATE','Обновление опции фильтра товара','Администратор изменил опцию фильтра товара.','ADMIN'),
('PRODUCT_FILTER_OPTION_DELETE','Удаление опции фильтра товара','Администратор удалил опцию фильтра товара.','ADMIN'),
('PRODUCT_REVIEW_MODERATE','Модерация отзыва к товару','Администратор изменил статус модерации отзыва к товару.','ADMIN'),
('PRODUCT_REVIEW_DELETE','Удаление отзыва к товару','Администратор удалил отзыв к товару.','ADMIN'),
('PRODUCT_REVIEW_REPLY_CREATE','Ответ администратора на отзыв','Администратор добавил ответ на отзыв к товару.','ADMIN'),
('PRODUCT_REVIEW_REPLY_UPDATE','Обновление ответа администратора на отзыв','Администратор изменил ответ на отзыв к товару.','ADMIN'),
('REVIEW_LIST_VIEW','Список отзывов','Администратор открыл страницу списка отзывов.','ADMIN'),
('REVIEW_MODERATE','Модерация отзыва','Администратор изменил статус модерации отзыва.','ADMIN'),
('REVIEW_DELETE','Удаление отзыва','Администратор удалил отзыв.','ADMIN'),
('SUPPORT_LIST_VIEW','Список обращений в поддержку','Администратор открыл страницу заявок в поддержку.','ADMIN'),
('SUPPORT_DETAIL_VIEW','Карточка обращения в поддержку','Администратор открыл карточку обращения в поддержку.','ADMIN'),
('SUPPORT_STATUS_UPDATE','Обновление статуса обращения','Администратор изменил статус обращения в поддержку.','ADMIN'),
('SUPPORT_REPLY_CREATE','Ответ на обращение','Администратор отправил ответ на обращение в поддержку.','ADMIN'),
('SUPPORT_PROCESSED_UPDATE','Отметка обращения обработанным','Администратор отметил обращение как обработанное.','ADMIN'),
('USER_LIST_VIEW','Список пользователей','Администратор открыл страницу списка пользователей.','ADMIN'),
('USER_VIEW','Карточка пользователя','Администратор открыл карточку пользователя.','ADMIN'),
('USER_UPDATE','Обновление пользователя','Администратор обновил данные/статус пользователя.','ADMIN');

insert into analytics.event_type(code, name, description, module_code, is_active)
select code, name, description, module_code, true
from tmp_event_type_target
on conflict (code) do update
set name = excluded.name,
    description = excluded.description,
    module_code = excluded.module_code,
    is_active = true;

update analytics.event e
set event_type_code = rm.new_code
from tmp_rename_map rm
where e.event_type_code = rm.old_code;

update analytics.aggregated_metric am
set event_type_code = rm.new_code
from tmp_rename_map rm
where am.event_type_code = rm.old_code;

update analytics.code_alias ca
set source_code = rm.new_code
from tmp_rename_map rm
where ca.alias_type = 'EVENT'
  and ca.source_code = rm.old_code;

update analytics.code_alias ca
set target_code = rm.new_code
from tmp_rename_map rm
where ca.alias_type = 'EVENT'
  and ca.target_code = rm.old_code;

delete from analytics.code_alias a
using analytics.code_alias b
where a.ctid < b.ctid
  and a.alias_type = b.alias_type
  and a.source_code = b.source_code
  and a.target_code = b.target_code;

delete from analytics.event_type et
using tmp_rename_map rm
where et.code = rm.old_code
  and rm.old_code <> rm.new_code
  and et.code not in (select code from tmp_event_type_target);

update analytics.event e
set module_code = et.module_code
from analytics.event_type et
where e.event_type_code = et.code
  and (e.module_code is null or e.module_code <> et.module_code);
