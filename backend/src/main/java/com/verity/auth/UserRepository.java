package com.verity.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Resolves a verified Clerk session token's subject to the local account row. */
    Optional<User> findByClerkId(String clerkId);

    Optional<User> findByEmail(String email);
}
