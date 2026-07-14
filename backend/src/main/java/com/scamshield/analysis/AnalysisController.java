package com.scamshield.analysis;

import com.scamshield.analysis.dto.AnalysisResponse;
import com.scamshield.analysis.dto.AnalyzeRequest;
import com.scamshield.analysis.dto.BulkAnalysisResponse;
import com.scamshield.common.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Analysis endpoints. Submitting is public (anonymous analysis is a product feature); an
 * authenticated submitter owns the analysis they create. Fetching by id is object-level
 * authorized in the service: an owned analysis is visible only to its owner.
 */
@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisController {

    // At most 100 postings per upload — bounds the pipeline work one bulk request can trigger.
    private static final int MAX_BULK_ROWS = 100;

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

    /**
     * Bulk scan: score a CSV of postings in one pass. Owner-only — the path sits outside every
     * permit-all matcher, so Spring Security requires an authenticated principal, and each scored row
     * is owned by that caller (it appears in their history). The first column holds the posting text.
     */
    @PostMapping(path = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BulkAnalysisResponse bulk(@AuthenticationPrincipal Long userId,
                                     @RequestParam(value = "file", required = false) MultipartFile file,
                                     HttpServletRequest http) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Attach a CSV file with a column of postings to scan.");
        }
        String csv;
        try {
            csv = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BadRequestException("Could not read that file. Upload a plain CSV.");
        }
        List<String> texts = CsvTextExtractor.firstColumn(csv, MAX_BULK_ROWS);
        if (texts.isEmpty()) {
            throw new BadRequestException(
                    "No postings found. Put the posting text in the first column, one per row.");
        }
        String ip = clientIp(http);
        // Keyed on the user (not IP): one rate-limit token for the whole batch, in its own bucket.
        return service.analyzeBulk(userId, texts, "bulk:" + userId, ip);
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
