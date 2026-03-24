package com.gymsystem.auth;

import com.gymsystem.auth.dto.AuthResponse;
import com.gymsystem.auth.dto.LoginRequest;
import com.gymsystem.auth.dto.SignupRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Signup and login flows")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a new user using an admin-issued invite token")
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        String token = authService.signup(request);
        return ResponseEntity.ok(new AuthResponse(token, "Bearer"));
    }

    @Operation(summary = "Login with email and password, returns a Bearer JWT")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request);
        return ResponseEntity.ok(new AuthResponse(token, "Bearer"));
    }
}
