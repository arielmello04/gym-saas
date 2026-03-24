// src/main/java/com/gymsystem/security/userdetails/CustomUserDetailsService.java
package com.gymsystem.security.userdetails;

import com.gymsystem.user.User; // Imports our User entity
import com.gymsystem.user.UserRepository; // Imports our repository to load users
import lombok.RequiredArgsConstructor; // Lombok for constructor injection
import org.springframework.security.core.authority.SimpleGrantedAuthority; // Represents a simple role-based authority
import org.springframework.security.core.userdetails.UserDetails; // Spring Security UserDetails contract
import org.springframework.security.core.userdetails.UserDetailsService; // Interface for loading user details
import org.springframework.security.core.userdetails.UsernameNotFoundException; // Exception thrown when user is not found
import org.springframework.stereotype.Service; // Marks as service component

import java.util.Collections; // Utility to create singleton list

/**
 * Loads user data from the database and adapts it for Spring Security.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository; // Repository to access users

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException { // Loads user by email
        User user = userRepository.findByEmail(username) // Queries database by email
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username)); // Throws if missing
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(), // Sets username to email
                user.getPasswordHash(), // Sets password (already hashed)
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())) // Maps role to authority
        );
    }
}
