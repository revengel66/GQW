package com.example.gqw.analytics.source;

import com.example.gqw.analytics.entity.MetricValueKind;
import com.example.gqw.analytics.entity.StageMetricType;
import com.example.gqw.analytics.support.SystemMetricReadingGuides;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DemoStageMetricTypeSource implements StageMetricTypeSource {

    @Override
    public List<StageMetricType> stageMetricTypes() {
        StageMetricType dbQueryCount = new StageMetricType();
        dbQueryCount.setCode("DB_QUERY_COUNT");
        dbQueryCount.setName("SQL-запросы");
        dbQueryCount.setDescription("Количество SQL-запросов");
        dbQueryCount.setReadingGuide(SystemMetricReadingGuides.guideFor("DB_QUERY_COUNT"));
        dbQueryCount.setValueKind(MetricValueKind.NUMERIC);
        dbQueryCount.setUnitDefault("count");
        dbQueryCount.setIsActive(true);

        StageMetricType responseSize = new StageMetricType();
        responseSize.setCode("RESPONSE_SIZE_BYTES");
        responseSize.setName("Полный размер ответа (байт)");
        responseSize.setDescription("Размер HTTP-ответа");
        responseSize.setReadingGuide(SystemMetricReadingGuides.guideFor("RESPONSE_SIZE_BYTES"));
        responseSize.setValueKind(MetricValueKind.NUMERIC);
        responseSize.setUnitDefault("bytes");
        responseSize.setIsActive(true);

        StageMetricType retryCount = new StageMetricType();
        retryCount.setCode("RETRY_COUNT");
        retryCount.setName("Повторные попытки");
        retryCount.setDescription("Количество повторных попыток");
        retryCount.setReadingGuide(SystemMetricReadingGuides.guideFor("RETRY_COUNT"));
        retryCount.setValueKind(MetricValueKind.NUMERIC);
        retryCount.setUnitDefault("count");
        retryCount.setIsActive(true);

        StageMetricType errorCode = new StageMetricType();
        errorCode.setCode("ERROR_CODE");
        errorCode.setName("Код ошибки");
        errorCode.setDescription("Код ошибки этапа");
        errorCode.setReadingGuide(SystemMetricReadingGuides.guideFor("ERROR_CODE"));
        errorCode.setValueKind(MetricValueKind.TEXT);
        errorCode.setUnitDefault(null);
        errorCode.setIsActive(true);

        StageMetricType errorClass = new StageMetricType();
        errorClass.setCode("ERROR_CLASS");
        errorClass.setName("Класс ошибки");
        errorClass.setDescription("Класс ошибки: VALIDATION, BUSINESS, SYSTEM");
        errorClass.setReadingGuide(SystemMetricReadingGuides.guideFor("ERROR_CLASS"));
        errorClass.setValueKind(MetricValueKind.TEXT);
        errorClass.setUnitDefault(null);
        errorClass.setIsActive(true);

        StageMetricType itemCount = new StageMetricType();
        itemCount.setCode("ITEM_COUNT");
        itemCount.setName("Количество элементов");
        itemCount.setDescription("Количество элементов в операции");
        itemCount.setReadingGuide(SystemMetricReadingGuides.guideFor("ITEM_COUNT"));
        itemCount.setValueKind(MetricValueKind.NUMERIC);
        itemCount.setUnitDefault("count");
        itemCount.setIsActive(true);

        StageMetricType payloadSize = new StageMetricType();
        payloadSize.setCode("PAYLOAD_SIZE_BYTES");
        payloadSize.setName("Размер полезных данных ответа (байт)");
        payloadSize.setDescription("Размер входного payload");
        payloadSize.setReadingGuide(SystemMetricReadingGuides.guideFor("PAYLOAD_SIZE_BYTES"));
        payloadSize.setValueKind(MetricValueKind.NUMERIC);
        payloadSize.setUnitDefault("bytes");
        payloadSize.setIsActive(true);

        StageMetricType validationErrorCount = new StageMetricType();
        validationErrorCount.setCode("VALIDATION_ERROR_COUNT");
        validationErrorCount.setName("Ошибки валидации");
        validationErrorCount.setDescription("Количество ошибок валидации");
        validationErrorCount.setReadingGuide(SystemMetricReadingGuides.guideFor("VALIDATION_ERROR_COUNT"));
        validationErrorCount.setValueKind(MetricValueKind.NUMERIC);
        validationErrorCount.setUnitDefault("count");
        validationErrorCount.setIsActive(true);

        return List.of(dbQueryCount, responseSize, retryCount, errorCode, errorClass, itemCount, payloadSize, validationErrorCount);
    }
}

