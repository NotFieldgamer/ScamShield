package com.verity.auth;

import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Turns a Clerk session token that Spring Security has already verified into this application's
 * authentication.
 *
 * <p>Two decisions matter here:
 *
 * <ul>
 *   <li><b>The principal is the local {@code users.id}</b>, not Clerk's subject. Every controller
 *       already takes {@code @AuthenticationPrincipal Long userId} and every foreign key is that
 *       id, so the switch to Clerk stops at this class instead of rippling through the app.
 *   <li><b>Authorities come from our database, never from the token.</b> Clerk lets a token carry
 *       arbitrary custom claims; trusting a {@code role} claim would let anyone who can influence
 *       their own Clerk metadata grant themselves ADMIN. The role is read from {@code users.role}.
 * </ul>
 */
@Component
public class ClerkJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final ClerkUserDirectory directory;

    public ClerkJwtAuthenticationConverter(ClerkUserDirectory directory) {
        this.directory = directory;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        User user = directory.resolve(jwt.getSubject());
        List<SimpleGrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        // Credentials are the verified token itself; there is no password in this system.
        return new UsernamePasswordAuthenticationToken(user.getId(), jwt, authorities);
    }
}
