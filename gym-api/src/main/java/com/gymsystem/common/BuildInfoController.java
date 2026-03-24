// src/main/java/com/gymsystem/common/BuildInfoController.java
package com.gymsystem.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * Lightweight "public ping" endpoint that also returns build/revision information when available.
 * You can expand this with git properties via spring-boot-maven-plugin if desired.
 */
@RestController
@RequestMapping("/public")
@Tag(name = "Public", description = "Public health-check and build info endpoints")
public class BuildInfoController {

    @Operation(summary = "Health-check ping — returns ok, timestamp and service name")
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "timestamp", Instant.now().toString(),
                "service", "gym-api"
        ));
    }
}
