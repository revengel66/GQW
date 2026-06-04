$ErrorActionPreference = 'Stop'
$root = (Get-Location).Path
$outFile = Join-Path $root 'analytics-dashboard-debug.md'

function Rel([string]$p) {
    $full = [System.IO.Path]::GetFullPath($p)
    if ($full.StartsWith($root, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $full.Substring($root.Length).TrimStart([char]92,[char]47)
    }
    return $full
}

$sb = New-Object System.Text.StringBuilder
function Add([string]$t='') { [void]$sb.AppendLine($t) }
function AddCodeBlock([string]$lang, [string]$content) {
    Add("```$lang")
    if ($null -ne $content) { [void]$sb.AppendLine($content.TrimEnd()) } else { Add('') }
    Add('```')
}

Add('# Analytics Dashboard Debug Package')
Add('')
Add("Generated at: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')")
Add('')

# ---------- 1. Structure ----------
Add('## 1) Analytics Project Structure')
Add('')

$structureTargets = @(
    'src/main/java/com/example/gqw/analytics',
    'src/main/resources/META-INF/gqw-analytics',
    'src/main/resources/META-INF/gqw-analytics/templates/analytics/admin-dashboard.html',
    'src/main/resources/META-INF/gqw-analytics/templates/analytics/dashboard.html',
    'src/main/resources/META-INF/gqw-analytics/static/js/analytics-dashboard.js',
    'src/main/resources/application.properties',
    'src/main/java/com/example/gqw/config/SecurityConfig.java',
    'src/main/java/com/example/gqw/analytics/config/AnalyticsAdminWebConfig.java',
    'src/main/java/com/example/gqw/analytics/config/AnalyticsAdminAuthInterceptor.java'
)

foreach ($target in $structureTargets) {
    if (Test-Path $target) {
        if ((Get-Item $target).PSIsContainer) {
            Add("### $(Rel $target)")
            $items = Get-ChildItem $target -Recurse | Sort-Object FullName
            $lines = @()
            foreach ($it in $items) {
                $rel = Rel $it.FullName
                $suffix = if ($it.PSIsContainer) { '/' } else { '' }
                $lines += $rel + $suffix
            }
            AddCodeBlock 'text' ($lines -join "`n")
            Add('')
        } else {
            Add("- $(Rel $target)")
        }
    }
}

$appConfigFiles = Get-ChildItem 'src/main/resources' -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -match '^application.*\.(properties|yml|yaml)$' } |
    Sort-Object Name
if ($appConfigFiles.Count -gt 0) {
    Add('')
    Add('### application*.properties/yml')
    foreach ($f in $appConfigFiles) { Add("- $(Rel $f.FullName)") }
}
Add('')

# ---------- 2. Full code ----------
Add('## 2) Full Source Code Dump')
Add('')

$fullFiles = New-Object System.Collections.Generic.List[string]

$explicit = @(
    'src/main/resources/META-INF/gqw-analytics/static/js/analytics-dashboard.js',
    'src/main/resources/META-INF/gqw-analytics/templates/analytics/admin-dashboard.html',
    'src/main/resources/META-INF/gqw-analytics/templates/analytics/dashboard.html',
    'src/main/java/com/example/gqw/config/SecurityConfig.java',
    'src/main/java/com/example/gqw/config/DataSourceConfig.java',
    'src/main/java/com/example/gqw/config/AnalyticsRequestPathSchemaPatchConfig.java',
    'src/main/resources/application.properties',
    'src/main/java/com/example/gqw/analytics/config/AnalyticsAdminWebConfig.java',
    'src/main/java/com/example/gqw/analytics/config/AnalyticsAdminAuthInterceptor.java',
    'src/main/java/com/example/gqw/analytics/config/AnalyticsRuntimeSettingsSchemaConfig.java'
)
foreach ($e in $explicit) { if (Test-Path $e) { [void]$fullFiles.Add((Resolve-Path $e).Path) } }

