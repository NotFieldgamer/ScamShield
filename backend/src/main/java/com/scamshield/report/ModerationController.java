package com.scamshield.report;

import com.scamshield.report.dto.ReportSummary;
import com.scamshield.report.dto.ResolveRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Moderator-only queue and decisions. A moderator's decision is the authoritative label change and
 * the only one retraining trusts. Every resolution writes an audit row.
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
@PreAuthorize("hasRole('MODERATOR')")
public class ModerationController {

    private final ReportRepository reports;
    private final ReportService reportService;

    public ModerationController(ReportRepository reports, ReportService reportService) {
        this.reports = reports;
        this.reportService = reportService;
    }

    @GetMapping
    public List<ReportSummary> pending() {
        return reports.pending();
    }

    @PostMapping("/{id}/resolve")
    public void resolve(@AuthenticationPrincipal Long moderatorId,
                        @PathVariable long id,
                        @Valid @RequestBody ResolveRequest request,
                        HttpServletRequest http) {
        reportService.resolve(moderatorId, id, request.confirm(), clientIp(http));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
