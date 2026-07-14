package com.scamshield.auth;

/** Application roles. Stored as VARCHAR (see the users.role CHECK constraint in V1). */
public enum Role {
    USER,
    MODERATOR,
    ADMIN
}