Get-ChildItem 'src/main/java/com/example/gqw/analytics/controller' -Filter 'Analytics*Controller.java' -File -ErrorAction SilentlyContinue | ForEach-Object { [void]$fullFiles.Add($_.FullName) }
Get-ChildItem 'src/main/java/com/example/gqw/analytics/service' -Filter 'Analytics*Service.java' -File -ErrorAction SilentlyContinue | ForEach-Object { [void]$fullFiles.Add($_.FullName) }
Get-ChildItem 'src/main/java/com/example/gqw/analytics/repository' -Filter 'Analytics*Repository.java' -File -ErrorAction SilentlyContinue | ForEach-Object { [void]$fullFiles.Add($_.FullName) }
Get-ChildItem 'src/main/java/com/example/gqw/analytics/entity' -Filter '*.java' -File -ErrorAction SilentlyContinue | ForEach-Object { [void]$fullFiles.Add($_.FullName) }
Get-ChildItem 'src/main/resources/db' -Recurse -Filter '*.sql' -File -ErrorAction SilentlyContinue | ForEach-Object { [void]$fullFiles.Add($_.FullName) }
Get-ChildItem 'src/main/resources' -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -match '^application.*\.(properties|yml|yaml)$' } | ForEach-Object { [void]$fullFiles.Add($_.FullName) }

$filesUnique = $fullFiles | Sort-Object -Unique

function LangFromPath([string]$p) {
    $ext = [System.IO.Path]::GetExtension($p).ToLowerInvariant()
    switch ($ext) {
        '.java' { 'java' }
        '.js' { 'javascript' }
        '.html' { 'html' }
        '.sql' { 'sql' }
        '.yml' { 'yaml' }
        '.yaml' { 'yaml' }
        '.properties' { 'properties' }
        default { 'text' }
    }
}

foreach ($f in $filesUnique) {
    if (-not (Test-Path $f)) { continue }
    $rel = Rel $f
    Add("### $rel")
    $content = Get-Content -Raw $f
    AddCodeBlock (LangFromPath $f) $content
    Add('')
}

# ---------- 3. Endpoint map ----------
Add('## 3) Analytics Dashboard Endpoints Map')
Add('')

$controllerFiles = Get-ChildItem 'src/main/java/com/example/gqw/analytics/controller' -Filter '*Controller.java' -File | Sort-Object Name
$endpointRows = New-Object System.Collections.Generic.List[object]

foreach ($cf in $controllerFiles) {
    $lines = Get-Content $cf.FullName
    $classMappings = @('')
    foreach ($line in $lines) {
        if ($line -match '@RequestMapping\((.+)\)') {
            $m = [regex]::Matches($line, '"([^"]+)"')
            if ($m.Count -gt 0) {
                $classMappings = @()
                foreach ($x in $m) { $classMappings += $x.Groups[1].Value }
            }
            break
        }
    }

    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($line -match '@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)(\((.+)\))?') {
            $verb = $matches[1].Replace('Mapping','').ToUpperInvariant()
            $annPart = $matches[3]
            $subPath = ''
            if ($annPart) {
                $mm = [regex]::Match($annPart, '"([^"]*)"')
                if ($mm.Success) { $subPath = $mm.Groups[1].Value }
            }
            $methodName = ''
            for ($j = $i + 1; $j -lt [Math]::Min($i + 10, $lines.Count); $j++) {
                if ($lines[$j] -match 'public\s+[\w\<\>\[\],\s\.\?]+\s+(\w+)\s*\(') {
                    $methodName = $matches[1]
                    break
                }
            }
            foreach ($base in $classMappings) {
                $url = ''
                if ([string]::IsNullOrWhiteSpace($base)) {
                    $url = if ($subPath) { $subPath } else { '/' }
                } else {
                    if ([string]::IsNullOrWhiteSpace($subPath)) { $url = $base }
                    elseif ($subPath.StartsWith('/')) { $url = $base.TrimEnd('/') + $subPath }
                    else { $url = $base.TrimEnd('/') + '/' + $subPath }
                }
                $endpointRows.Add([pscustomobject]@{
                    Method = $verb
                    Url = $url
                    Controller = "$($cf.BaseName).$methodName"
                }) | Out-Null
            }
        }
    }
}

