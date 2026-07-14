package com.scamshield.audit;

import com.scamshield.audit.dto.AuditEntry;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only view of the append-only audit log. */
@RestController
@RequestMapping("/api/v1/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditRepository audit;

    public AuditController(AuditRepository audit) {
        this.audit = audit;
    }

    @GetMapping
    public List<AuditEntry> recent(@RequestParam(defaultValue = "100") int limit) {
        int bounded = Math.min(Math.max(limit, 1), 500);
        return audit.recent(bounded);
    }
}
