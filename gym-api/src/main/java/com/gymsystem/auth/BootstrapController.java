package com.gymsystem.auth;

import com.gymsystem.user.User;
import com.gymsystem.user.UserRepository;
import com.gymsystem.user.UserRole;
import com.gymsystem.security.jwt.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Signup and login flows")
public class BootstrapController {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Data
    public static class BootstrapRequest {
        @Email @NotBlank private String email;
        @NotBlank private String password;
        @NotBlank private String name;
    }

    /** Creates the first SUPER_ADMIN user. Returns 409 if any user already exists. */
    @Operation(summary = "Create the first SUPER_ADMIN user — returns 409 if the system is already initialized")
    @PostMapping("/bootstrap")
    public ResponseEntity<?> bootstrap(@Valid @RequestBody BootstrapRequest req) {
        if (userRepository.count() > 0) {
            return ResponseEntity.status(409)
                    .body(Map.of("error", "System already initialized"));
        }

        Instant now = Instant.now();
        User admin = User.builder()
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(UserRole.ADMIN_WEB)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
        userRepository.save(admin);

        String token = jwtService.generateToken(admin.getEmail(), admin.getRole().name());
        return ResponseEntity.ok(Map.of("token", token));
    }
}
