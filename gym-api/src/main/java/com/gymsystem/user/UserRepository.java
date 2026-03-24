// src/main/java/com/gymsystem/user/UserRepository.java
package com.gymsystem.user;

import org.springframework.data.jpa.repository.JpaRepository; // Imports JPA repository
import java.util.Optional; // Optional wrapper

/**
 * Repository for accessing User entities.
 */
public interface UserRepository extends JpaRepository<User, Long> { // Extends JpaRepository for CRUD
    Optional<User> findByEmail(String email); // Finder method to fetch user by email
}
