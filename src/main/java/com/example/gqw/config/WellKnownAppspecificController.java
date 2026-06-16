package com.example.gqw.config;

import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WellKnownAppspecificController {

    @GetMapping("/.well-known/appspecific/com.chrome.devtools.json")
    public ResponseEntity<Map<String, Object>> chromeDevtoolsWellKnown() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(Map.of());
    }
}
