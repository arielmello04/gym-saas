// src/main/java/com/gymsystem/common/RestExceptionHandler.java
package com.gymsystem.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;

@ControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,Object>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String,Object>> conflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(err(HttpStatus.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String,Object>> unauthorized(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(err(HttpStatus.UNAUTHORIZED, ex.getMessage()));
    }

    private Map<String,Object> err(HttpStatus s, String msg) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "status", s.value(),
                "error", s.getReasonPhrase(),
                "message", msg
        );
    }
}
