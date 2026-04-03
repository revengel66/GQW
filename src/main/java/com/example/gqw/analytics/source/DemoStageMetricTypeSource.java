package com.example.gqw.analytics.source;

import com.example.gqw.analytics.entity.MetricValueKind;
import com.example.gqw.analytics.entity.StageMetricType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DemoStageMetricTypeSource implements StageMetricTypeSource {

    @Override
    public List<StageMetricType> stageMetricTypes() {
        StageMetricType dbQueryCount = new StageMetricType();
        dbQueryCount.setCode("DB_QUERY_COUNT");
        dbQueryCount.setName("SQL queries");
        dbQueryCount.setDescription("Количество SQL-запросов");
        dbQueryCount.setValueKind(MetricValueKind.NUMERIC);
        dbQueryCount.setUnitDefault("count");
        dbQueryCount.setIsActive(true);

        StageMetricType responseSize = new StageMetricType();
        responseSize.setCode("RESPONSE_SIZE_BYTES");
        responseSize.setName("Response size");
        responseSize.setDescription("Размер HTTP-ответа");
        responseSize.setValueKind(MetricValueKind.NUMERIC);
        responseSize.setUnitDefault("bytes");
        responseSize.setIsActive(true);

        StageMetricType retryCount = new StageMetricType();
        retryCount.setCode("RETRY_COUNT");
        retryCount.setName("Retry count");
        retryCount.setDescription("Количество повторных попыток");
        retryCount.setValueKind(MetricValueKind.NUMERIC);
        retryCount.setUnitDefault("count");
        retryCount.setIsActive(true);

        StageMetricType errorCode = new StageMetricType();
        errorCode.setCode("ERROR_CODE");
        errorCode.setName("Error code");
        errorCode.setDescription("Код ошибки этапа");
        errorCode.setValueKind(MetricValueKind.TEXT);
        errorCode.setUnitDefault(null);
        errorCode.setIsActive(true);

        return List.of(dbQueryCount, responseSize, retryCount, errorCode);
    }
}

