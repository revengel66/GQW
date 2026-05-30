(function () {
    if (window.__nexoraStoreInitialized) {
        return;
    }
    window.__nexoraStoreInitialized = true;

    const headerCartBadge = document.getElementById("cart-count-badge");
    const expandableTextContexts = [];
    let expandableTextEventsBound = false;
    let expandableTextRaf = 0;

    function initProductSliders() {
        if (typeof window.Swiper !== "function") {
            return;
        }
        document.querySelectorAll(".js-products-slider").forEach((slider) => {
            const wrapper = slider.closest(".products-slider-wrap");
            const prevEl = wrapper?.querySelector(".slider-prev");
            const nextEl = wrapper?.querySelector(".slider-next");
            const config = {
                slidesPerView: 1.15,
                spaceBetween: 0,
                speed: 420,
                breakpoints: {
                    576: {slidesPerView: 2, spaceBetween: 0},
                    992: {slidesPerView: 3, spaceBetween: 0},
                    1200: {slidesPerView: 4, spaceBetween: 0}
                }
            };
            if (prevEl && nextEl) {
                config.navigation = {prevEl, nextEl};
            }
            new window.Swiper(slider, config);
        });
    }

    function initCategorySliders() {
        if (typeof window.Swiper !== "function") {
            return;
        }
        document.querySelectorAll(".js-categories-slider").forEach((slider) => {
            const wrapper = slider.closest(".categories-slider-wrap") || slider.closest(".products-slider-wrap");
            const prevEl = wrapper?.querySelector(".slider-prev");
            const nextEl = wrapper?.querySelector(".slider-next");
            const config = {
                slidesPerView: 1.15,
                spaceBetween: 0,
                speed: 420,
                breakpoints: {
                    576: {slidesPerView: 2, spaceBetween: 0},
                    992: {slidesPerView: 3, spaceBetween: 0},
                    1200: {slidesPerView: 4, spaceBetween: 0}
                }
            };
            if (prevEl && nextEl) {
                config.navigation = {prevEl, nextEl};
            }
            new window.Swiper(slider, config);
        });
    }

    function renderHeaderCartBadge(count) {
        if (!headerCartBadge) {
            return;
        }
        const safeCount = Number.isFinite(count) ? Math.max(0, count) : 0;
        headerCartBadge.textContent = String(safeCount);
        headerCartBadge.classList.toggle("d-none", safeCount <= 0);
    }

    function formatPrice(value) {
        const amount = Number.isFinite(value) ? Math.max(0, value) : 0;
        return `${new Intl.NumberFormat("ru-RU", {maximumFractionDigits: 0}).format(Math.round(amount))} ₽`;
    }

    function refreshCartTotalsFromRows() {
        const totalNode = document.getElementById("cart-total-amount");
        const rows = Array.from(document.querySelectorAll(".js-cart-row"));
        if (!totalNode) {
            return;
        }
        if (rows.length === 0) {
            if (window.location.pathname.startsWith("/cart")) {
                window.location.reload();
            }
            return;
        }

        const total = rows.reduce((acc, row) => {
            const unitPrice = Number.parseFloat(row.dataset.unitPrice || "0");
            const qtyInput = row.querySelector(".js-product-qty-input");
            const quantity = Number.parseInt(qtyInput?.value || "0", 10);
            return acc + (Number.isFinite(unitPrice) ? unitPrice : 0) * (Number.isFinite(quantity) ? quantity : 0);
        }, 0);
        totalNode.textContent = formatPrice(total);
    }

    function updateProductQuantity(productId, quantity) {
        const pid = String(productId);
        const safeQty = Number.isFinite(quantity) ? Math.max(0, quantity) : 0;

        document
            .querySelectorAll(`.js-cart-toggle-form[data-product-id="${pid}"] .js-product-cart-qty, .js-cart-add-form[data-product-id="${pid}"] .js-product-cart-qty`)
            .forEach((node) => {
            node.textContent = String(safeQty);
            node.classList.toggle("d-none", safeQty <= 0);
        });

        document.querySelectorAll(`.js-product-qty-input[data-product-id="${pid}"]`).forEach((input) => {
            input.value = String(safeQty);
        });

        document.querySelectorAll(`.js-cart-row[data-product-id="${pid}"]`).forEach((row) => {
            const unitPrice = Number.parseFloat(row.dataset.unitPrice || "0");
            const lineTotalNode = row.querySelector(".js-cart-line-total");
            if (lineTotalNode instanceof HTMLElement) {
                lineTotalNode.textContent = formatPrice(unitPrice * safeQty);
            }
            if (safeQty <= 0) {
                row.remove();
            }
        });

        refreshCartTotalsFromRows();
    }

    function resolveDisplayedProductQuantity(productId) {
        const pid = String(productId);
        const qtyInput = document.querySelector(`.js-product-qty-input[data-product-id="${pid}"]`);
        if (qtyInput instanceof HTMLInputElement) {
            const val = Number.parseInt(qtyInput.value || "0", 10);
            return Number.isFinite(val) ? Math.max(0, val) : 0;
        }
        const badge = document.querySelector(`.js-cart-toggle-form[data-product-id="${pid}"] .js-product-cart-qty`);
        if (badge instanceof HTMLElement && !badge.classList.contains("d-none")) {
            const val = Number.parseInt(badge.textContent || "0", 10);
            return Number.isFinite(val) ? Math.max(0, val) : 0;
        }
        return 0;
    }

    function updateWishlistState(productId, inWishlist) {
        const pid = String(productId);
        document.querySelectorAll(`.js-wishlist-toggle-form[data-product-id="${pid}"] .js-wishlist-btn`).forEach((button) => {
            button.classList.toggle("is-active", Boolean(inWishlist));
            const icon = button.querySelector("i");
            if (icon instanceof HTMLElement) {
                icon.classList.remove("bi-heart", "bi-heart-fill");
                icon.classList.add(Boolean(inWishlist) ? "bi-heart-fill" : "bi-heart");
            }
        });
    }

    function initCategoryFiltersAutoApply() {
        document.querySelectorAll("form.js-category-filter-form").forEach((form) => {
            if (!(form instanceof HTMLFormElement) || form.dataset.boundAutoApply === "1") {
                return;
            }
            form.dataset.boundAutoApply = "1";

            const submitForm = () => {
                const pageInput = form.querySelector("input[name='page']");
                if (pageInput instanceof HTMLInputElement) {
                    pageInput.value = "0";
                }
                if (typeof form.requestSubmit === "function") {
                    form.requestSubmit();
                    return;
                }
                form.submit();
            };

            form.querySelectorAll("input.js-category-filter-option[type='checkbox'][name='optionIds']").forEach((checkbox) => {
                checkbox.addEventListener("change", submitForm);
            });

            form.querySelectorAll("select.js-category-filter-sort[name='sort']").forEach((sortSelect) => {
                sortSelect.addEventListener("change", submitForm);
            });
        });
    }

    function initCategoryPriceRange() {
        document.querySelectorAll(".js-price-range").forEach((rangeRoot) => {
            const minInput = rangeRoot.querySelector(".js-price-range-input-min");
            const maxInput = rangeRoot.querySelector(".js-price-range-input-max");
            const minHidden = rangeRoot.querySelector(".js-price-range-hidden-min");
            const maxHidden = rangeRoot.querySelector(".js-price-range-hidden-max");
            const minLabel = rangeRoot.querySelector(".js-price-range-value-min");
            const maxLabel = rangeRoot.querySelector(".js-price-range-value-max");
            const progress = rangeRoot.querySelector(".js-price-range-progress");

            if (!(minInput instanceof HTMLInputElement) || !(maxInput instanceof HTMLInputElement)) {
                return;
            }

            const parseNum = (value, fallback) => {
                const normalized = String(value ?? "").replace(",", ".");
                const parsed = Number.parseFloat(normalized);
                return Number.isFinite(parsed) ? parsed : fallback;
            };
            const formatNum = (value) =>
                new Intl.NumberFormat("ru-RU", {maximumFractionDigits: 0}).format(Math.round(value));

            let rangeMin = parseNum(rangeRoot.getAttribute("data-min"), 0);
            let rangeMax = parseNum(rangeRoot.getAttribute("data-max"), rangeMin);
            if (rangeMax < rangeMin) {
                rangeMax = rangeMin;
            }
            const span = Math.max(0, rangeMax - rangeMin);

            const render = (active) => {
                let leftValue = parseNum(minInput.value, rangeMin);
                let rightValue = parseNum(maxInput.value, rangeMax);

                if (leftValue < rangeMin) {
                    leftValue = rangeMin;
                }
                if (rightValue > rangeMax) {
                    rightValue = rangeMax;
                }
                if (leftValue > rightValue) {
                    if (active === "min") {
                        rightValue = leftValue;
                    } else {
                        leftValue = rightValue;
                    }
                }

                minInput.value = String(leftValue);
                maxInput.value = String(rightValue);
                if (minHidden instanceof HTMLInputElement) {
                    minHidden.value = String(leftValue);
                }
                if (maxHidden instanceof HTMLInputElement) {
                    maxHidden.value = String(rightValue);
                }
                if (minLabel instanceof HTMLElement) {
                    minLabel.textContent = `от ${formatNum(leftValue)} ₽`;
                }
                if (maxLabel instanceof HTMLElement) {
                    maxLabel.textContent = `до ${formatNum(rightValue)} ₽`;
                }
                if (progress instanceof HTMLElement) {
                    if (span <= 0) {
                        progress.style.left = "0%";
                        progress.style.right = "0%";
                    } else {
                        const leftPercent = ((leftValue - rangeMin) / span) * 100;
                        const rightPercent = ((rangeMax - rightValue) / span) * 100;
                        progress.style.left = `${leftPercent}%`;
                        progress.style.right = `${rightPercent}%`;
                    }
                }
            };

            if (span <= 0) {
                minInput.disabled = true;
                maxInput.disabled = true;
            }

            minInput.addEventListener("input", () => render("min"));
            maxInput.addEventListener("input", () => render("max"));
            render();
        });
    }

    function initStickyNavbarState() {
        const navbar = document.querySelector(".store-navbar");
        if (!(navbar instanceof HTMLElement)) {
            return;
        }
        document.body.classList.remove("has-stuck-navbar");
        document.body.style.removeProperty("--store-navbar-height");
        const meta = document.querySelector(".store-header");
        let rafId = 0;
        let stuck = navbar.classList.contains("is-stuck");
        const hysteresis = 12;
        let threshold = 0;

        const recalcThreshold = () => {
            threshold = meta instanceof HTMLElement
                ? Math.max(0, Math.round(meta.getBoundingClientRect().height))
                : 0;
        };

        const update = () => {
            const y = window.scrollY;
            if (!stuck && y > threshold + hysteresis) {
                stuck = true;
            } else if (stuck && y < Math.max(0, threshold - hysteresis)) {
                stuck = false;
            }
            navbar.classList.toggle("is-stuck", stuck);
        };

        const onScroll = () => {
            if (rafId) {
                return;
            }
            rafId = window.requestAnimationFrame(() => {
                rafId = 0;
                update();
            });
        };

        const onResize = () => {
            recalcThreshold();
            update();
        };

        window.addEventListener("scroll", onScroll, {passive: true});
        window.addEventListener("resize", onResize);
        window.addEventListener("orientationchange", onResize);
        recalcThreshold();
        update();
    }

    function syncExpandableTextToggle(context) {
        const expanded = context.root.classList.contains("is-expanded");
        context.toggle.textContent = expanded ? context.labelLess : context.labelMore;
        context.toggle.setAttribute("aria-expanded", expanded ? "true" : "false");
    }

    function measureExpandableTextContext(context) {
        const isVisible = context.root.offsetParent !== null || context.root.getClientRects().length > 0;
        if (!isVisible) {
            syncExpandableTextToggle(context);
            return;
        }

        const linesRaw = Number.parseInt(
            context.root.getAttribute("data-collapsed-lines") || context.content.getAttribute("data-collapsed-lines") || "4",
            10
        );
        const collapsedLines = Number.isFinite(linesRaw) && linesRaw > 0 ? linesRaw : 4;
        context.content.style.setProperty("--collapsed-lines", String(collapsedLines));

        const isExpanded = context.root.classList.contains("is-expanded");
        context.content.classList.add("is-collapsed");
        const hasOverflow = context.content.scrollHeight > context.content.clientHeight + 1;

        if (!hasOverflow) {
            context.root.classList.remove("is-expanded");
            context.content.classList.remove("is-collapsed");
            context.toggle.classList.add("d-none");
            context.toggle.setAttribute("aria-expanded", "false");
            return;
        }

        context.toggle.classList.remove("d-none");
        context.content.classList.toggle("is-collapsed", !isExpanded);
        syncExpandableTextToggle(context);
    }

    function recalcExpandableText() {
        expandableTextContexts.forEach((context) => {
            if (!context.root.isConnected) {
                return;
            }
            measureExpandableTextContext(context);
        });
    }

    function scheduleExpandableTextRecalc() {
        if (expandableTextRaf) {
            return;
        }
        expandableTextRaf = window.requestAnimationFrame(() => {
            expandableTextRaf = 0;
            recalcExpandableText();
        });
    }

    function initExpandableText() {
        document.querySelectorAll(".js-expandable-text").forEach((root) => {
            if (!(root instanceof HTMLElement) || root.dataset.boundExpandable === "1") {
                return;
            }
            const content = root.querySelector(".js-expandable-content");
            const toggle = root.querySelector(".js-expandable-toggle");
            if (!(content instanceof HTMLElement) || !(toggle instanceof HTMLButtonElement)) {
                return;
            }

            root.dataset.boundExpandable = "1";
            const context = {
                root,
                content,
                toggle,
                labelMore: toggle.dataset.labelMore || "Показать ещё",
                labelLess: toggle.dataset.labelLess || "Скрыть"
            };
            expandableTextContexts.push(context);

            toggle.addEventListener("click", () => {
                const expanded = !root.classList.contains("is-expanded");
                root.classList.toggle("is-expanded", expanded);
                content.classList.toggle("is-collapsed", !expanded);
                syncExpandableTextToggle(context);
            });
        });

        if (!expandableTextEventsBound) {
            expandableTextEventsBound = true;
            window.addEventListener("resize", scheduleExpandableTextRecalc);
            window.addEventListener("orientationchange", scheduleExpandableTextRecalc);
            window.addEventListener("load", scheduleExpandableTextRecalc);
            document.addEventListener("shown.bs.collapse", scheduleExpandableTextRecalc);
            document.addEventListener("shown.bs.tab", scheduleExpandableTextRecalc);
        }

        scheduleExpandableTextRecalc();
    }

    async function fetchCartCount() {
        if (!headerCartBadge || typeof window.fetch !== "function") {
            return;
        }
        try {
            const response = await fetch("/api/cart/count", {
                method: "GET",
                headers: {"X-Requested-With": "XMLHttpRequest"}
            });
            if (!response.ok) {
                return;
            }
            const payload = await response.json();
            renderHeaderCartBadge(payload.count);
        } catch (_error) {
            // keep server-rendered badge
        }
    }

    async function submitCartMutation(event) {
        const form = event.currentTarget;
        if (!(form instanceof HTMLFormElement) || typeof window.fetch !== "function") {
            return;
        }
        event.preventDefault();
        event.stopPropagation();

        if (form.dataset.pending === "1") {
            return;
        }
        form.dataset.pending = "1";

        const submitButton = form.querySelector("button[type='submit']");
        if (submitButton instanceof HTMLButtonElement) {
            submitButton.disabled = true;
        }
        const formData = new FormData(form);
        if (form.classList.contains("js-cart-toggle-form")) {
            const productId = form.dataset.productId;
            if (productId) {
                formData.append("expectedQuantity", String(resolveDisplayedProductQuantity(productId)));
            }
        }
        try {
            const response = await fetch(form.action, {
                method: "POST",
                body: formData,
                headers: {"X-Requested-With": "XMLHttpRequest"}
            });
            if (!response.ok) {
                let message = "Не удалось добавить товар в корзину";
                try {
                    const errorPayload = await response.json();
                    if (errorPayload && typeof errorPayload.message === "string" && errorPayload.message.trim()) {
                        message = errorPayload.message.trim();
                    }
                } catch (_parseError) {
                    // ignore
                }
                window.alert(message);
                return;
            }
            const payload = await response.json();
            if (payload && payload.ok) {
                renderHeaderCartBadge(payload.count);
                updateProductQuantity(payload.productId, payload.productQuantity);
            }
        } catch (_error) {
            // no-op
        } finally {
            if (submitButton instanceof HTMLButtonElement) {
                submitButton.disabled = false;
            }
            form.dataset.pending = "0";
        }
    }

    async function submitWishlistToggle(event) {
        const form = event.currentTarget;
        if (!(form instanceof HTMLFormElement) || typeof window.fetch !== "function") {
            return;
        }
        event.preventDefault();
        event.stopPropagation();

        if (form.dataset.pending === "1") {
            return;
        }
        form.dataset.pending = "1";

        const submitButton = form.querySelector("button[type='submit']");
        if (submitButton instanceof HTMLButtonElement) {
            submitButton.disabled = true;
        }
        try {
            const response = await fetch(form.action, {
                method: "POST",
                body: new FormData(form),
                headers: {"X-Requested-With": "XMLHttpRequest"}
            });
            if (!response.ok) {
                return;
            }
            const payload = await response.json();
            if (payload && payload.ok) {
                updateWishlistState(payload.productId, payload.inWishlist);
                if (window.location.pathname.startsWith("/wishlist") && !payload.inWishlist) {
                    window.location.reload();
                }
            }
        } catch (_error) {
            // no-op
        } finally {
            if (submitButton instanceof HTMLButtonElement) {
                submitButton.disabled = false;
            }
            form.dataset.pending = "0";
        }
    }

    initProductSliders();
    initCategorySliders();
    initCategoryPriceRange();
    initStickyNavbarState();
    initExpandableText();
    fetchCartCount();
    refreshCartTotalsFromRows();

    document
        .querySelectorAll("form.js-cart-toggle-form, form.js-cart-add-form, form.js-cart-increment-form, form.js-cart-decrement-form")
        .forEach((form) => {
            if (form.dataset.boundCartMutation === "1") {
                return;
            }
            form.dataset.boundCartMutation = "1";
            form.addEventListener("submit", submitCartMutation);
        });

    document
        .querySelectorAll("form.js-wishlist-toggle-form")
        .forEach((form) => {
            if (form.dataset.boundWishlistMutation === "1") {
                return;
            }
            form.dataset.boundWishlistMutation = "1";
            form.addEventListener("submit", submitWishlistToggle);
        });

    document.querySelectorAll(".js-wishlist-btn").forEach((button) => {
        const icon = button.querySelector("i");
        if (!(icon instanceof HTMLElement)) {
            return;
        }
        icon.classList.remove("bi-heart", "bi-heart-fill");
        icon.classList.add(button.classList.contains("is-active") ? "bi-heart-fill" : "bi-heart");
    });
})();
