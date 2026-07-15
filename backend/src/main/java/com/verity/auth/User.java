package com.verity.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A local account row, one per Clerk user, provisioned on that user's first authenticated request.
 *
 * <p>Clerk owns identity — credentials, sessions, verification. This row exists because two product
 * guards need facts Clerk does not put on the request path: {@link #createdAt} backs the 7-day
 * account-age rule for reports, and {@link #role} backs ADMIN-only retraining. Authority is read
 * from here, never from a claim in a client-supplied token.
 *
 * <p>Maps the {@code users} table; the schema is owned by Flyway (see V6).
 */
@Entity
@Table(name = "users")
public class User {

    /** Local surrogate key. Every foreign key in the schema points here, not at {@link #clerkId}. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Clerk's user id ({@code user_...}); the alternate key we resolve a session token by. */
    @Column(name = "clerk_id", unique = true)
    private String clerkId;

    @Column(nullable = false, unique = true)
    private String email;

    /** Always null since V6 — Clerk stores credentials. Retained only because the column remains. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    // Set by the database default; never written by the application.
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected User() {
        // JPA
    }

    /**
     * Provisions the local row for a Clerk user. Always {@code USER}: a role is a decision this
     * application makes, so a new account can never arrive already privileged.
     */
    public User(String clerkId, String email) {
        this.clerkId = clerkId;
        this.email = email;
        this.role = Role.USER;
        // Clerk verifies the address before it issues a session token for it.
        this.emailVerified = true;
    }

    /**
     * Adopts a pre-Clerk account, linking it to the Clerk user with the same email address. The
     * row keeps its id, role, history and {@code created_at}, so foreign keys, admin rights and the
     * 7-day report guard all survive the move to Clerk. The dead password hash goes with it.
     */
    void linkClerk(String clerkId) {
        this.clerkId = clerkId;
        this.passwordHash = null;
        this.emailVerified = true;
    }

    public Long getId() {
        return id;
    }

    public String getClerkId() {
        return clerkId;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
