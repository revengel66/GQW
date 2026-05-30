package com.example.gqw.analytics.source;

import com.example.gqw.analytics.entity.ModuleType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DemoModuleTypeSource implements ModuleTypeSource {

    @Override
    public List<ModuleType> moduleTypes() {
        return List.of(
            module("DEFAULT", "Общий", "Модуль по умолчанию для универсальных событий")
        );
    }

    private static ModuleType module(String code, String name, String description) {
        ModuleType moduleType = new ModuleType();
        moduleType.setCode(code);
        moduleType.setName(name);
        moduleType.setDescription(description);
        moduleType.setIsActive(true);
        return moduleType;
    }
}
