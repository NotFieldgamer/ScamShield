package com.scamshield.modelregistry.dto;

import java.util.List;

/**
 * Everything the /model page renders, all derived in Java from the stored held-out predictions.
 * When {@code hasPredictions} is false the arrays are empty and the scalars are null — the page
 * shows an honest "predictions not loaded" state rather than any invented number.
 *
 * <p>The {@code grid} drives the threshold slider: at each threshold it carries the exact
 * confusion counts recomputed from {@code validation_predictions}, so moving the slider updates
 * precision, recall, "real jobs blocked" (false positives) and "scams let through" (false
 * negatives) from real data with no round-trip.
 */
public record ModelMetricsResponse(
        String modelName,
        String modelVersion,
        boolean hasPredictions,
        int total,
        int positives,
        int negatives,
        Double prAuc,
        Double rocAuc,
        Double brier,
        Double noSkillFloor,
        OperatingPoint operating,
        List<PrPoint> pr,
        List<RocPoint> roc,
        List<CalibrationBin> calibration,
        List<ThresholdPoint> grid) {

    /** The chosen operating point: highest recall at precision >= 0.90. Null if unreachable. */
    public record OperatingPoint(double threshold, double precision, double recall) {}

    public record PrPoint(double recall, double precision) {}

    public record RocPoint(double fpr, double tpr) {}

    public record CalibrationBin(double meanPredicted, double empirical, int count) {}

    public record ThresholdPoint(double threshold, int tp, int fp, int fn, int tn,
                                 double precision, double recall) {}
}
