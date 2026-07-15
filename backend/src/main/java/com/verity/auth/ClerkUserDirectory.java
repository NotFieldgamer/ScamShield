package com.verity.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maps a verified Clerk session token's subject onto this application's local account row,
 * provisioning it on first sight.
 *
 * <p>Called on every authenticated request, so the hot path is a single indexed lookup on
 * {@code users.clerk_id}. Only a user's very first request does more.
 */
@Service
public class ClerkUserDirectory {

    private final UserRepository users;
    private final ClerkApi clerk;

    public ClerkUserDirectory(UserRepository users, ClerkApi clerk) {
        this.users = users;
        this.clerk = clerk;
    }

    /** The local account for a Clerk user id, created if this is their first authenticated request. */
    @Transactional
    public User resolve(String clerkId) {
        return users.findByClerkId(clerkId).orElseGet(() -> provision(clerkId));
    }

    private User provision(String clerkId) {
        String email = clerk.primaryEmail(clerkId);

        // An account with this address may predate Clerk (it had a password). Adopt it rather than
        // insert a second row: it owns postings, reports and an audit trail, and its created_at is
        // what the 7-day report guard measures. A fresh row would silently reset that guard and
        // orphan the history.
        User existing = users.findByEmail(email).orElse(null);
        if (existing != null) {
            existing.linkClerk(clerkId);
            return users.save(existing);
        }

        try {
            return users.saveAndFlush(new User(clerkId, email));
        } catch (DataIntegrityViolationException race) {
            // Two first-requests can land together; the unique index on clerk_id settles it and the
            // loser reads the winner's row.
            return users.findByClerkId(clerkId).orElseThrow(() -> race);
        }
    }
}
