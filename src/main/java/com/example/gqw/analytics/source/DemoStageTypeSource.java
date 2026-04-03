package com.example.gqw.analytics.source;

import com.example.gqw.analytics.entity.StageType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DemoStageTypeSource implements StageTypeSource {

    @Override
    public List<StageType> stageTypes() {
        StageType controller = new StageType();
        controller.setCode("CONTROLLER");
        controller.setName("Контроллер");
        controller.setDescription("Обработка HTTP-запроса");
        controller.setIsActive(true);

        StageType service = new StageType();
        service.setCode("SERVICE");
        service.setName("Сервис");
        service.setDescription("Бизнес-логика");
        service.setIsActive(true);

        StageType database = new StageType();
        database.setCode("DATABASE");
        database.setName("База данных");
        database.setDescription("Операции записи/чтения из БД");
        database.setIsActive(true);

        StageType response = new StageType();
        response.setCode("RESPONSE");
        response.setName("Ответ");
        response.setDescription("Подготовка и отправка ответа");
        response.setIsActive(true);

        return List.of(controller, service, database, response);
    }
}

