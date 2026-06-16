CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE IF NOT EXISTS analytics.event_rollup_bucket (
    bucket_start timestamptz NOT NULL,
    granularity_minutes integer NOT NULL,
    module_code varchar(64) NOT NULL,
    event_type_code varchar(64) NOT NULL,
    sample_count bigint NOT NULL,
    error_count bigint NOT NULL,
    duration_sum bigint NOT NULL,
    avg_ms numeric(12, 3) NOT NULL,
    p95_ms numeric(12, 3) NOT NULL,
    p99_ms numeric(12, 3) NOT NULL,
    max_ms numeric(12, 3) NOT NULL,
    PRIMARY KEY (bucket_start, granularity_minutes, module_code, event_type_code)
);

CREATE INDEX IF NOT EXISTS idx_event_rollup_bucket_only
    ON analytics.event_rollup_bucket (granularity_minutes, bucket_start);
CREATE INDEX IF NOT EXISTS idx_event_rollup_scope_bucket
    ON analytics.event_rollup_bucket (
        granularity_minutes,
        module_code,
        event_type_code,
        bucket_start
    );
CREATE INDEX IF NOT EXISTS idx_event_rollup_bucket_scope_cover
    ON analytics.event_rollup_bucket (
        granularity_minutes,
        bucket_start,
        event_type_code,
        module_code
    )
    INCLUDE (
        sample_count,
        error_count,
        duration_sum,
        p95_ms,
        p99_ms,
        max_ms
    );

CREATE TABLE IF NOT EXISTS analytics.stage_rollup_bucket (
    bucket_start timestamptz NOT NULL,
    granularity_minutes integer NOT NULL,
    module_code varchar(64) NOT NULL,
    event_type_code varchar(64) NOT NULL,
    stage_type_code varchar(64) NOT NULL,
    sample_count bigint NOT NULL,
    error_count bigint NOT NULL,
    duration_sum bigint NOT NULL,
    avg_ms numeric(12, 3) NOT NULL,
    p95_ms numeric(12, 3) NOT NULL,
    p99_ms numeric(12, 3) NOT NULL,
    max_ms numeric(12, 3) NOT NULL,
    self_duration_sum bigint NOT NULL DEFAULT 0,
    self_avg_ms numeric(12, 3) NOT NULL DEFAULT 0,
    self_p95_ms numeric(12, 3) NOT NULL DEFAULT 0,
    self_p99_ms numeric(12, 3) NOT NULL DEFAULT 0,
    self_max_ms numeric(12, 3) NOT NULL DEFAULT 0,
    PRIMARY KEY (
        bucket_start,
        granularity_minutes,
        module_code,
        event_type_code,
        stage_type_code
    )
);

ALTER TABLE analytics.stage_rollup_bucket
    ADD COLUMN IF NOT EXISTS self_duration_sum bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS self_avg_ms numeric(12, 3) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS self_p95_ms numeric(12, 3) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS self_p99_ms numeric(12, 3) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS self_max_ms numeric(12, 3) NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_stage_rollup_bucket_only
    ON analytics.stage_rollup_bucket (granularity_minutes, bucket_start);
CREATE INDEX IF NOT EXISTS idx_stage_rollup_scope_bucket
    ON analytics.stage_rollup_bucket (
        granularity_minutes,
        module_code,
        event_type_code,
        stage_type_code,
        bucket_start
    );
CREATE INDEX IF NOT EXISTS idx_stage_rollup_bucket_scope_cover
    ON analytics.stage_rollup_bucket (
        granularity_minutes,
        bucket_start,
        event_type_code,
        stage_type_code,
        module_code
    )
    INCLUDE (
        sample_count,
        error_count,
        duration_sum,
        p95_ms,
        p99_ms,
        max_ms,
        self_duration_sum,
        self_p95_ms,
        self_p99_ms,
        self_max_ms
    );

CREATE INDEX IF NOT EXISTS idx_analytics_stage_started_at
    ON analytics.stage (started_at);
CREATE INDEX IF NOT EXISTS idx_analytics_stage_event_started_ended
    ON analytics.stage (event_id, started_at, ended_at);
CREATE INDEX IF NOT EXISTS idx_analytics_stage_started_type_event
    ON analytics.stage (started_at, stage_type_code, event_id);
CREATE INDEX IF NOT EXISTS idx_analytics_event_started_type_module
    ON analytics.event (started_at, event_type_code, module_code);

CREATE TABLE IF NOT EXISTS analytics.time_rollup_watermark (
    scope_code varchar(32) NOT NULL,
    granularity_minutes integer NOT NULL,
    watermark_at timestamptz,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_time_rollup_watermark_scope_granularity
    ON analytics.time_rollup_watermark (scope_code, granularity_minutes);

CREATE TABLE IF NOT EXISTS analytics.stage_metric_rollup_bucket (
    bucket_start timestamptz NOT NULL,
    granularity_minutes integer NOT NULL,
    module_code varchar(64) NOT NULL,
    event_type_code varchar(64) NOT NULL,
    stage_type_code varchar(64) NOT NULL,
    metric_type_code varchar(64) NOT NULL,
    unit varchar(32),
    sample_count bigint NOT NULL,
    numeric_count bigint NOT NULL,
    numeric_sum numeric(20, 3) NOT NULL,
    p95_value numeric(20, 3) NOT NULL,
    min_value numeric(20, 3),
    max_value numeric(20, 3),
    PRIMARY KEY (
        bucket_start,
        granularity_minutes,
        module_code,
        event_type_code,
        stage_type_code,
        metric_type_code
    )
);

CREATE INDEX IF NOT EXISTS idx_stage_metric_rollup_bucket_only
    ON analytics.stage_metric_rollup_bucket (granularity_minutes, bucket_start);
CREATE INDEX IF NOT EXISTS idx_stage_metric_rollup_scope_bucket
    ON analytics.stage_metric_rollup_bucket (
        granularity_minutes,
        module_code,
        event_type_code,
        stage_type_code,
        metric_type_code,
        bucket_start
    );
CREATE INDEX IF NOT EXISTS idx_stage_metric_rollup_bucket_scope_cover
    ON analytics.stage_metric_rollup_bucket (
        granularity_minutes,
        bucket_start,
        event_type_code,
        stage_type_code,
        metric_type_code,
        module_code
    )
    INCLUDE (
        unit,
        sample_count,
        numeric_count,
        numeric_sum,
        p95_value,
        min_value,
        max_value
    );

CREATE TABLE IF NOT EXISTS analytics.filter_event_type_day (
    day_start date NOT NULL,
    module_code varchar(64) NOT NULL,
    event_type_code varchar(64) NOT NULL,
    sample_count bigint NOT NULL,
    PRIMARY KEY (day_start, module_code, event_type_code)
);

CREATE INDEX IF NOT EXISTS idx_filter_event_type_day_scope
    ON analytics.filter_event_type_day (day_start, module_code, event_type_code);

CREATE TABLE IF NOT EXISTS analytics.filter_attr_value_day (
    day_start date NOT NULL,
    module_code varchar(64) NOT NULL,
    event_type_code varchar(64) NOT NULL,
    attribute_type_code varchar(64) NOT NULL,
    attribute_value varchar(255) NOT NULL,
    sample_count bigint NOT NULL,
    PRIMARY KEY (
        day_start,
        module_code,
        event_type_code,
        attribute_type_code,
        attribute_value
    )
);

CREATE INDEX IF NOT EXISTS idx_filter_attr_value_day_scope
    ON analytics.filter_attr_value_day (
        day_start,
        module_code,
        event_type_code,
        attribute_type_code
    );
