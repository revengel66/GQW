# Analytics Frontend Workflow

Analytics Admin frontend lives under `src/main/resources/META-INF/gqw-analytics/`.
Do not edit `bin/main` or `.gqw-build` copies by hand.

## Edit And Verify

1. Change source files in `src/main/resources/META-INF/gqw-analytics/`.
2. Bump the relevant cache-buster query in templates:
   - `admin-dashboard.html`: `analytics-dashboard.js`, `analytics-settings.js`, `app.css`
   - `dashboard.html`: `analytics-dashboard.js`
3. Compile resources/classes:

```powershell
.\gradlew.bat compileJava
```

4. Runtime files are served from the classpath copy under:

```text
%USERPROFILE%\.gqw-build\gqw\resources\main\META-INF\gqw-analytics\
```

5. In the browser, verify the actual response in DevTools:
   - Open Network.
   - Disable cache or hard refresh.
   - Select `/analytics/js/analytics-dashboard.js?v=...`.
   - Confirm the `v=` value and response contents match the source change.

## Runtime Source Of Truth

Spring serves analytics static files through `AnalyticsAdminWebConfig`:

```text
/analytics/js/**  -> classpath:/META-INF/gqw-analytics/static/js/
/analytics/css/** -> classpath:/META-INF/gqw-analytics/static/css/
/analytics/img/** -> classpath:/META-INF/gqw-analytics/static/img/
```

Thymeleaf templates are resolved from:

```text
classpath:/META-INF/gqw-analytics/templates/
```
