# Нагрузочное тестирование для модуля аналитики

Этот сценарий генерирует **реальный HTTP-трафик** в приложение и создаёт 3 фазы:

- `baseline`: обычная нагрузка
- `spike`: всплеск RPS (рост p95/p99)
- `error_wave`: волна ошибок (часть запросов на несуществующие товары)

Скрипт: `scripts/analytics-load.js` (k6).
Для фазы `spike` запросы также передают `X-Load-Phase: spike`, и контроллер
эмулирует “тяжёлый хвост” БД (рост `DB_QUERY_COUNT`, `RETRY_COUNT`, задержка).

## 1) Подготовка

1. Запусти приложение (`http://localhost:8080`).
2. Убедись, что аналитика открывается и пишутся логи:
   - `logs/gqw.log`
3. Установи k6 (если не установлен):
   - Windows (Chocolatey): `choco install k6`
   - или через официальный installer.

## 2) Запуск сценария

Из корня проекта:

```bash
k6 run scripts/analytics-load.js --env BASE_URL=http://localhost:8080 --out json=logs/load/k6-result.json
```

Опционально можно подкрутить интенсивность:

```bash
k6 run scripts/analytics-load.js \
  --env BASE_URL=http://localhost:8080 \
  --env BASELINE_RPS=12 \
  --env SPIKE_PEAK_RPS=160 \
  --env ERROR_RATIO=0.4 \
  --out json=logs/load/k6-result.json
```

## 3) Что смотреть в UI аналитики

Период: последние 15-30 минут.

1. KPI:
   - `count`, `avg`, `p95`, `p99`, `error_rate`
2. Графики:
   - В фазе `spike` должны вырасти `p95/p99`.
   - В фазе `error_wave` должен вырасти `error_rate`.
3. Этапы и метрики этапов:
   - В `spike` на этапе `DATABASE` должны вырасти:
     - `DB_QUERY_COUNT`
     - `RETRY_COUNT`
     - задержки по этапу (особенно p95/p99)
3. Raw события:
   - Должны появиться ошибки по `Path` вида `/product/not-found-*`.
   - Открой `Детали` и проверь `trace`.

## 4) Как искать причину по trace

В логах приложения:

```bat
findstr /I "trace:lt-" logs\gqw.log
```

Или конкретный trace:

```bat
findstr /I "trace:lt-error-7-1234-abc" logs\gqw.log
```

Trace из k6 передаётся в заголовке `X-Trace-Id`, и этот же идентификатор попадает:

- в analytics raw events,
- в логи (`[trace:...]`).

## 5) Учебный разбор аномалий (чек-лист)

1. Найди момент роста `p95/p99`.
2. Провались в `Этапы`.
3. Выбери проблемный этап.
4. Проверь `Raw события` за тот же интервал.
5. Открой 3-5 событий `Детали`.
6. Возьми `trace` и найди в `logs/gqw.log`.
7. Зафиксируй гипотезу: где узкое место и почему.
