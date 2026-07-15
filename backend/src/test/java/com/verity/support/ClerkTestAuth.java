package com.verity.support;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Stands in for Clerk in integration tests.
 *
 * <p>Tests cannot mint real Clerk tokens — Clerk holds the signing key. So this generates a
 * throwaway RSA keypair, signs tokens with it, and overrides the application's {@link JwtDecoder}
 * with one trusting the matching public key. Everything downstream is the real thing: the same
 * RS256 verification, the same issuer check, the same
 * {@code ClerkJwtAuthenticationConverter} resolving the subject to a local user and reading the
 * role from the database. Only the key and the issuer are ours, and no network is involved.
 *
 * <p>Import it with {@code @Import(ClerkTestAuth.class)} and apply {@link #PROPERTIES}.
 */
@TestConfiguration
public class ClerkTestAuth {

    /** A test-only issuer. Deliberately not a resolvable URL: nothing here may reach the network. */
    public static final String ISSUER = "https://clerk.test.invalid";

    // Properties every test importing this config needs. Separate String constants rather than a
    // String[]: annotation values must be compile-time constants, and an array reference is not one.

    /** Satisfies the application's own decoder bean, which this config then overrides. */
    public static final String ISSUER_PROP = "app.clerk.issuer=" + ISSUER;

    /**
     * Satisfies {@link com.verity.auth.ClerkApi}'s constructor. It is never called: tests provision
     * their own users, so the directory always finds one and never asks Clerk for an email.
     */
    public static final String SECRET_KEY_PROP = "app.clerk.secret-key=sk_test_unused-no-network-in-tests";

    private static final RSAKey KEY = generateKey("verity-test-key");

    /** A second, untrusted key — an attacker's. Nothing ever configures the decoder to accept it. */
    private static final RSAKey FOREIGN_KEY = generateKey("not-our-key");

    private static RSAKey generateKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (Exception e) {
            throw new IllegalStateException("could not generate the test signing key", e);
        }
    }

    /**
     * Replaces the production decoder, which would otherwise try to fetch Clerk's JWKS. Marked
     * primary rather than removing the real bean, so the application's own wiring still has to be
     * correct for these tests to pass.
     */
    @Bean
    @Primary
    public JwtDecoder testJwtDecoder() throws Exception {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY.toPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder;
    }

    /** A valid session token for a Clerk user id, shaped like Clerk's own. */
    public static String tokenFor(String clerkUserId) {
        return sign(clerkUserId, Instant.now().plus(15, ChronoUnit.MINUTES), KEY);
    }

    /** An already-expired token, for proving the decoder rejects it. */
    public static String expiredTokenFor(String clerkUserId) {
        return sign(clerkUserId, Instant.now().minus(1, ChronoUnit.MINUTES), KEY);
    }

    /**
     * A well-formed, unexpired token signed by a key the decoder does not trust — what an attacker
     * minting their own "session" would produce.
     */
    public static String forgedTokenFor(String clerkUserId) {
        return sign(clerkUserId, Instant.now().plus(15, ChronoUnit.MINUTES), FOREIGN_KEY);
    }

    private static String sign(String subject, Instant expiry, RSAKey key) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer(ISSUER)
                    .issueTime(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)))
                    .expirationTime(Date.from(expiry))
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("could not sign a test token", e);
        }
    }
}
