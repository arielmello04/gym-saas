// src/main/java/com/gymsystem/bootstrap/AdminBootstrap.java
package com.gymsystem.bootstrap;

import com.gymsystem.user.User;
import com.gymsystem.user.UserRepository;
import com.gymsystem.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements CommandLineRunner {

    @Value("${bootstrap.admin.email:}") // set via env or application.yml
    private String adminEmail;

    @Value("${bootstrap.admin.password:}") // set via env or application.yml
    private String adminPassword;

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder encoder;

    @Override
    public void run(String... args) {
        // Skip silently if not configured
        if (adminEmail == null || adminEmail.isBlank() ||
            adminPassword == null || adminPassword.isBlank()) {
            return;
        }

        // Create the admin only if it doesn't already exist
        userRepository.findByEmail(adminEmail).ifPresentOrElse(
            u -> log.info("Bootstrap admin already exists: {}", adminEmail),
            () -> {
                Instant now = Instant.now();
                User admin = User.builder()
                        .email(adminEmail)
                        .passwordHash(encoder.encode(adminPassword))
                        .role(UserRole.ADMIN_WEB) // make sure this role exists
                        .active(true)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
                userRepository.save(admin);
                log.info("Bootstrap admin created: {}", adminEmail);
            }
        );
    }
}
