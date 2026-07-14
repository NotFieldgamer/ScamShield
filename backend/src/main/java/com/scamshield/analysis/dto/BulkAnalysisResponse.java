package com.scamshield.analysis.dto;

import java.util.List;
import java.util.UUID;

/**
 * The result of a bulk scan: every row of the uploaded CSV run through the same pipeline as a single
 * paste. The counts are tallies of the per-row labels — no field is estimated. {@code id} is the
 * verdict permalink for that row, so a scanned posting can be opened in full.
 */
public record BulkAnalysisResponse(int total, int scam, int uncertain, int legit, List<Row> rows) {

    public record Row(int line, String snippet, UUID id, String label, double probability) {}
}