$dashboardJs = Get-Content 'src/main/resources/META-INF/gqw-analytics/static/js/analytics-dashboard.js'
$settingsJsPath = 'src/main/resources/META-INF/gqw-analytics/static/js/analytics-settings.js'
$settingsJs = if (Test-Path $settingsJsPath) { Get-Content $settingsJsPath } else { @() }

function FindJsFunctions([string[]]$sourceLines, [string]$patternLiteral) {
    $found = New-Object System.Collections.Generic.HashSet[string]
    for ($i = 0; $i -lt $sourceLines.Count; $i++) {
        if ($sourceLines[$i] -like "*$patternLiteral*") {
            for ($j = $i; $j -ge [Math]::Max(0, $i - 80); $j--) {
                if ($sourceLines[$j] -match '^\s*(?:async\s+)?function\s+([A-Za-z0-9_]+)\s*\(') {
                    [void]$found.Add($matches[1]); break
                }
                if ($sourceLines[$j] -match '^\s*const\s+([A-Za-z0-9_]+)\s*=\s*(?:async\s*)?\(') {
                    [void]$found.Add($matches[1]); break
                }
            }
        }
    }
    return ($found | Sort-Object)
}

Add('| Method | URL | Controller method | Frontend function(s) |')
Add('|---|---|---|---|')
foreach ($row in ($endpointRows | Sort-Object Url, Method -Unique)) {
    $suffix = ''
    if ($row.Url -match '/analytics(?:-admin)?/api(.+)') { $suffix = $matches[1] }
    $frontendFns = @()
    if ($suffix) {
        $pat = 'api("' + $suffix + '")'
        $frontendFns += FindJsFunctions $dashboardJs $pat
        if ($settingsJs.Count -gt 0) { $frontendFns += FindJsFunctions $settingsJs $pat }
    }
    $frontendFns = $frontendFns | Sort-Object -Unique
    $frontendText = if ($frontendFns.Count -gt 0) { ($frontendFns -join ', ') } else { '-' }
    Add("| $($row.Method) | `$($row.Url)` | `$($row.Controller)` | `$frontendText` |")
}
Add('')

# ---------- 4. API response samples ----------
Add('## 4) Real API Response Samples')
Add('')

function Login-ShopAdmin {
    $sess = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $page = Invoke-WebRequest -Uri 'http://localhost:8080/login' -WebSession $sess -UseBasicParsing
    $html = $page.Content
    $token = [regex]::Match($html, 'name="_csrf"\s+value="([^"]+)"').Groups[1].Value
    if (-not $token) { $token = [regex]::Match($html, 'name="_csrf"\s+content="([^"]+)"').Groups[1].Value }
    $headerName = [regex]::Match($html, 'name="_csrf_header"\s+content="([^"]+)"').Groups[1].Value
    if (-not $headerName) { $headerName = 'X-CSRF-TOKEN' }
    $headers = @{}
    $headers[$headerName] = $token
    $body = @{ username = 'admin'; password = 'admin'; _csrf = $token }
    [void](Invoke-WebRequest -Uri 'http://localhost:8080/login' -Method Post -Body $body -Headers $headers -WebSession $sess -UseBasicParsing)
    return $sess
}

function FetchSample([Microsoft.PowerShell.Commands.WebRequestSession]$session, [string]$url) {
    try {
        $resp = Invoke-WebRequest -Uri $url -WebSession $session -UseBasicParsing
        return [pscustomobject]@{ Url = $url; Status = $resp.StatusCode; Body = $resp.Content }
    } catch {
        if ($_.Exception.Response) {
            $r = $_.Exception.Response
            $reader = New-Object System.IO.StreamReader($r.GetResponseStream())
            $body = $reader.ReadToEnd()
            return [pscustomobject]@{ Url = $url; Status = [int]$r.StatusCode; Body = $body }
        }
        return [pscustomobject]@{ Url = $url; Status = 'ERR'; Body = $_.Exception.Message }
    }
}

$session = $null
try { $session = Login-ShopAdmin } catch { }

