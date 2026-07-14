package com.scamshield.support;

/**
 * A fixed, test-only JWT signing key. It lives under src/test so it never ships in the jar,
 * and it is deliberately obvious that it is not a production secret. Exposed as a compile-time
 * constant so it can be used in {@code @SpringBootTest(properties = ...)}.
 */
public final class TestSecrets {

    private TestSecrets() {
    }

    /** >= 32 bytes, as HS256 requires. */
    public static final String JWT = "test-signing-key-not-for-production-0123456789abcdef";

    public static final String JWT_PROP = "app.jwt.secret=" + JWT;
}
