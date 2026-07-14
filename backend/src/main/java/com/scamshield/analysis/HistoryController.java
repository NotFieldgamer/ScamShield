package com.scamshield.analysis;

import com.scamshield.analysis.dto.AnalysisSummary;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A caller's own analysis history. Owner-only: the route sits under {@code /api/v1/me/**}, which is
 * outside every permit-all matcher, so Spring Security requires an authenticated principal. The
 * query is scoped to that principal's id, so a caller can only ever list analyses they own.
 */
@RestController
@RequestMapping("/api/v1/me/analyses")
public class HistoryController {

    private final AnalysisService service;

    public HistoryController(AnalysisService service) {
        this.service = service;
    }

    @GetMapping
    public List<AnalysisSummary> myAnalyses(@AuthenticationPrincipal Long userId,
                                            @RequestParam(defaultValue = "100") int limit) {
        return service.history(userId, limit);
    }
}
