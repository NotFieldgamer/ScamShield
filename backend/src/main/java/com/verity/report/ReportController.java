package com.verity.report;

import com.verity.report.dto.ReportRequest;
import com.verity.report.dto.ReportSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * File a report against a verdict. Requires authentication; the account-age guard (≥ 7 days) is
 * enforced in {@link ReportService}, since it depends on the account, not the role.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportSummary submit(@AuthenticationPrincipal Long userId,
                                @Valid @RequestBody ReportRequest request,
                                HttpServletRequest http) {
        return reports.submit(userId, request.postingId(), request.claim(), clientIp(http));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
