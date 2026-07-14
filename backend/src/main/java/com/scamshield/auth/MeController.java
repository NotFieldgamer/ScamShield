package com.scamshield.auth;

import com.scamshield.auth.dto.MeResponse;
import com.scamshield.common.UnauthorizedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Returns the authenticated caller's own account. Requires a valid access token. */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final UserRepository users;

    public MeController(UserRepository users) {
        this.users = users;
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal Long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Account no longer exists."));
        return new MeResponse(user.getId(), user.getEmail(), user.getRole(), user.isEmailVerified());
    }
}