$fromIso = '2026-05-31T00:00:00Z'
$toIso = '2026-06-01T23:59:59Z'
$bucket = '60'

$sampleUrls = @(
    "http://localhost:8080/analytics/api/overview?from=$fromIso&to=$toIso&bucketMinutes=$bucket",
    "http://localhost:8080/analytics/api/stages?from=$fromIso&to=$toIso&bucketMinutes=$bucket",
    "http://localhost:8080/analytics/api/stage-metrics?from=$fromIso&to=$toIso&bucketMinutes=$bucket",
    "http://localhost:8080/analytics/api/events?from=$fromIso&to=$toIso&page=0&size=5&sortBy=startedAt&sortDir=desc"
)

$sampleResults = @()
if ($session -ne $null) {
    foreach ($u in $sampleUrls) { $sampleResults += FetchSample -session $session -url $u }
} else {
    $sampleResults += [pscustomobject]@{ Url='(login failed)'; Status='ERR'; Body='Unable to authenticate as shop admin (admin/admin).' }
}

foreach ($sample in $sampleResults) {
    Add('### Request: ' + $sample.Url)
    $statusLine = '- HTTP status: **' + $sample.Status + '**'
    Add($statusLine)
    $bodyOut = $sample.Body
    if ($bodyOut.Length -gt 4000) { $bodyOut = $bodyOut.Substring(0, 4000) + [Environment]::NewLine + '... (truncated)' }
    AddCodeBlock 'json' $bodyOut
    Add('')
}

# Derived samples for requested chart names
$overviewSample = $sampleResults | Where-Object { $_.Url -like '*analytics/api/overview*' } | Select-Object -First 1
if ($overviewSample -and $overviewSample.Status -eq 200) {
    try {
        $obj = $overviewSample.Body | ConvertFrom-Json
        Add('### Derived from `/analytics/api/overview` (for requested charts)')
        Add('- events over time: `series[].count`')
        Add('- latency trend: `series[].avgMs/p95Ms/p99Ms`')
        Add('- error rate trend: `series[].errorRate`')
        Add('- KPI by event type: `eventBreakdown[]`')
        $eventsSeries = $obj.series | Select-Object -First 5 time,count
        $latencySeries = $obj.series | Select-Object -First 5 time,avgMs,p95Ms,p99Ms
        $errorSeries = $obj.series | Select-Object -First 5 time,errorRate
        $kpi = $obj.eventBreakdown | Select-Object -First 8 eventTypeCode,eventTypeName,count,errorRate,avgMs,p95Ms,p99Ms
        Add('#### events over time (sample)')
        AddCodeBlock 'json' (($eventsSeries | ConvertTo-Json -Depth 6))
        Add('#### latency trend (sample)')
        AddCodeBlock 'json' (($latencySeries | ConvertTo-Json -Depth 6))
        Add('#### error rate trend (sample)')
        AddCodeBlock 'json' (($errorSeries | ConvertTo-Json -Depth 6))
        Add('#### KPI by event type (sample)')
        AddCodeBlock 'json' (($kpi | ConvertTo-Json -Depth 6))
        Add('')
    } catch {
        Add('Failed to parse overview JSON for derived chart samples.')
        Add('')
    }
}

# ---------- 5. SQL diagnostics ----------
Add('## 5) SQL Diagnostics')
Add('')

$psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
$env:PGPASSWORD = 'postgres'

function RunSql([string]$title, [string]$sql) {
    Add("### $title")
    $out = & $psql -h localhost -U postgres -d gqw -c $sql 2>&1 | Out-String
    AddCodeBlock 'sql' ("-- SQL`n$sql")
    AddCodeBlock 'text' $out
    Add('')
}

