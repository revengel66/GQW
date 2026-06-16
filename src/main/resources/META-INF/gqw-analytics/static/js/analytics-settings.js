(function () {
    const inferredBase = window.location.pathname.startsWith("/analytics-admin")
        ? "/analytics-admin/api"
        : "/analytics/api";
    const API_BASE = (window.analyticsApiBase || inferredBase).replace(/\/+$/, "");
    const SETTINGS_API = `${API_BASE}/runtime-settings`;
    const DIAGNOSTICS_API = `${API_BASE}/runtime-settings/diagnostics`;
    const OPERATIONS_API = `${API_BASE}/runtime-settings/operations`;
    const DIAGNOSTICS_REFRESH_MS = 30_000;

    const refs = {};
    let modalInstance = null;
    let loadedPayload = null;
    let diagnosticsTimer = null;
    let operationInFlight = false;
    let runtimeTooltipEl = null;
    let activeSectionObserver = null;

    const RUNTIME_DIAG_HELP = {
        rawRowsEstimate: "Количество исходных событий, сохранённых в аналитике. Если значение быстро растёт, проверьте сроки хранения.",
        rollupRowsEstimate: "Подготовленные записи для быстрых графиков и RCA. Чем их больше, тем быстрее открываются длинные периоды.",
        maxLagMinutes: "Самое большое отставание между текущим временем и последним обновлением агрегатов. Большое значение означает устаревшие графики.",
        minLagMinutes: "Самое маленькое отставание среди агрегатов. Помогает понять, есть ли хотя бы один актуальный набор данных.",
        staleWatermarkCount: "Сколько отметок обновления требуют пересчёта. Ненулевое значение значит, что часть графиков может отставать.",
        currentFiles: "Файлы логов в активной папке. Они используются для поиска логов трассировки по свежим событиям.",
        archiveFiles: "Архивные файлы логов. Они нужны для поиска трассировок старых событий.",
        indexedFiles: "Файлы, уже обработанные индексатором. Чем ближе к общему числу файлов, тем полнее поиск по логам.",
        traceLinks: "Количество трассировок, доступных для быстрого поиска в индексе логов.",
        pendingFiles: "Очередь файлов, которые ещё не обработаны индексатором. Если число растёт, запустите индексацию логов.",
        cleanupCandidates: "Файлы и записи индекса, которые могут быть удалены обслуживанием по правилам хранения."
    };

    const RUNTIME_TABLE_HELP = {
        logIndex: "Показывает состояние индекса логов: когда он обновлялся, есть ли очередь и ошибки обработки.",
        watermarks: "Показывает актуальность агрегатов по интервалам. Если отставание выше интервала обновления, графики могут показывать старые данные.",
        eta: "Показывает, какие агрегаты ещё ждут планового обновления и когда они догонят последние события.",
        tableSizes: "Помогает контролировать рост аналитических таблиц и оценивать влияние аналитики на базу данных."
    };

    const RUNTIME_COLUMN_HELP = {
        logStatus: "Общее состояние индекса логов.",
        logLastIndexedAt: "Когда индексатор последний раз успешно проверял файлы логов.",
        logPendingFiles: "Сколько файлов ещё ждут обработки.",
        logMissingFiles: "Сколько архивов указаны в индексе, но не найдены на диске.",
        logTooLargeFiles: "Файлы, пропущенные из-за ограничения размера.",
        logIndexErrorFiles: "Файлы, при обработке которых возникла ошибка.",
        logExcerptRows: "Короткие важные фрагменты логов, сохранённые для быстрого просмотра.",
        logLastError: "Последняя ошибка индексатора логов.",
        scope: "Какой аналитический набор обновляется: события, этапы, метрики или фильтры.",
        interval: "Период агрегации данных.",
        watermark: "Последнее время, до которого данные уже подготовлены.",
        lag: "Разница между текущим временем и последним обновлением.",
        enabled: "Участвует ли этот агрегат в построении аналитики.",
        status: "Текущее состояние обновления.",
        eta: "Оценка времени до актуального состояния.",
        comment: "Пояснение, почему данные актуальны, ждут обновления или отключены.",
        tableName: "Физическая таблица базы данных с аналитическими данными.",
        rows: "Приблизительное количество записей.",
        size: "Объём, который таблица занимает на диске.",
        minTime: "Начало истории данных в таблице.",
        maxTime: "Самые свежие данные в таблице."
    };

    document.addEventListener("DOMContentLoaded", () => {
        initRefs();
        const hasPage = Boolean(refs.pageRoot);
        const hasModalTrigger = Boolean(refs.openButton && refs.modalEl);
        if (!hasPage && !hasModalTrigger) {
            return;
        }
        if (hasModalTrigger) {
            modalInstance = new bootstrap.Modal(refs.modalEl);
        }
        bindEvents();
        if (hasPage) {
            void reloadSettings();
            startDiagnosticsPolling();
        }
    });

    function initRefs() {
        refs.openButton = document.getElementById("analytics-runtime-settings-open");
        refs.modalEl = document.getElementById("analytics-runtime-settings-modal");
        refs.pageRoot = document.getElementById("analytics-runtime-settings-page");
        refs.navRoot = document.getElementById("analytics-runtime-settings-nav");
        refs.formRoot = document.getElementById("analytics-runtime-settings-form");
        refs.error = document.getElementById("analytics-runtime-settings-error");
        refs.status = document.getElementById("analytics-runtime-settings-status");
        refs.saveButton = document.getElementById("analytics-runtime-settings-save");
        refs.diagnosticsStatus = document.getElementById("analytics-runtime-diagnostics-status");
        refs.diagnosticsContent = document.getElementById("analytics-runtime-diagnostics-content");
        refs.diagnosticsRefreshButton = document.getElementById("analytics-runtime-diagnostics-refresh");
        refs.operationStatus = document.getElementById("analytics-runtime-operation-status");
        refs.opRefreshButton = document.getElementById("analytics-runtime-op-refresh");
        refs.opBackfillButton = document.getElementById("analytics-runtime-op-backfill");
        refs.opLifecycleButton = document.getElementById("analytics-runtime-op-lifecycle");
        refs.opLogIndexButton = document.getElementById("analytics-runtime-op-log-index");
        refs.opLogCleanupButton = document.getElementById("analytics-runtime-op-log-cleanup");
        refs.helpModalEl = document.getElementById("analytics-help-modal");
        refs.helpModalTitle = document.getElementById("analytics-help-modal-title");
        refs.helpModalBody = document.getElementById("analytics-help-modal-body");
    }

    function bindEvents() {
        refs.openButton?.addEventListener("click", async () => {
            if (!modalInstance) {
                return;
            }
            modalInstance.show();
            await reloadSettings();
            startDiagnosticsPolling();
        });
        refs.modalEl?.addEventListener("hidden.bs.modal", () => {
            stopDiagnosticsPolling();
        });
        refs.helpModalEl?.addEventListener("hidden.bs.modal", () => {
            refs.helpModalEl.classList.remove("analytics-help-modal--nested");
            document.querySelectorAll(".analytics-help-backdrop--nested").forEach((backdrop) => {
                backdrop.classList.remove("analytics-help-backdrop--nested");
            });
            if (refs.modalEl?.classList.contains("show")) {
                document.body.classList.add("modal-open");
            }
        });
        refs.saveButton?.addEventListener("click", async () => {
            await saveSettings();
        });
        refs.diagnosticsRefreshButton?.addEventListener("click", () => {
            void reloadDiagnostics();
        });
        refs.opRefreshButton?.addEventListener("click", () => {
            void runOperation("refresh_now", "Агрегаты обновлены.");
        });
        refs.opBackfillButton?.addEventListener("click", () => {
            void runOperation("backfill_now", "Пересчёт истории запущен.");
        });
        refs.opLifecycleButton?.addEventListener("click", () => {
            void runOperation("lifecycle_now", "Обслуживание выполнено.");
        });
        refs.opLogIndexButton?.addEventListener("click", () => {
            void runOperation("index_logs_now", "Индексация логов выполнена.");
        });
        refs.opLogCleanupButton?.addEventListener("click", () => {
            void runOperation("cleanup_logs_now", "Очистка старых логов выполнена.");
        });
        const helpScope = runtimeHelpScope();
        helpScope?.addEventListener("click", handleRuntimeHelpClick, true);
        helpScope?.addEventListener("mouseover", handleRuntimeTooltipEnter, true);
        helpScope?.addEventListener("focusin", handleRuntimeTooltipEnter, true);
        helpScope?.addEventListener("mouseout", handleRuntimeTooltipLeave, true);
        helpScope?.addEventListener("focusout", handleRuntimeTooltipLeave, true);
        helpScope?.addEventListener("scroll", hideRuntimeTooltip, true);
        helpScope?.addEventListener("keydown", (event) => {
            if (event.key !== "Enter" && event.key !== " ") {
                return;
            }
            handleRuntimeHelpClick(event);
        }, true);
    }

    function runtimeHelpScope() {
        return refs.pageRoot || refs.modalEl;
    }

    async function reloadSettings() {
        setError("");
        setStatus("Загрузка настроек...");
        setDiagnosticsStatus("Загрузка диагностики...");
        setOperationStatus("");
        renderDiagnosticsContent("<div class='text-muted small'>Загрузка...</div>");
        setSaveButtonLabel("Загрузка...");
        toggleSave(false);

        const [settingsResult, diagnosticsResult] = await Promise.allSettled([
            fetchJson(SETTINGS_API),
            fetchJson(DIAGNOSTICS_API)
        ]);

        try {
            if (settingsResult.status !== "fulfilled") {
                throw settingsResult.reason;
            }
            loadedPayload = settingsResult.value;
            renderSettings(loadedPayload);
            setStatus(buildStatusText(loadedPayload));
        } catch (error) {
            setError(error?.message || "Не удалось загрузить настройки.");
            setStatus("");
        }

        if (diagnosticsResult.status === "fulfilled") {
            renderDiagnostics(diagnosticsResult.value);
            setDiagnosticsStatus(buildDiagnosticsStatusText(diagnosticsResult.value));
        } else {
            const detail = diagnosticsResult.reason?.message || "не удалось получить диагностику";
            setDiagnosticsStatus(`Ошибка диагностики: ${detail}`);
            renderDiagnosticsContent("<div class='text-danger small'>Диагностика временно недоступна.</div>");
        }

        toggleSave(true);
        setSaveButtonLabel("");
    }

    async function saveSettings() {
        if (!loadedPayload) {
            return;
        }
        if (!validateSettingsForm()) {
            return;
        }
        const values = collectValues();
        const scrollY = window.scrollY;
        setSaveButtonLabel("Сохранение...");
        setError("");
        setStatus("Сохраняем...");
        toggleSave(false);
        try {
            loadedPayload = await postJson(SETTINGS_API, {values});
            renderSettings(loadedPayload);
            if (refs.pageRoot) {
                window.scrollTo({top: scrollY, behavior: "instant"});
            } else if (modalInstance) {
                modalInstance.hide();
            }
            setStatus(buildStatusText(loadedPayload, "Сохранено."));
            showSettingsToast("Настройки сохранены");
            await reloadDiagnostics();
        } catch (error) {
            setError(error?.message || "Не удалось сохранить настройки.");
            console.warn("Failed to save analytics settings", error);
            showSettingsToast("Не удалось сохранить настройки", true);
        } finally {
            toggleSave(true);
            setSaveButtonLabel("");
        }
    }

    async function reloadDiagnostics() {
        setDiagnosticsStatus("Обновляем диагностику...");
        try {
            const payload = await fetchJson(DIAGNOSTICS_API);
            renderDiagnostics(payload);
            setDiagnosticsStatus(buildDiagnosticsStatusText(payload));
        } catch (error) {
            const detail = error?.message || "неизвестная ошибка";
            setDiagnosticsStatus(`Ошибка диагностики: ${detail}`);
            renderDiagnosticsContent("<div class='text-danger small'>Диагностика временно недоступна.</div>");
        }
    }

    async function runOperation(action, successMessage) {
        if (operationInFlight) {
            return;
        }
        operationInFlight = true;
        toggleOperationButtons(false);
        setOperationStatus("Выполняем операцию...");
        try {
            const payload = await postJson(OPERATIONS_API, {action});
            const tookMs = Number(payload?.tookMs || 0);
            const tookText = Number.isFinite(tookMs) && tookMs > 0 ? ` (${Math.round(tookMs)} ms)` : "";
            const details = Array.isArray(payload?.steps) && payload.steps.length
                ? ` ${payload.steps.map(formatOperationStep).join("; ")}`
                : "";
            setOperationStatus(`${successMessage}${tookText}.${details}`);
            if (payload?.diagnostics) {
                renderDiagnostics(payload.diagnostics);
                setDiagnosticsStatus(buildDiagnosticsStatusText(payload.diagnostics));
            } else {
                await reloadDiagnostics();
            }
        } catch (error) {
            setOperationStatus(`Ошибка операции: ${error?.message || "неизвестная ошибка"}`);
        } finally {
            operationInFlight = false;
            toggleOperationButtons(true);
        }
    }

    function collectValues() {
        const values = {};
        refs.formRoot?.querySelectorAll("[data-setting-key]").forEach((input) => {
            const key = String(input.getAttribute("data-setting-key") || "").trim();
            const kind = String(input.getAttribute("data-setting-kind") || "").trim().toUpperCase();
            if (!key) {
                return;
            }
            if (kind === "BOOLEAN") {
                values[key] = input.checked ? "true" : "false";
                return;
            }
            values[key] = String(input.value ?? "").trim();
        });
        return values;
    }

    function validateSettingsForm() {
        const invalid = refs.formRoot?.querySelector(":invalid");
        if (!invalid) {
            return true;
        }
        invalid.reportValidity?.();
        const label = invalid.closest(".analytics-runtime-item")?.querySelector(".analytics-runtime-item-label")?.textContent?.trim();
        const min = invalid.getAttribute("min");
        const max = invalid.getAttribute("max");
        const rangeText = min || max
            ? ` Допустимый диапазон: ${min || "-∞"}...${max || "+∞"}.`
            : "";
        setError(`Проверьте значение параметра${label ? ` «${label}»` : ""}.${rangeText}`);
        invalid.focus?.();
        return false;
    }

    function renderSettings(payload) {
        const groups = Array.isArray(payload?.groups) ? payload.groups : [];
        refs.formRoot.innerHTML = groups.map((group) => renderGroup(group)).join("");
        renderSettingsNavigation(groups);
        observeRuntimeSections();
        initTooltips();
        attachRuntimeSettingTooltips();
    }

    function renderDiagnostics(payload) {
        const summary = payload?.summary || {};
        const logIndex = payload?.logIndex || {};
        const tables = Array.isArray(payload?.tables) ? payload.tables : [];
        const watermarks = Array.isArray(payload?.watermarks) ? payload.watermarks : [];
        const etaRows = Array.isArray(payload?.eta) ? payload.eta : [];

        const html = `
            <div class="analytics-runtime-diag-cards">
                ${renderDiagCard("Сырые события", formatNullableInt(summary.rawRowsEstimate))}
                ${renderDiagCard("Подготовленные агрегаты", formatNullableInt(summary.rollupRowsEstimate))}
                ${renderDiagCard("Максимальное отставание", formatLag(summary.maxLagMinutes))}
                ${renderDiagCard("Минимальное отставание", formatLag(summary.minLagMinutes))}
                ${renderDiagCard("Устаревших отметок обновления", formatNullableInt(summary.staleWatermarkCount))}
                ${renderDiagCard("Текущих файлов логов", formatNullableInt(logIndex.currentFiles))}
                ${renderDiagCard("Архивов логов", formatNullableInt(logIndex.archiveFiles))}
                ${renderDiagCard("Проиндексированных файлов", formatNullableInt(logIndex.indexedFiles))}
                ${renderDiagCard("Трассировки в индексе логов", formatNullableInt(logIndex.traceLinks))}
                ${renderDiagCard("Файлов ожидают индексации", formatNullableInt(logIndex.pendingFiles))}
                ${renderDiagCard("Кандидатов на очистку", formatNullableInt(logIndex.cleanupCandidates))}
            </div>
            <div class="analytics-runtime-diag-block-title">Логи и архивы</div>
            <p class="analytics-runtime-diag-description">
                Индекс логов помогает находить трассировки даже тогда, когда исходный файл уже перемещён в архив. Полные логи остаются в файлах, а в базе хранится только быстрый указатель и короткая диагностическая сводка.
            </p>
            <div class="table-responsive mb-4">
                <table class="table table-sm analytics-runtime-diag-table">
                    <tbody>
                        <tr><th>Статус</th><td>${renderEtaStatus(logIndex.status || "OK")}</td></tr>
                        <tr><th>Последняя индексация</th><td>${escapeHtml(formatDateTime(logIndex.lastIndexedAt))}</td></tr>
                        <tr><th>Файлов ожидают индексации</th><td>${escapeHtml(formatNullableInt(logIndex.pendingFiles))}</td></tr>
                        <tr><th>Архивов не найдено</th><td>${escapeHtml(formatNullableInt(logIndex.missingFiles))}</td></tr>
                        <tr><th>Слишком больших файлов</th><td>${escapeHtml(formatNullableInt(logIndex.skippedTooLargeFiles))}</td></tr>
                        <tr><th>Ошибок индексации</th><td>${escapeHtml(formatNullableInt(logIndex.indexErrorFiles))}</td></tr>
                        <tr><th>Важных фрагментов в индексе</th><td>${escapeHtml(formatNullableInt(logIndex.excerptRows))}</td></tr>
                        <tr><th>Последняя ошибка</th><td>${escapeHtml(logIndex.lastError || "—")}</td></tr>
                    </tbody>
                </table>
            </div>
            <div class="analytics-runtime-diag-block-title">Свежесть агрегатов по интервалам</div>
            <div class="table-responsive mb-4">
                <table class="table table-sm analytics-runtime-diag-table">
                    <thead>
                    <tr>
                        <th>Тип данных</th>
                        <th>Интервал</th>
                        <th>Данные обновлены до</th>
                        <th>Отставание</th>
                        <th>Включено</th>
                    </tr>
                    </thead>
                    <tbody>
                    ${watermarks.map((row) => `
                        <tr>
                            <td>${escapeHtml(formatScope(row.scopeCode))}</td>
                            <td>${escapeHtml(formatGranularity(row.granularityMinutes))}</td>
                            <td>${escapeHtml(formatDateTime(row.watermarkAt))}</td>
                            <td>${escapeHtml(formatLag(row.lagMinutes))}</td>
                            <td>${row.enabled ? "<span class='analytics-runtime-diag-status is-ok'>Да</span>" : "<span class='analytics-runtime-diag-status is-off'>Нет</span>"}</td>
                        </tr>
                    `).join("") || "<tr><td colspan='5' class='text-muted'>Нет данных</td></tr>"}
                    </tbody>
                </table>
            </div>
            <div class="analytics-runtime-diag-block-title">Когда данные догонят последние события</div>
            <div class="table-responsive mb-4">
                <table class="table table-sm analytics-runtime-diag-table analytics-runtime-diag-table-when">
                    <thead>
                    <tr>
                        <th>Тип данных</th>
                        <th>Интервал</th>
                        <th>Статус</th>
                        <th>Осталось примерно</th>
                        <th>Комментарий</th>
                    </tr>
                    </thead>
                    <tbody>
                    ${etaRows.map((row) => `
                        <tr>
                            <td>${escapeHtml(formatScope(row.scopeCode))}</td>
                            <td>${escapeHtml(formatGranularity(row.granularityMinutes))}</td>
                            <td>${renderEtaStatus(row.status)}</td>
                            <td>${escapeHtml(formatEta(row.etaMinutes))}</td>
                            <td>${escapeHtml(formatEtaDetails(row.details, row.status))}</td>
                        </tr>
                    `).join("") || "<tr><td colspan='5' class='text-muted'>Нет данных</td></tr>"}
                    </tbody>
                </table>
            </div>
            <div class="analytics-runtime-diag-block-title">Размеры таблиц с аналитическими данными</div>
            <div class="table-responsive mb-4">
                <table class="table table-sm analytics-runtime-diag-table">
                    <thead>
                    <tr>
                        <th>Таблица</th>
                        <th>Строки (оценка)</th>
                        <th>Размер</th>
                        <th>Самая ранняя запись</th>
                        <th>Самая свежая запись</th>
                    </tr>
                    </thead>
                    <tbody>
                    ${tables.map((row) => `
                        <tr>
                            <td>${escapeHtml(row.tableName || "-")}</td>
                            <td>${escapeHtml(formatNullableInt(row.rowEstimate))}</td>
                            <td>${escapeHtml(formatBytes(row.totalBytes))}</td>
                            <td>${escapeHtml(formatDateTime(row.minTime))}</td>
                            <td>${escapeHtml(formatDateTime(row.maxTime))}</td>
                        </tr>
                    `).join("") || "<tr><td colspan='5' class='text-muted'>Нет данных</td></tr>"}
                    </tbody>
                </table>
            </div>
        `;

        renderDiagnosticsContent(html);
        attachRuntimeDiagnosticsTooltips();
    }

    function renderGroup(group) {
        const groupCode = String(group?.code || "");
        const title = escapeHtml(formatRuntimeGroupTitle(groupCode, group?.title));
        const description = escapeHtml(formatRuntimeGroupDescription(groupCode, group?.description));
        const settings = (Array.isArray(group?.settings) ? group.settings : [])
            .filter((setting) => !isHiddenRuntimeSetting(setting?.key) && !isReservedRuntimeSetting(setting?.key));
        const primarySettings = settings.filter((setting) => !isAdvancedLogIndexerSetting(setting?.key));
        const advancedSettings = settings.filter((setting) => isAdvancedLogIndexerSetting(setting?.key));
        const rows = primarySettings.map((setting) => renderSetting(setting)).join("");
        const advancedRows = advancedSettings.map((setting) => renderSetting(setting)).join("");
        const advancedBlock = advancedRows ? `
            <details class="analytics-runtime-advanced mt-3">
                <summary class="analytics-runtime-advanced-summary">
                    <span>Дополнительные настройки индексатора логов</span>
                    <span class="analytics-runtime-advanced-sub">Внутренние лимиты обработки логов</span>
                </summary>
                <div class="analytics-runtime-grid analytics-runtime-advanced-grid">
                    ${advancedRows}
                </div>
            </details>
        ` : "";
        return `
            <section class="analytics-runtime-group analytics-settings-section mb-3" id="${runtimeGroupSectionId(groupCode)}">
                <div class="analytics-runtime-group-head">
                    <div class="analytics-runtime-group-title">${title}</div>
                    <div class="analytics-runtime-group-sub">${description}</div>
                </div>
                <div class="analytics-runtime-grid">
                    ${rows}
                </div>
                ${advancedBlock}
            </section>
        `;
    }

    function renderSettingsNavigation(groups) {
        if (!refs.navRoot) {
            return;
        }
        const groupLinks = (Array.isArray(groups) ? groups : [])
            .map((group) => {
                const groupCode = String(group?.code || "");
                const title = escapeHtml(formatRuntimeGroupTitle(groupCode, group?.title));
                return `<a class="analytics-settings-nav-link" href="#${runtimeGroupSectionId(groupCode)}">${title}</a>`;
            })
            .join("");
        refs.navRoot.innerHTML = `
            <a class="analytics-settings-nav-link is-active" href="#runtime-section-diagnostics">Диагностика состояния</a>
            ${groupLinks}
        `;
        refs.navRoot.querySelectorAll("a[href^='#']").forEach((link) => {
            link.addEventListener("click", (event) => {
                const target = document.querySelector(link.getAttribute("href"));
                if (!target) {
                    return;
                }
                event.preventDefault();
                target.scrollIntoView({behavior: "smooth", block: "start"});
                setActiveSettingsNav(link.getAttribute("href").slice(1));
            });
        });
    }

    function observeRuntimeSections() {
        if (!refs.pageRoot || !refs.navRoot || typeof IntersectionObserver === "undefined") {
            return;
        }
        activeSectionObserver?.disconnect();
        const sections = refs.pageRoot.querySelectorAll(".analytics-settings-section[id]");
        activeSectionObserver = new IntersectionObserver((entries) => {
            const visible = entries
                .filter((entry) => entry.isIntersecting)
                .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
            if (visible?.target?.id) {
                setActiveSettingsNav(visible.target.id);
            }
        }, {
            root: null,
            rootMargin: "-90px 0px -55% 0px",
            threshold: [0.1, 0.25, 0.5]
        });
        sections.forEach((section) => activeSectionObserver.observe(section));
    }

    function setActiveSettingsNav(sectionId) {
        if (!refs.navRoot || !sectionId) {
            return;
        }
        refs.navRoot.querySelectorAll(".analytics-settings-nav-link").forEach((link) => {
            link.classList.toggle("is-active", link.getAttribute("href") === `#${sectionId}`);
        });
    }

    function runtimeGroupSectionId(groupCode) {
        const normalized = String(groupCode || "settings")
            .trim()
            .toLowerCase()
            .replace(/[^a-z0-9]+/g, "-")
            .replace(/^-+|-+$/g, "");
        return `runtime-section-${normalized || "settings"}`;
    }

    function renderSetting(setting) {
        const rawKey = String(setting?.key || "");
        const key = escapeHtml(rawKey);
        const kind = String(setting?.kind || "TEXT").toUpperCase();
        const label = escapeHtml(formatRuntimeSettingLabel(rawKey, setting?.label));
        const helpRaw = formatRuntimeSettingHelp(rawKey, setting?.help);
        const help = escapeHtml(helpRaw);
        const value = setting?.value == null ? "" : String(setting.value);
        const defaultValue = setting?.defaultValue == null ? "" : String(setting.defaultValue);
        const min = setting?.minValue;
        const max = setting?.maxValue;
        const custom = Boolean(setting?.custom);
        const input = renderInput(kind, key, value, min, max, setting?.options);
        const valueHint = runtimeSettingValueHint(rawKey);
        const defaultLabel = `\u041f\u043e \u0443\u043c\u043e\u043b\u0447\u0430\u043d\u0438\u044e ${escapeHtml(defaultValue || "\u2014")}`;

        return `
            <div class="analytics-runtime-item">
                <div class="analytics-runtime-item-head">
                    <label class="analytics-runtime-item-label" for="setting-${key}">${label}</label>
                    <button class="analytics-runtime-help-badge"
                            type="button"
                            data-runtime-help="setting"
                            data-runtime-help-title="${label}"
                            data-runtime-help-body="${help}"
                            data-runtime-help-default="${defaultLabel}"
                            aria-label="Открыть пояснение параметра">?</button>
                </div>
                <div class="analytics-runtime-item-input">
                    ${input}
                </div>
                ${valueHint ? `<div class="analytics-runtime-item-hint">${escapeHtml(valueHint)}</div>` : ""}
                <div class="analytics-runtime-item-meta">
                    <span class="text-muted">${defaultLabel}</span>
                    ${isReservedRuntimeSetting(rawKey) ? "<span class=\"text-warning\">Пока не используется в текущей версии</span>" : ""}
                </div>
            </div>
        `;
    }

    function renderInput(kind, key, value, min, max, options) {
        const id = `setting-${key}`;
        if (kind === "BOOLEAN") {
            const checked = value.toLowerCase() === "true" ? "checked" : "";
            return `
                <div class="form-check form-switch">
                    <input class="form-check-input"
                           type="checkbox"
                           id="${id}"
                           data-setting-key="${key}"
                           data-setting-kind="${kind}"
                           ${checked}>
                </div>
            `;
        }
        if (kind === "INTEGER") {
            const minAttr = Number.isFinite(Number(min)) ? `min="${Number(min)}"` : "";
            const maxAttr = Number.isFinite(Number(max)) ? `max="${Number(max)}"` : "";
            const unit = runtimeSettingUnit(key);
            const unitHtml = unit ? `<span class="input-group-text analytics-runtime-unit">${escapeHtml(unit)}</span>` : "";
            return `
                <div class="input-group input-group-sm analytics-runtime-input-group">
                    <input class="form-control form-control-sm"
                           type="number"
                           id="${id}"
                           value="${escapeHtml(value)}"
                           ${minAttr}
                           ${maxAttr}
                           step="1"
                           data-setting-key="${key}"
                           data-setting-kind="${kind}">
                    ${unitHtml}
                </div>
            `;
        }
        if (kind === "ENUM") {
            const list = Array.isArray(options) ? options : [];
            const optionsHtml = list.map((option) => {
                const optionValue = String(option?.value ?? "");
                const selected = optionValue.toUpperCase() === value.toUpperCase() ? "selected" : "";
                return `<option value="${escapeHtml(optionValue)}" ${selected}>${escapeHtml(formatSettingOptionLabel(optionValue, option?.label || optionValue))}</option>`;
            }).join("");
            return `
                <select class="form-select form-select-sm"
                        id="${id}"
                        data-setting-key="${key}"
                        data-setting-kind="${kind}">
                    ${optionsHtml}
                </select>
            `;
        }
        return `
            <input class="form-control form-control-sm"
                   type="text"
                   id="${id}"
                   value="${escapeHtml(value)}"
                   data-setting-key="${key}"
            data-setting-kind="${kind}">
        `;
    }

    function isAdvancedLogIndexerSetting(key) {
        return new Set([
            "analytics.log-index.batch-size",
            "analytics.log-index.max-file-size-mb",
            "analytics.log-index.max-lines-per-trace",
            "analytics.log-index.max-excerpts-per-trace",
            "analytics.log-index.excerpt-max-length",
            "analytics.log-index.archive-read-max-lines",
            "analytics.log-index.include-levels",
            "analytics.log-retention.delete-batch-size",
            "analytics.log-retention.safe-mode-enabled",
            "analytics.log-retention.archive-indexed-only"
        ]).has(String(key || ""));
    }

    function runtimeSettingUnit(key) {
        const units = {
            "analytics.lifecycle.interval-minutes": "мин",
            "analytics.lifecycle.delete-batch-size": "событий",
            "analytics.raw.retention.days": "дней",
            "analytics.aggregate.retention.days": "дней",
            "analytics.time-rollup.overlap-minutes": "мин",
            "analytics.time-rollup.bootstrap-lookback-days": "дней",
            "analytics.time-rollup.refresh-interval-minutes": "мин",
            "analytics.stage-metric-rollup.overlap-minutes": "мин",
            "analytics.stage-metric-rollup.bootstrap-lookback-days": "дней",
            "analytics.stage-metric-rollup.refresh-interval-minutes": "мин",
            "analytics.filter-rollup.long-range-days": "дней",
            "analytics.filter-rollup.refresh-recent-days": "дней",
            "analytics.filter-rollup.refresh-interval-minutes": "мин",
            "analytics.filter-rollup.retention.days": "дней",
            "analytics.event-rollup.retention.1m.days": "дней",
            "analytics.event-rollup.retention.5m.days": "дней",
            "analytics.event-rollup.retention.1h.days": "дней",
            "analytics.event-rollup.retention.1d.days": "дней",
            "analytics.stage-rollup.retention.1m.days": "дней",
            "analytics.stage-rollup.retention.5m.days": "дней",
            "analytics.stage-rollup.retention.1h.days": "дней",
            "analytics.stage-rollup.retention.1d.days": "дней",
            "analytics.stage-metric-rollup.retention.1m.days": "дней",
            "analytics.stage-metric-rollup.retention.5m.days": "дней",
            "analytics.stage-metric-rollup.retention.1h.days": "дней",
            "analytics.stage-metric-rollup.retention.1d.days": "дней",
            "analytics.log-index.interval-minutes": "мин",
            "analytics.log-index.batch-size": "файлов",
            "analytics.log-index.max-file-size-mb": "МБ",
            "analytics.log-index.max-lines-per-trace": "строк",
            "analytics.log-index.max-excerpts-per-trace": "фрагментов",
            "analytics.log-index.excerpt-max-length": "символов",
            "analytics.log-index.retention-days": "дней",
            "analytics.log-index.archive-read-max-lines": "строк",
            "analytics.log-retention.current-days": "дней",
            "analytics.log-retention.archive-days": "дней",
            "analytics.log-retention.index-days": "дней",
            "analytics.log-retention.interval-hours": "часов",
            "analytics.log-retention.delete-batch-size": "файлов"
        };
        return units[String(key || "")] || "";
    }

    function runtimeSettingValueHint(key) {
        if (String(key || "") === "analytics.log-index.include-levels") {
            return "Допустимые значения: FATAL, ERROR, WARN, SLOW.";
        }
        return "";
    }

    function formatRuntimeGroupTitle(code, fallback) {
        const titles = {
            STORAGE: "Хранение данных",
            ROLLUP: "Подготовка данных для графиков",
            RETENTION: "Срок хранения агрегатов",
            PARTITIONING: "Обслуживание таблиц",
            LOG_ARCHIVE: "Логи и архивы"
        };
        return titles[String(code || "").toUpperCase()] || cleanRuntimeText(fallback, code || "");
    }

    function formatRuntimeGroupDescription(code, fallback) {
        const descriptions = {
            STORAGE: "Сроки хранения сырых событий и резервные настройки переноса старой истории.",
            ROLLUP: "Фоновый пересчёт подготовленных данных, из которых строятся быстрые графики.",
            RETENTION: "Сколько хранить подготовленные данные для минутных, часовых и дневных интервалов.",
            PARTITIONING: "Плановые правила обслуживания больших таблиц. Часть параметров зарезервирована.",
            LOG_ARCHIVE: "Индексация, чтение и очистка текущих и архивных файлов логов."
        };
        return descriptions[String(code || "").toUpperCase()] || cleanRuntimeText(fallback, "");
    }

    function formatRuntimeSettingLabel(key, fallback) {
        const labels = {
            "analytics.lifecycle.enabled": "Автоматическое обслуживание включено",
            "analytics.lifecycle.interval-minutes": "Как часто запускать обслуживание",
            "analytics.lifecycle.delete-batch-size": "Событий удалять за один проход",
            "analytics.raw.retention.days": "Срок хранения сырых событий",
            "analytics.aggregate.retention.days": "Срок хранения агрегатов",
            "analytics.raw.hot.days": "Период быстрых сырых данных",
            "analytics.raw.warm.days": "Период редко используемых сырых данных",
            "analytics.tiering.enabled": "Перенос старых данных включён",
            "analytics.tiering.cold.provider": "Хранилище для старой истории",
            "analytics.tiering.cold.location": "Путь к хранилищу старой истории",
            "analytics.tail-merge.enabled": "Дочитывать свежие события после агрегатов",
            "analytics.time-rollup.enabled": "Агрегаты по времени включены",
            "analytics.time-rollup.overlap-minutes": "Запас пересчёта по времени",
            "analytics.time-rollup.bootstrap-lookback-days": "Глубина первого пересчёта по времени",
            "analytics.time-rollup.refresh-interval-minutes": "Как часто обновлять агрегаты по времени",
            "analytics.stage-metric-rollup.enabled": "Агрегаты метрик этапов включены",
            "analytics.stage-metric-rollup.overlap-minutes": "Запас пересчёта метрик этапов",
            "analytics.stage-metric-rollup.bootstrap-lookback-days": "Глубина первого пересчёта метрик этапов",
            "analytics.stage-metric-rollup.refresh-interval-minutes": "Как часто обновлять метрики этапов",
            "analytics.filter-rollup.enabled": "Быстрые фильтры для длинных периодов включены",
            "analytics.filter-rollup.long-range-days": "Когда включать быстрые фильтры",
            "analytics.filter-rollup.refresh-recent-days": "Сколько последних дней обновлять для фильтров",
            "analytics.filter-rollup.refresh-interval-minutes": "Как часто обновлять быстрые фильтры",
            "analytics.event-rollup.retention.1m.days": "События: хранить данные по 1 минуте",
            "analytics.event-rollup.retention.5m.days": "События: хранить данные по 5 минут",
            "analytics.event-rollup.retention.1h.days": "События: хранить данные по 1 часу",
            "analytics.event-rollup.retention.1d.days": "События: хранить данные по 1 дню",
            "analytics.stage-rollup.retention.1m.days": "Этапы: хранить данные по 1 минуте",
            "analytics.stage-rollup.retention.5m.days": "Этапы: хранить данные по 5 минут",
            "analytics.stage-rollup.retention.1h.days": "Этапы: хранить данные по 1 часу",
            "analytics.stage-rollup.retention.1d.days": "Этапы: хранить данные по 1 дню",
            "analytics.stage-metric-rollup.retention.1m.days": "Метрики этапов: хранить данные по 1 минуте",
            "analytics.stage-metric-rollup.retention.5m.days": "Метрики этапов: хранить данные по 5 минут",
            "analytics.stage-metric-rollup.retention.1h.days": "Метрики этапов: хранить данные по 1 часу",
            "analytics.stage-metric-rollup.retention.1d.days": "Метрики этапов: хранить данные по 1 дню",
            "analytics.filter-rollup.retention.days": "Фильтры: срок хранения подготовленных данных",
            "analytics.partitioning.enabled": "Автоматически обслуживать разделы таблиц",
            "analytics.partitioning.strategy": "Как делить большие таблицы",
            "analytics.partitioning.precreate-days": "Создавать разделы заранее",
            "analytics.partitioning.drop-after-days": "Удалять старые разделы после",
            "analytics.log-index.enabled": "Индексация логов включена",
            "analytics.log-index.interval-minutes": "Как часто проверять логи",
            "analytics.log-index.batch-size": "Файлов за один запуск",
            "analytics.log-index.current-logs-enabled": "Индексировать текущие логи",
            "analytics.log-index.archives-enabled": "Индексировать архивы",
            "analytics.log-index.max-file-size-mb": "Максимальный размер файла",
            "analytics.log-index.max-lines-per-trace": "Строк на одну трассировку",
            "analytics.log-index.max-excerpts-per-trace": "Важных фрагментов на трассировку",
            "analytics.log-index.excerpt-max-length": "Длина одного фрагмента",
            "analytics.log-index.retention-days": "Срок хранения индекса",
            "analytics.log-index.include-levels": "Уровни для важных фрагментов",
            "analytics.log-index.archive-read-enabled": "Читать архивы из интерфейса",
            "analytics.log-index.archive-read-max-lines": "Максимум строк из архива",
            "analytics.log-index.allowed-directory": "Папка с логами",
            "analytics.log-retention.cleanup-enabled": "Очистка старых логов включена",
            "analytics.log-retention.current-days": "Хранить текущие логи, дней",
            "analytics.log-retention.archive-days": "Хранить архивы логов, дней",
            "analytics.log-retention.index-days": "Хранить индекс логов, дней",
            "analytics.log-retention.interval-hours": "Как часто очищать логи, часов",
            "analytics.log-retention.delete-batch-size": "Файлов удалять за один запуск",
            "analytics.log-retention.archive-indexed-only": "Удалять архив только после индексации",
            "analytics.log-retention.safe-mode-enabled": "Безопасная очистка"
        };
        return labels[key] || cleanRuntimeText(fallback, key || "Параметр");
    }

    function formatRuntimeSettingHelp(key, fallback) {
        const normalizedKey = String(key || "");
        const explicitHelp = runtimeSettingHelpText(normalizedKey);
        if (explicitHelp) {
            return explicitHelp;
        }
        if (isReservedRuntimeSetting(normalizedKey)) {
            return "Параметр зарезервирован для будущих сценариев хранения или обслуживания данных. В текущей версии изменение этого значения не влияет на работу Analytics.";
        }
        if (normalizedKey.includes("rollup.retention")) {
            return "Определяет, сколько дней хранить подготовленные данные для выбранной детализации. Больший срок даёт более длинную историю анализа, но занимает больше места в базе данных.";
        }
        if (normalizedKey.startsWith("analytics.log-index.")) {
            return "Управляет индексированием логов: как часто читать файлы, сколько данных сохранять для трассировок и как долго хранить быстрый индекс поиска.";
        }
        if (normalizedKey.startsWith("analytics.log-retention.")) {
            return "Управляет хранением и очисткой файлов логов. Эти параметры помогают не накапливать лишние файлы и сохранять доступность диагностической истории.";
        }
        if (normalizedKey.includes("rollup")) {
            return "Управляет подготовкой данных для быстрых графиков и отчётов. Подготовленные данные ускоряют просмотр больших периодов и уменьшают нагрузку на исходные события.";
        }
        if (normalizedKey.includes("lifecycle")) {
            return "Управляет плановым обслуживанием аналитических данных: очисткой устаревших записей и поддержанием служебного состояния.";
        }
        if (normalizedKey.includes("retention")) {
            return "Определяет срок хранения данных. Увеличение срока расширяет историю анализа, но требует больше места в хранилище.";
        }
        if (normalizedKey.includes("partitioning")) {
            return "Управляет обслуживанием больших объёмов данных. Эти параметры нужны для стабильной работы хранилища при длительном накоплении аналитики.";
        }
        return cleanRuntimeText(fallback, "Настройка влияет на работу Analytics Module. Изменяйте значение только если понимаете ожидаемый эффект.");
    }

    function isHiddenRuntimeSetting(key) {
        return new Set([]).has(String(key || ""));
    }

    function isReservedRuntimeSetting(key) {
        return new Set([
            "analytics.raw.hot.days",
            "analytics.raw.warm.days",
            "analytics.tiering.enabled",
            "analytics.tiering.cold.provider",
            "analytics.tiering.cold.location",
            "analytics.partitioning.enabled",
            "analytics.partitioning.strategy",
            "analytics.partitioning.precreate-days",
            "analytics.partitioning.drop-after-days"
        ]).has(String(key || ""));
    }

    function runtimeSettingHelpText(key) {
        const help = {
            "analytics.logging.level": "Определяет, какие встроенные сообщения Analytics попадут в логи приложения. INFO показывает информационные сообщения, предупреждения и ошибки; WARN оставляет только предупреждения и ошибки; ERROR оставляет только ошибки; OFF полностью отключает встроенные сообщения Analytics.",
            "analytics.logging.controller.enabled": "Включает служебные сообщения о начале и завершении обработки контроллеров. Это помогает сопоставлять пользовательский запрос с трассировкой, но не влияет на запись событий, этапов и метрик.",
            "analytics.logging.service.enabled": "Включает служебные сообщения о работе сервисного слоя приложения. Это помогает видеть, где выполнялась бизнес-логика, но не управляет сохранением самих этапов аналитики.",
            "analytics.logging.database.enabled": "Включает служебные сообщения о вызовах к базе данных. Это влияет только на строки логов; этапы базы данных и их метрики продолжают сохраняться отдельно.",
            "analytics.logging.custom-layer.enabled": "Сохраняет в логах информацию о работе пользовательских слоёв приложения для последующего анализа производительности и поиска узких мест.",
            "analytics.logging.user-log-capture.enabled": "Разрешает показывать пользовательские сообщения разработчика в логах трассировки, если они были записаны внутри активного аналитического события.",
            "analytics.logging.strict-warnings.enabled": "Включает предупреждения строгой модели. Если код события, модуля, этапа, атрибута или метрики неизвестен либо отключён, Analytics запишет предупреждение в лог и создаст диагностическое служебное событие.",
            "analytics.lifecycle.enabled": "Включает плановое обслуживание аналитических данных. Оно помогает очищать устаревшие записи и поддерживать подготовленные данные в актуальном состоянии.",
            "analytics.lifecycle.interval-minutes": "Определяет, как часто запускать обслуживание аналитических данных. Слишком короткий интервал повышает нагрузку, слишком длинный откладывает очистку и обновление служебных данных.",
            "analytics.lifecycle.delete-batch-size": "Ограничивает количество записей, которые обслуживание удаляет за один проход. Меньшее значение снижает нагрузку на базу данных, большее быстрее освобождает место.",
            "analytics.raw.retention.days": "Определяет, сколько дней хранить исходные события и подробные данные. Чем больше срок, тем глубже история расследований и тем больше требуется места.",
            "analytics.aggregate.retention.days": "Определяет, сколько дней хранить подготовленные данные для графиков и отчётов. Если срок меньше детальных настроек агрегатов, старые точки на графиках будут удалены по этому общему сроку.",
            "analytics.tail-merge.enabled": "Позволяет графикам дополнять подготовленные данные свежими событиями, которые ещё не попали в фоновый пересчёт. Это делает последние минуты на графиках более актуальными.",
            "analytics.time-rollup.enabled": "Включает подготовку данных по времени для быстрых графиков количества, ошибок и длительности. Отключение замедлит просмотр длинных периодов.",
            "analytics.time-rollup.overlap-minutes": "Задаёт запас повторного пересчёта по времени, чтобы поздно записанные события корректно попали в графики.",
            "analytics.time-rollup.bootstrap-lookback-days": "Определяет, за сколько прошлых дней выполнить первичную подготовку данных при запуске или ручном пересчёте.",
            "analytics.time-rollup.refresh-interval-minutes": "Определяет частоту обновления подготовленных данных для графиков по времени.",
            "analytics.stage-metric-rollup.enabled": "Включает подготовку данных по метрикам этапов. Это ускоряет анализ производительности слоёв и операций на длинных периодах.",
            "analytics.stage-metric-rollup.overlap-minutes": "Задаёт запас повторного пересчёта метрик этапов, чтобы поздно записанные данные не выпадали из агрегатов.",
            "analytics.stage-metric-rollup.bootstrap-lookback-days": "Определяет, за сколько прошлых дней пересчитать метрики этапов при первичной подготовке данных.",
            "analytics.stage-metric-rollup.refresh-interval-minutes": "Определяет частоту обновления подготовленных данных по метрикам этапов.",
            "analytics.filter-rollup.enabled": "Включает подготовку значений для фильтров. Это ускоряет выбор событий, атрибутов и маршрутов на больших периодах.",
            "analytics.filter-rollup.long-range-days": "Определяет, с какого размера периода фильтры должны использовать подготовленные данные вместо просмотра исходных событий.",
            "analytics.filter-rollup.refresh-recent-days": "Определяет, сколько последних дней обновлять для быстрых фильтров при плановом пересчёте.",
            "analytics.filter-rollup.refresh-interval-minutes": "Определяет частоту обновления подготовленных данных для фильтров.",
            "analytics.log-index.enabled": "Включает или отключает быстрый поиск по логам. Если выключить, трассировки, ошибки и предупреждения будет сложнее находить в Analytics Admin.",
            "analytics.log-index.interval-minutes": "Определяет, как часто система проверяет папку логов на новые файлы и записи. Чем меньше значение, тем быстрее обновляется поиск, но тем чаще система обращается к диску.",
            "analytics.log-index.batch-size": "Задаёт, сколько лог-файлов можно обработать за один цикл. Меньшее значение снижает разовую нагрузку, но большие папки будут обрабатываться дольше.",
            "analytics.log-index.current-logs-enabled": "Разрешает читать активные лог-файлы, которые приложение ещё продолжает записывать. Если выключить, свежие трассировки появятся позже.",
            "analytics.log-index.archives-enabled": "Разрешает учитывать архивные лог-файлы при поиске по трассировкам, ошибкам и предупреждениям. Если выключить, новые архивы не будут попадать в быстрый поиск и диагностическую сводку.",
            "analytics.log-index.max-file-size-mb": "Файлы больше указанного размера будут пропущены. Это защищает систему от чрезмерной нагрузки при слишком больших логах.",
            "analytics.log-index.max-lines-per-trace": "Ограничивает, сколько строк трассировки сохраняется в быстром индексе для одного traceId. Полный текст остаётся в лог-файле, если файл ещё доступен для чтения.",
            "analytics.log-index.max-excerpts-per-trace": "Задаёт, сколько важных сообщений сохранить в диагностической сводке одной трассировки. Сообщения сверх лимита не попадают в эту сводку, но могут оставаться в исходном файле лога.",
            "analytics.log-index.excerpt-max-length": "Ограничивает длину одного сохранённого диагностического фрагмента. Это влияет только на краткую сводку, а не на исходный лог-файл.",
            "analytics.log-index.retention-days": "Определяет, через сколько дней устаревшие данные поиска по логам будут удаляться. Чем больше срок, тем дольше доступна история поиска.",
            "analytics.log-index.include-levels": "Определяет, какие уровни логирования считаются важными и попадают в диагностическую сводку. Поддерживаются значения: FATAL, ERROR, WARN, SLOW.",
            "analytics.log-index.archive-read-enabled": "Разрешает открывать содержимое архивных логов из Analytics Admin. Индексация помогает найти нужный архив, а эта настройка разрешает показать строки из него.",
            "analytics.log-index.archive-read-max-lines": "Ограничивает количество строк трассировки, которое можно показать из архивного файла за один запрос. Индекс при этом может знать о трассировке больше, чем отображается в интерфейсе.",
            "analytics.log-index.allowed-directory": "Задаёт папку, в которой система ищет текущие и архивные лог-файлы. Если путь указан неверно, поиск по логам не найдёт нужные файлы.",
            "analytics.log-retention.cleanup-enabled": "Разрешает автоматически удалять устаревшие лог-файлы. Если выключить, старые файлы нужно очищать вручную или внешними средствами.",
            "analytics.log-retention.current-days": "Определяет, сколько дней хранить обычные лог-файлы. После истечения срока они могут быть удалены правилами очистки.",
            "analytics.log-retention.archive-days": "Определяет, сколько дней хранить архивные файлы логов.",
            "analytics.log-retention.index-days": "Определяет, сколько дней хранить индекс логов и быстрые диагностические фрагменты.",
            "analytics.log-retention.interval-hours": "Определяет, как часто запускать очистку старых логов.",
            "analytics.log-retention.delete-batch-size": "Ограничивает количество лог-файлов, которое можно удалить за один запуск очистки. Меньшее значение снижает разовую нагрузку, но очистка больших каталогов займёт больше циклов.",
            "analytics.log-retention.archive-indexed-only": "Если включено, архивный файл удаляется только после того, как он был проиндексирован.",
            "analytics.log-retention.safe-mode-enabled": "В безопасном режиме очистка показывает состояние и расчёт, но не удаляет файлы физически."
        };
        return help[String(key || "")] || "";
    }

    function cleanRuntimeText(value, fallback) {
        const text = String(value || "").trim();
        const mojibakePattern = /\u0420[\u00A0-\u00BF\u0400-\u040F\u0450-\u045F]|\u0421[\u0400-\u040F\u0450-\u045F\u201A-\u201E]|\u00D0|\u00D1|\uFFFD/;
        if (!text || mojibakePattern.test(text)) {
            return String(fallback || "");
        }
        return text;
    }

    function initTooltips() {
        // Help is opened in a modal so it works inside the scrollable settings window.
    }

    function handleRuntimeHelpClick(event) {
        const button = event.target?.closest?.("[data-runtime-help], [data-runtime-action-help], [data-runtime-overview-help]");
        if (!button) {
            return;
        }
        event.preventDefault();
        event.stopPropagation();
        event.stopImmediatePropagation?.();
        // Runtime help uses compact hover tooltips inside the settings window.
        // A click should not open an additional modal over this modal.
        return;
        if (button.hasAttribute("data-runtime-overview-help")) {
            openRuntimeHelpModal(runtimeOverviewHelpPayload());
            return;
        }
        if (button.hasAttribute("data-runtime-action-help")) {
            openRuntimeHelpModal(actionHelpPayload(button.getAttribute("data-runtime-action-help") || ""));
            return;
        }
        openRuntimeHelpModal({
            title: button.getAttribute("data-runtime-help-title") || "Параметр аналитики",
            body: button.getAttribute("data-runtime-help-body") || "Описание отсутствует.",
            safeValue: button.getAttribute("data-runtime-help-default") || "\u041f\u043e \u0443\u043c\u043e\u043b\u0447\u0430\u043d\u0438\u044e \u2014",
            when: "Меняйте этот параметр, если диагностика показывает отставание, нехватку срока хранения или слишком тяжёлую фоновую обработку.",
            impact: "Изменение влияет только на обслуживание аналитических данных и не переименовывает внутренние ключи или значения в базе.",
            risk: "Слишком маленькие интервалы могут чаще нагружать диск или базу. Слишком короткие сроки хранения могут удалить старую историю раньше, чем вы ожидаете."
        });
    }

    function openRuntimeHelpModal(payload) {
        if (!refs.helpModalEl || !refs.helpModalTitle || !refs.helpModalBody) {
            return;
        }
        refs.helpModalTitle.textContent = payload.title || "Пояснение";
        refs.helpModalBody.innerHTML = `
            <section class="analytics-help-modal-section">
                <h6>Что это такое</h6>
                <p>${escapeHtml(payload.body || "Описание отсутствует.")}</p>
            </section>
            <section class="analytics-help-modal-section">
                <h6>Когда менять</h6>
                <p>${escapeHtml(payload.when || "Обычно менять не требуется.")}</p>
            </section>
            <section class="analytics-help-modal-section">
                <h6>Как влияет на приложение</h6>
                <p>${escapeHtml(payload.impact || "Влияет на фоновые задачи аналитики и отображение диагностических данных.")}</p>
            </section>
            <section class="analytics-help-modal-section">
                <h6>Безопасное значение</h6>
                <p>${escapeHtml(payload.safeValue || "Оставьте значение по умолчанию.")}</p>
            </section>
            <section class="analytics-help-modal-section mb-0">
                <h6>Что может пойти не так</h6>
                <p>${escapeHtml(payload.risk || "Слишком агрессивные настройки могут увеличить нагрузку или сократить доступную историю.")}</p>
            </section>
        `;
        refs.helpModalEl.classList.add("analytics-help-modal--nested");
        bootstrap.Modal.getOrCreateInstance(refs.helpModalEl).show();
        window.requestAnimationFrame(() => {
            const backdrops = Array.from(document.querySelectorAll(".modal-backdrop"));
            backdrops[backdrops.length - 1]?.classList.add("analytics-help-backdrop--nested");
        });
    }

    function runtimeOverviewHelpPayload() {
        return {
            title: "Конфигурация аналитики",
            body: "В этом окне настраивается, сколько времени хранить сырые события, подготовленные данные для графиков и служебную информацию аналитики.",
            when: "Обычно сюда заходят, когда графики отстают от новых событий, старая история занимает слишком много места или нужно продлить срок хранения аналитики.",
            impact: "Свежие данные используются для быстрых графиков, а старая история может храниться дольше в более компактном виде. Подготовленные агрегаты помогают открывать большие периоды без тяжёлого чтения всех сырых событий.",
            safeValue: "Хорошее начальное правило: сырые события хранить около 90 дней, подготовленные почасовые и дневные данные - 2-3 года.",
            risk: "Слишком короткое хранение удалит историю раньше ожидаемого. Слишком долгие сроки и частые пересчёты могут увеличить нагрузку на базу и диск."
        };
    }

    function actionHelpPayload(action) {
        const items = {
            refresh: {
                title: "Обновить агрегаты",
                body: "Пересчитывает подготовленные данные за ближайший период. Нажимайте, если графики отстают от последних событий или в диагностике видно задержку обновления.",
                when: "Когда свежие события уже есть, но графики ещё показывают старое состояние.",
                impact: "Запускает короткое обновление агрегатов. Обычно это безопасная операция.",
                safeValue: "Используйте при видимом отставании или после проверки диагностики.",
                risk: "Если нажимать слишком часто, можно создать лишнюю нагрузку на базу."
            },
            backfill: {
                title: "Догнать историю",
                body: "Запускает пересчёт старых данных. Это полезно после первого запуска аналитики или после изменения правил хранения.",
                when: "Когда в старых периодах графики пустые или агрегаты были созданы не для всей истории.",
                impact: "Может работать дольше обычного обновления, если данных много.",
                safeValue: "Запускайте вручную и дождитесь окончания операции.",
                risk: "На больших объёмах может заметно нагрузить базу."
            },
            lifecycle: {
                title: "Очистить и обслужить",
                body: "Применяет правила хранения: удаляет устаревшие сырые данные, обновляет служебные отметки и готовит таблицы к дальнейшей работе.",
                when: "После изменения сроков хранения или если диагностика показывает накопление старых данных.",
                impact: "Освобождает место и поддерживает служебные таблицы в актуальном состоянии.",
                safeValue: "Перед запуском проверьте сроки хранения.",
                risk: "Слишком короткие сроки хранения могут удалить историю, которая ещё нужна."
            },
            logs: {
                title: "Индексировать логи сейчас",
                body: "Ищет текущие и архивные файлы логов, строит быстрый индекс по трассировкам и событиям. После этого вкладка «Логи трассировки» сможет подсказать, в каком архиве находится нужный лог.",
                when: "Когда трассировка не найдена в текущем файле или после появления новых архивов.",
                impact: "Не сохраняет полный текст логов в базе, только указатель и короткую диагностическую сводку.",
                safeValue: "Оставьте стандартные лимиты и запускайте вручную при необходимости.",
                risk: "Если архивов очень много, индексация может занять время и нагрузить диск."
            },
            "log-cleanup": {
                title: "Очистить старые логи",
                body: "Применяет настройки хранения логов: находит устаревшие .log/.log.gz и старые строки индекса. В безопасном режиме показывает dry-run результат без физического удаления.",
                when: "После проверки сроков хранения или если диагностика показывает много кандидатов на очистку.",
                impact: "Освобождает место на диске и удаляет устаревшие записи индекса в пределах разрешённой директории логов.",
                safeValue: "Держите включённым безопасный режим, пока не проверите список кандидатов и сроки хранения.",
                risk: "Если выключить безопасный режим и поставить слишком короткие сроки, можно удалить архивы раньше, чем они понадобятся."
            }
        };
        return items[action] || {
            title: "Действие",
            body: "Запускает служебную операцию аналитики.",
            safeValue: "Используйте при необходимости."
        };
    }

    function formatOperationStep(step) {
        const text = String(step || "");
        return text
            .replaceAll("refresh_completed", "агрегаты пересчитаны")
            .replaceAll("backfill_started", "пересчёт истории запущен")
            .replaceAll("lifecycle_completed", "обслуживание завершено")
            .replaceAll("log_index_completed", "индекс логов обновлён")
            .replaceAll("deletedFiles=", "удалено файлов: ")
            .replaceAll("skippedActive=", "активных пропущено: ")
            .replaceAll("skippedNotIndexed=", "непроиндексированных пропущено: ")
            .replaceAll("deletedIndexRows=", "удалено строк индекса: ")
            .replaceAll("processed=", "обработано: ")
            .replaceAll("indexed=", "проиндексировано: ")
            .replaceAll("skipped=", "пропущено: ")
            .replaceAll("errors=", "ошибок: ");
    }

    function buildStatusText(payload, prefix = "") {
        const updatedAt = payload?.updatedAt ? new Date(payload.updatedAt) : null;
        if (!updatedAt || Number.isNaN(updatedAt.getTime())) {
            return prefix || "Готово.";
        }
        const pretty = updatedAt.toLocaleString();
        return `${prefix ? `${prefix} ` : ""}Последнее изменение: ${pretty}`;
    }

    function buildDiagnosticsStatusText(payload) {
        const generatedAt = payload?.generatedAt ? new Date(payload.generatedAt) : null;
        if (!generatedAt || Number.isNaN(generatedAt.getTime())) {
            return "Диагностика обновлена.";
        }
        return `Диагностика обновлена: ${generatedAt.toLocaleString()}`;
    }

    function attachRuntimeSettingTooltips() {
        refs.formRoot?.querySelectorAll(".analytics-runtime-help-badge[data-runtime-help='setting']").forEach((button) => {
            const body = button.getAttribute("data-runtime-help-body") || "Поясняет назначение параметра и влияние изменения.";
            setRuntimeTooltip(button, body);
        });
        const scope = runtimeHelpScope();
        const overview = scope?.querySelector("[data-runtime-overview-help]");
        if (overview) {
            setRuntimeTooltip(overview, "Здесь настраиваются сроки хранения, пересчёт агрегатов, обслуживание логов и фоновые задачи аналитики.");
        }
        scope?.querySelectorAll("[data-runtime-action-help]").forEach((button) => {
            const help = actionHelpPayload(button.getAttribute("data-runtime-action-help") || "");
            setRuntimeTooltip(button, help.body || "Запускает служебную операцию аналитики.");
        });
    }

    function attachRuntimeDiagnosticsTooltips() {
        const cardKeys = [
            "rawRowsEstimate",
            "rollupRowsEstimate",
            "maxLagMinutes",
            "minLagMinutes",
            "staleWatermarkCount",
            "currentFiles",
            "archiveFiles",
            "indexedFiles",
            "traceLinks",
            "pendingFiles",
            "cleanupCandidates"
        ];
        refs.diagnosticsContent?.querySelectorAll(".analytics-runtime-diag-card-label").forEach((label, index) => {
            appendRuntimeTooltip(label, RUNTIME_DIAG_HELP[cardKeys[index]]);
        });

        const blockKeys = ["logIndex", "watermarks", "eta", "tableSizes"];
        refs.diagnosticsContent?.querySelectorAll(".analytics-runtime-diag-block-title").forEach((title, index) => {
            appendRuntimeTooltip(title, RUNTIME_TABLE_HELP[blockKeys[index]]);
        });

    }

    function appendRuntimeTooltip(host, text) {
        if (!host || !text || host.querySelector?.(".analytics-runtime-help-badge")) {
            return;
        }
        host.classList.add("analytics-runtime-help-host");
        const button = document.createElement("button");
        button.type = "button";
        button.className = "analytics-runtime-help-badge analytics-runtime-tooltip-badge";
        button.textContent = "?";
        setRuntimeTooltip(button, text);
        host.append(" ", button);
    }

    function setRuntimeTooltip(button, text) {
        if (!button || !text) {
            return;
        }
        const normalized = String(text).replace(/\s+/g, " ").trim();
        button.setAttribute("data-runtime-tooltip", normalized);
        button.removeAttribute("title");
        button.setAttribute("aria-label", normalized);
    }

    function handleRuntimeTooltipEnter(event) {
        const button = event.target?.closest?.("[data-runtime-tooltip]");
        if (!button || !runtimeHelpScope()?.contains(button)) {
            return;
        }
        showRuntimeTooltip(button);
    }

    function handleRuntimeTooltipLeave(event) {
        const button = event.target?.closest?.("[data-runtime-tooltip]");
        if (!button || !runtimeHelpScope()?.contains(button)) {
            return;
        }
        const next = event.relatedTarget;
        if (next && button.contains(next)) {
            return;
        }
        hideRuntimeTooltip();
    }

    function showRuntimeTooltip(button) {
        const text = button.getAttribute("data-runtime-tooltip") || "";
        if (!text) {
            return;
        }
        if (!runtimeTooltipEl) {
            runtimeTooltipEl = document.createElement("div");
            runtimeTooltipEl.className = "analytics-runtime-floating-tooltip";
            document.body.appendChild(runtimeTooltipEl);
        }
        runtimeTooltipEl.textContent = text;
        runtimeTooltipEl.classList.add("is-visible");
        positionRuntimeTooltip(button);
    }

    function positionRuntimeTooltip(button) {
        if (!runtimeTooltipEl || !button) {
            return;
        }
        const rect = button.getBoundingClientRect();
        const tooltipRect = runtimeTooltipEl.getBoundingClientRect();
        const gap = 8;
        const viewportPadding = 10;
        let left = rect.left + rect.width / 2 - tooltipRect.width / 2;
        left = Math.max(viewportPadding, Math.min(left, window.innerWidth - tooltipRect.width - viewportPadding));
        let top = rect.top - tooltipRect.height - gap;
        if (top < viewportPadding) {
            top = rect.bottom + gap;
        }
        runtimeTooltipEl.style.left = `${left}px`;
        runtimeTooltipEl.style.top = `${top}px`;
    }

    function hideRuntimeTooltip() {
        runtimeTooltipEl?.classList.remove("is-visible");
    }

    function renderDiagCard(label, value) {
        return `
            <article class="analytics-runtime-diag-card">
                <div class="analytics-runtime-diag-card-label">${escapeHtml(label)}</div>
                <div class="analytics-runtime-diag-card-value">${escapeHtml(value)}</div>
            </article>
        `;
    }

    function renderEtaStatus(status) {
        const normalized = String(status || "").toUpperCase();
        if (normalized === "UP_TO_DATE" || normalized === "OK") {
            return `<span class='analytics-runtime-diag-status is-ok'>${normalized === "OK" ? "Готово" : "Актуально"}</span>`;
        }
        if (normalized === "DISABLED") {
            return "<span class='analytics-runtime-diag-status is-off'>Выключено</span>";
        }
        if (normalized === "PENDING_REFRESH") {
            return "<span class='analytics-runtime-diag-status is-warn'>Ожидает обновления</span>";
        }
        if (normalized === "ERROR" || normalized === "INDEX_ERROR") {
            return "<span class='analytics-runtime-diag-status is-warn'>Ошибка</span>";
        }
        return `<span class='analytics-runtime-diag-status is-warn'>${escapeHtml(normalized || "-")}</span>`;
    }

    function formatScope(value) {
        const normalized = String(value || "").toUpperCase();
        const labels = {
            EVENT: "События",
            STAGE: "Этапы",
            METRIC: "Метрики этапов",
            FILTER: "Фильтры"
        };
        return labels[normalized] || normalized || "—";
    }

    function formatEtaDetails(details, status) {
        const normalized = String(status || "").toUpperCase();
        if (normalized === "PENDING_REFRESH") {
            return "Система обновит эти данные при ближайшем плановом запуске.";
        }
        if (normalized === "UP_TO_DATE") {
            return "Данные считаются актуальными: отставание укладывается в выбранный интервал.";
        }
        if (normalized === "DISABLED") {
            return "Обновление для этого типа данных выключено.";
        }
        return details || "—";
    }

    function formatSettingOptionLabel(value, fallback) {
        const normalized = String(value || "").toUpperCase();
        const labels = {
            NONE: "Не используется",
            DAILY: "По дням",
            WEEKLY: "По неделям",
            MONTHLY: "По месяцам"
        };
        return labels[normalized] || fallback || value || "—";
    }

    function formatDateTime(value) {
        if (!value) {
            return "—";
        }
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return "—";
        }
        return date.toLocaleString();
    }

    function formatLag(value) {
        if (!Number.isFinite(Number(value))) {
            return "—";
        }
        const minutes = Math.max(0, Number(value));
        if (minutes < 60) {
            return `${Math.round(minutes)} мин`;
        }
        const hours = minutes / 60;
        if (hours < 24) {
            return `${hours.toFixed(1)} ч`;
        }
        return `${(hours / 24).toFixed(1)} д`;
    }

    function formatEta(value) {
        if (!Number.isFinite(Number(value))) {
            return "—";
        }
        const minutes = Math.max(0, Number(value));
        if (minutes < 60) {
            return `${Math.round(minutes)} мин`;
        }
        return `${(minutes / 60).toFixed(1)} ч`;
    }

    function formatGranularity(value) {
        const num = Number(value);
        if (!Number.isFinite(num) || num <= 0) {
            return "—";
        }
        if (num < 60) {
            return `${num} ${num === 1 ? "минута" : "минут"}`;
        }
        if (num % 1440 === 0) {
            const days = num / 1440;
            return `${days} ${days === 1 ? "день" : "дней"}`;
        }
        if (num % 60 === 0) {
            const hours = num / 60;
            return `${hours} ${hours === 1 ? "час" : "часов"}`;
        }
        return `${num} минут`;
    }

    function formatBytes(value) {
        const bytes = Number(value);
        if (!Number.isFinite(bytes) || bytes <= 0) {
            return "—";
        }
        const units = ["B", "KB", "MB", "GB", "TB"];
        let current = bytes;
        let unitIndex = 0;
        while (current >= 1024 && unitIndex < units.length - 1) {
            current /= 1024;
            unitIndex += 1;
        }
        return `${current.toFixed(current >= 10 ? 1 : 2)} ${units[unitIndex]}`;
    }

    function formatNullableInt(value) {
        if (!Number.isFinite(Number(value))) {
            return "—";
        }
        return Math.round(Number(value)).toLocaleString("ru-RU");
    }

    function startDiagnosticsPolling() {
        stopDiagnosticsPolling();
        diagnosticsTimer = window.setInterval(() => {
            if (!refs.pageRoot && !refs.modalEl?.classList.contains("show")) {
                return;
            }
            void reloadDiagnostics();
        }, DIAGNOSTICS_REFRESH_MS);
    }

    function stopDiagnosticsPolling() {
        if (!diagnosticsTimer) {
            return;
        }
        window.clearInterval(diagnosticsTimer);
        diagnosticsTimer = null;
    }

    async function fetchJson(url) {
        const response = await fetch(url, {
            method: "GET",
            headers: {
                "Accept": "application/json",
                "X-Requested-With": "XMLHttpRequest"
            },
            credentials: "same-origin"
        });
        if (!response.ok) {
            const message = await readErrorMessage(response);
            throw new Error(message);
        }
        return response.json();
    }

    async function postJson(url, payload) {
        const csrfToken = document.querySelector("meta[name='_csrf']")?.getAttribute("content") || "";
        const csrfHeader = document.querySelector("meta[name='_csrf_header']")?.getAttribute("content") || "";
        const headers = {
            "Accept": "application/json",
            "Content-Type": "application/json",
            "X-Requested-With": "XMLHttpRequest"
        };
        if (csrfToken && csrfHeader) {
            headers[csrfHeader] = csrfToken;
        }
        const response = await fetch(url, {
            method: "POST",
            headers,
            credentials: "same-origin",
            body: JSON.stringify(payload || {})
        });
        if (!response.ok) {
            const message = await readErrorMessage(response);
            throw new Error(message);
        }
        return response.json();
    }

    async function readErrorMessage(response) {
        const contentType = (response.headers.get("content-type") || "").toLowerCase();
        const text = await response.text();
        if (contentType.includes("application/json")) {
            try {
                const payload = JSON.parse(text);
                return payload?.message || payload?.error || `HTTP ${response.status}`;
            } catch (_ignored) {
                return `HTTP ${response.status}`;
            }
        }
        const clean = text.replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim();
        return clean || `HTTP ${response.status}`;
    }

    function setError(message) {
        if (!refs.error) {
            return;
        }
        const safe = String(message || "").trim();
        refs.error.classList.toggle("d-none", !safe);
        refs.error.textContent = safe;
    }

    function setStatus(message) {
        if (!refs.status) {
            return;
        }
        refs.status.textContent = String(message || "");
    }

    function setDiagnosticsStatus(message) {
        if (!refs.diagnosticsStatus) {
            return;
        }
        refs.diagnosticsStatus.textContent = String(message || "");
    }

    function setOperationStatus(message) {
        if (!refs.operationStatus) {
            return;
        }
        refs.operationStatus.textContent = String(message || "");
    }

    function renderDiagnosticsContent(html) {
        if (!refs.diagnosticsContent) {
            return;
        }
        refs.diagnosticsContent.innerHTML = String(html || "");
    }

    function toggleOperationButtons(enabled) {
        [
            refs.diagnosticsRefreshButton,
            refs.opRefreshButton,
            refs.opBackfillButton,
            refs.opLifecycleButton,
            refs.opLogIndexButton,
            refs.opLogCleanupButton
        ].forEach((button) => {
            if (!button) {
                return;
            }
            button.disabled = !enabled;
        });
    }

    function toggleSave(enabled) {
        if (!refs.saveButton) {
            return;
        }
        refs.saveButton.disabled = !enabled;
    }

    function setSaveButtonLabel(label) {
        if (!refs.saveButton) {
            return;
        }
        if (!refs.saveButton.dataset.defaultLabel) {
            refs.saveButton.dataset.defaultLabel = refs.saveButton.textContent.trim() || "Сохранить";
        }
        refs.saveButton.textContent = label || refs.saveButton.dataset.defaultLabel;
    }

    function showSettingsToast(message, isError = false) {
        const toast = ensureSettingsToast();
        toast.textContent = String(message || "");
        toast.classList.toggle("is-error", Boolean(isError));
        toast.classList.add("is-visible");
        window.clearTimeout(toast._analyticsSettingsTimer);
        toast._analyticsSettingsTimer = window.setTimeout(() => {
            toast.classList.remove("is-visible");
        }, 2600);
    }

    function ensureSettingsToast() {
        let toast = document.getElementById("analytics-api-toast");
        if (toast) {
            return toast;
        }
        toast = document.createElement("div");
        toast.id = "analytics-api-toast";
        toast.className = "analytics-api-toast";
        toast.setAttribute("role", "status");
        toast.setAttribute("aria-live", "polite");
        document.body.appendChild(toast);
        return toast;
    }

    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll("\"", "&quot;")
            .replaceAll("'", "&#39;");
    }
})();
