package com.scamshield.auth.dto;

import com.scamshield.auth.Role;

/** The authenticated caller's own account. */
public record MeResponse(Long id, String email, Role role, boolean emailVerified) {
}