RunSql 'Row counts by analytics tables' @"
select 'event' as table_name, count(*) as cnt from analytics.event
union all select 'stage', count(*) from analytics.stage
union all select 'stage_metric', count(*) from analytics.stage_metric
union all select 'event_rollup_bucket', count(*) from analytics.event_rollup_bucket
union all select 'stage_rollup_bucket', count(*) from analytics.stage_rollup_bucket
union all select 'stage_metric_rollup_bucket', count(*) from analytics.stage_metric_rollup_bucket
union all select 'filter_event_type_day', count(*) from analytics.filter_event_type_day
union all select 'filter_attr_value_day', count(*) from analytics.filter_attr_value_day
union all select 'aggregated_metric', count(*) from analytics.aggregated_metric
union all select 'aggregation_run', count(*) from analytics.aggregation_run
order by table_name;
"@

RunSql 'Min/Max timestamps for core analytics tables' @"
select
  (select min(created_at) from analytics.event) as event_created_min,
  (select max(created_at) from analytics.event) as event_created_max,
  (select min(started_at) from analytics.event) as event_started_min,
  (select max(started_at) from analytics.event) as event_started_max,
  (select min(started_at) from analytics.stage) as stage_started_min,
  (select max(started_at) from analytics.stage) as stage_started_max,
  (select min(bucket_start) from analytics.event_rollup_bucket) as event_rollup_min,
  (select max(bucket_start) from analytics.event_rollup_bucket) as event_rollup_max,
  (select min(bucket_start) from analytics.stage_rollup_bucket) as stage_rollup_min,
  (select max(bucket_start) from analytics.stage_rollup_bucket) as stage_rollup_max,
  (select min(bucket_start) from analytics.stage_metric_rollup_bucket) as stage_metric_rollup_min,
  (select max(bucket_start) from analytics.stage_metric_rollup_bucket) as stage_metric_rollup_max;
"@

RunSql 'Latest 5 events' @"
select id, event_uid, started_at, module_code, event_type_code, status_code, is_error, duration_ms
from analytics.event
order by started_at desc nulls last
limit 5;
"@

RunSql 'Latest 5 stages' @"
select id, event_id, stage_type_code, started_at, duration_ms, is_error, error_message
from analytics.stage
order by started_at desc nulls last
limit 5;
"@

RunSql 'Distinct event/status/error fields' @"
select 'event_type_code' as field, event_type_code::text as value, count(*) as cnt
from analytics.event
group by event_type_code
order by cnt desc, value
limit 50;

select 'status_code' as field, status_code::text as value, count(*) as cnt
from analytics.event
group by status_code
order by cnt desc, value;

select 'is_error' as field, is_error::text as value, count(*) as cnt
from analytics.event
group by is_error
order by value;

select 'stage_type_code' as field, stage_type_code::text as value, count(*) as cnt
from analytics.stage
group by stage_type_code
order by cnt desc, value;
"@

# ---------- 6. Browser/Network diagnostics ----------
Add('## 6) Browser Console and Network Diagnostics')
Add('')
Add('### Network requests captured from terminal (equivalent HTTP diagnostics)')
Add('| URL | HTTP status | Notes |')
Add('|---|---:|---|')
foreach ($sample in $sampleResults) {
    $note = if ($sample.Status -eq 200) { 'OK JSON' } elseif ($sample.Status -eq 302) { 'Redirect' } else { 'See body dump above' }
    Add("| `$($sample.Url)` | $($sample.Status) | $note |")
}
Add('')

Add('### Browser console errors')
Add('- Direct browser DevTools console is not accessible from terminal session.')
Add('- No JavaScript stack traces were available via server logs in this run.')
Add('- If needed, export DevTools HAR + console logs from browser and append here.')
Add('')

Add('### Recent server log lines (analytics-related)')
$logPath = 'logs/gqw.log'
if (Test-Path $logPath) {
    $logTail = Get-Content $logPath -Tail 120 | Select-String -Pattern 'analytics|/analytics|ERROR|WARN' -SimpleMatch
    if ($logTail) {
        AddCodeBlock 'text' (($logTail | ForEach-Object { $_.Line }) -join "`n")
    } else {
        Add('- No matching analytics lines in recent log tail.')
    }
} else {
    Add('- Log file not found at `logs/gqw.log`.')
}

[System.IO.File]::WriteAllText($outFile, $sb.ToString(), [System.Text.UTF8Encoding]::new($false))
"Generated: $outFile"
