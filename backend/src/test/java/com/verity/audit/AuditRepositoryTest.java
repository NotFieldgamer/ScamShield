package com.verity.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The audit IP is client-supplied (X-Forwarded-For), so a non-IP value must never reach the
 * {@code CAST(? AS inet)} insert — otherwise it would abort the transaction and roll back the very
 * action being audited. Sanitizing to null is the safe degrade.
 */
class AuditRepositoryTest {

    @Test
    void acceptsWellFormedIpLiterals() {
        assertThat(AuditRepository.sanitizeIp("203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(AuditRepository.sanitizeIp("2001:db8::1")).isEqualTo("2001:db8::1");
        assertThat(AuditRepository.sanitizeIp("  198.51.100.2  ")).isEqualTo("198.51.100.2");
    }

    @Test
    void dropsAnythingThatIsNotAnIpLiteral() {
        // A forged / malformed X-Forwarded-For value that would otherwise crash CAST(? AS inet).
        assertThat(AuditRepository.sanitizeIp("garbage")).isNull();
        assertThat(AuditRepository.sanitizeIp("1.2.3")).isNull();
        assertThat(AuditRepository.sanitizeIp("'; DROP TABLE audit_log; --")).isNull();
        assertThat(AuditRepository.sanitizeIp("")).isNull();
        assertThat(AuditRepository.sanitizeIp("   ")).isNull();
        assertThat(AuditRepository.sanitizeIp(null)).isNull();
    }
}
