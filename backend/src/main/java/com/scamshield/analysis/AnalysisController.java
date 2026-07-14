package com.scamshield.analysis;

import com.scamshield.analysis.dto.AnalysisResponse;
import com.scamshield.analysis.dto.AnalyzeRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Analysis endpoints. Submitting is public (anonymous analysis is a product feature); an
 * authenticated submitter owns the analysis they create. Fetching by id is object-level
 * authorized in the service: an owned analysis is visible only to its owner.
 */
@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisController {

    private final AnalysisService service;

    public AnalysisController(AnalysisService service) {
        this.service = service;
    }

    @PostMapping
    public AnalysisResponse analyze(@AuthenticationPrincipal Long userId,
                                    @Valid @RequestBody AnalyzeRequest request, HttpServletRequest http) {
        String ip = clientIp(http);
        // An authenticated caller owns the analysis they create; an anonymous caller's posting stays
        // owner-less (a public, shareable permalink). Rate limiting remains keyed by IP.
        return service.analyze(userId, request.text(), request.kind(), ip, ip);
    }

    @GetMapping("/{id}")
    public AnalysisResponse get(@AuthenticationPrincipal Long userId, @PathVariable UUID id) {
        // Object-level authorization lives in the service: an owned analysis is returned only to its
        // owner; any other requester (including an anonymous one) gets 404, never the record.
        return service.getById(id, userId);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
