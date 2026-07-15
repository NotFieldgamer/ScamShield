package com.verity.auth.dto;

import com.verity.auth.Role;

/** The authenticated caller's own account. */
public record MeResponse(Long id, String email, Role role, boolean emailVerified) {
}
