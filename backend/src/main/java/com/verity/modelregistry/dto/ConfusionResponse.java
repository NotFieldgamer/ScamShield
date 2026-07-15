package com.verity.modelregistry.dto;

/**
 * The confusion matrix at one threshold, recomputed from the stored held-out predictions. The
 * two error counts are named in human terms per the brief: {@code realJobsBlocked} is the false
 * positives (legitimate jobs the model would flag), {@code scamsLetThrough} is the false
 * negatives (frauds it would miss).
 */
public record ConfusionResponse(
        double threshold,
        boolean hasPredictions,
        int truePositives,
        int falsePositives,
        int falseNegatives,
        int trueNegatives,
        int realJobsBlocked,
        int scamsLetThrough,
        double precision,
        double recall,
        int total,
        int positives,
        int negatives) {

    /** Honest empty result: no predictions loaded, so there is no confusion matrix to report. */
    public static ConfusionResponse empty(double threshold) {
        return new ConfusionResponse(threshold, false, 0, 0, 0, 0, 0, 0, 0.0, 0.0, 0, 0, 0);
    }
}
