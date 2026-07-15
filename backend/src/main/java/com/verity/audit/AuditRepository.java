package com.verity.audit;

import com.verity.audit.dto.AuditEntry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Append-only audit writer. The DB enforces append-only via a trigger; this only inserts and reads. */
@Repository
public class AuditRepository {

    // The ip arrives from a client-supplied X-Forwarded-For header, so it is untrusted input at a
    // system boundary. A value that is not an IP literal (e.g. "garbage") would make CAST(? AS inet)
    // throw, aborting the surrounding transaction and rolling back the very action being audited.
    // We therefore drop anything that is not a plain IPv4/IPv6 literal rather than trust it blindly.
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:]+$");

    private final JdbcTemplate jdbc;

    public AuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(Long actorId, String action, String targetType, String targetId, String ip) {
        String cleanIp = sanitizeIp(ip);
        jdbc.update(
                "INSERT INTO audit_log (actor_id, action, target_type, target_id, ip) "
                        + "VALUES (?, ?, ?, ?, CAST(? AS inet))",
                actorId, action, targetType, targetId, cleanIp);
    }

    /** Accept only a well-formed IP literal; anything else becomes null so it can never crash the insert. */
    static String sanitizeIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        String trimmed = ip.trim();
        if (IPV4.matcher(trimmed).matches() || (trimmed.indexOf(':') >= 0 && IPV6.matcher(trimmed).matches())) {
            return trimmed;
        }
        return null;
    }

    /** Most recent audit entries, newest first. Read side of the ADMIN audit endpoint. */
    public List<AuditEntry> recent(int limit) {
        return jdbc.query(
                "SELECT id, actor_id, action, target_type, target_id, "
                        + "host(ip) AS ip, created_at "
                        + "FROM audit_log ORDER BY created_at DESC, id DESC LIMIT ?",
                (rs, i) -> new AuditEntry(
                        rs.getLong("id"),
                        (Long) rs.getObject("actor_id"),
                        rs.getString("action"),
                        rs.getString("target_type"),
                        rs.getString("target_id"),
                        rs.getString("ip"),
                        // pgjdbc maps timestamptz to OffsetDateTime, not directly to Instant.
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()),
                limit);
    }
}
