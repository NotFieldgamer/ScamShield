package com.scamshield.analysis;

import com.scamshield.analysis.dto.AnalysisResponse;
import com.scamshield.analysis.dto.AnalyzeRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public analysis endpoints (no auth in Phase 3). */
@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisController {

    private final AnalysisService service;

    public AnalysisController(AnalysisService service) {
        this.service = service;
    }

    @PostMapping
    public AnalysisResponse analyze(@Valid @RequestBody AnalyzeRequest request, HttpServletRequest http) {
        String ip = clientIp(http);
        // Anonymous callers are rate-limited by IP (the caller key). Auth arrives in Phase 4.
        return service.analyze(request.text(), request.kind(), ip, ip);
    }

    @GetMapping("/{id}")
    public AnalysisResponse get(@PathVariable UUID id) {
        return service.getById(id);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
