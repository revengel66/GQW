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
        if (!refs.openButton || !refs.modalEl) {
            return;
        }
        modalInstance = new bootstrap.Modal(refs.modalEl);
        bindEvents();
    });

    function initRefs() {
        refs.openButton = document.getElementById("analytics-runtime-settings-open");
        refs.modalEl = document.getElementById("analytics-runtime-settings-modal");
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
        refs.modalEl?.addEventListener("click", handleRuntimeHelpClick, true);
        refs.modalEl?.addEventListener("mouseover", handleRuntimeTooltipEnter, true);
        refs.modalEl?.addEventListener("focusin", handleRuntimeTooltipEnter, true);
        refs.modalEl?.addEventListener("mouseout", handleRuntimeTooltipLeave, true);
        refs.modalEl?.addEventListener("focusout", handleRuntimeTooltipLeave, true);
        refs.modalEl?.addEventListener("scroll", hideRuntimeTooltip, true);
        refs.modalEl?.addEventListener("keydown", (event) => {
            if (event.key !== "Enter" && event.key !== " ") {
                return;
            }
            handleRuntimeHelpClick(event);
        }, true);
    }

    async function reloadSettings() {
        setError("");
        setStatus("Загрузка настроек...");
        setDiagnosticsStatus("Загрузка диагностики...");
        setOperationStatus("");
        renderDiagnosticsContent("<div class='text-muted small'>Загрузка...</div>");
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
    }

    async function saveSettings() {
        if (!loadedPayload) {
            return;
        }
        if (!validateSettingsForm()) {
            return;
        }
        const values = collectValues();
        setError("");
        setStatus("Сохраняем...");
        toggleSave(false);
        try {
            loadedPayload = await postJson(SETTINGS_API, {values});
            renderSettings(loadedPayload);
            setStatus(buildStatusText(loadedPayload, "Сохранено."));
            await reloadDiagnostics();
        } catch (error) {
            setError(error?.message || "Не удалось сохранить настройки.");
        } finally {
            toggleSave(true);
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
            <div class="table-responsive mb-2">
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
            <div class="table-responsive mb-2">
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
            <div class="table-responsive mb-2">
                <table class="table table-sm analytics-runtime-diag-table">
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
            <div class="table-responsive">
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
            .filter((setting) => !isReservedRuntimeSetting(setting?.key));
        const rows = settings.map((setting) => renderSetting(setting)).join("");
        return `
            <section class="analytics-runtime-group mb-3">
                <div class="analytics-runtime-group-head">
                    <div class="analytics-runtime-group-title">${title}</div>
                    <div class="analytics-runtime-group-sub">${description}</div>
                </div>
                <div class="analytics-runtime-grid">
                    ${rows}
                </div>
            </section>
        `;
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
            return `
                <input class="form-control form-control-sm"
                       type="number"
                       id="${id}"
                       value="${escapeHtml(value)}"
                       ${minAttr}
                       ${maxAttr}
                       step="1"
                       data-setting-key="${key}"
                       data-setting-kind="${kind}">
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
            "analytics.log-retention.delete-batch-size": "Размер удаления за один запуск",
            "analytics.log-retention.archive-indexed-only": "Удалять архив только после индексации",
            "analytics.log-retention.safe-mode-enabled": "Безопасная очистка"
        };
        return labels[key] || cleanRuntimeText(fallback, key || "Параметр");
    }

    function formatRuntimeSettingHelp(key, fallback) {
        if (isReservedRuntimeSetting(key)) {
            return "Пока не используется в текущей версии. Это зарезервированная настройка для будущего хранения или обслуживания данных; изменение значения сейчас не меняет backend-поведение.";
        }
        if (key.startsWith("analytics.log-index.")) {
            return "Управляет индексом текущих и архивных логов: частотой обхода, лимитами чтения, сроком хранения индекса и безопасным чтением архивов из UI.";
        }
        if (key.startsWith("analytics.log-retention.")) {
            return "Управляет очисткой файлов логов и строк индекса. Безопасный режим показывает dry-run/status без физического удаления файлов.";
        }
        if (key.includes("rollup.retention")) {
            return "Определяет, сколько дней хранить подготовленные rollup-данные для выбранной детализации. Больший срок даёт более длинную историю, но занимает больше места.";
        }
        if (key.includes("rollup")) {
            return "Управляет фоновым пересчётом подготовленных данных для быстрых графиков и длинных периодов.";
        }
        if (key.includes("lifecycle")) {
            return "Управляет плановым обслуживанием аналитических данных: очисткой устаревших записей и служебными отметками.";
        }
        return cleanRuntimeText(fallback, "Описание отсутствует.");
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
        const overview = refs.modalEl?.querySelector("[data-runtime-overview-help]");
        if (overview) {
            setRuntimeTooltip(overview, "Здесь настраиваются сроки хранения, пересчёт агрегатов, обслуживание логов и фоновые задачи аналитики.");
        }
        refs.modalEl?.querySelectorAll("[data-runtime-action-help]").forEach((button) => {
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
        if (!button || !refs.modalEl?.contains(button)) {
            return;
        }
        showRuntimeTooltip(button);
    }

    function handleRuntimeTooltipLeave(event) {
        const button = event.target?.closest?.("[data-runtime-tooltip]");
        if (!button || !refs.modalEl?.contains(button)) {
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
            if (!refs.modalEl?.classList.contains("show")) {
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

    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll("\"", "&quot;")
            .replaceAll("'", "&#39;");
    }
})();
