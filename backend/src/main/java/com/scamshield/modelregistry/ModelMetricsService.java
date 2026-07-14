package com.scamshield.modelregistry;

import com.scamshield.modelregistry.ModelMetricsRepository.ActiveModel;
import com.scamshield.modelregistry.ModelMetricsRepository.Predictions;
import com.scamshield.modelregistry.dto.ConfusionResponse;
import com.scamshield.modelregistry.dto.ModelMetricsResponse;
import com.scamshield.modelregistry.dto.ModelMetricsResponse.CalibrationBin;
import com.scamshield.modelregistry.dto.ModelMetricsResponse.OperatingPoint;
import com.scamshield.modelregistry.dto.ModelMetricsResponse.PrPoint;
import com.scamshield.modelregistry.dto.ModelMetricsResponse.RocPoint;
import com.scamshield.modelregistry.dto.ModelMetricsResponse.ThresholdPoint;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Computes the /model page's numbers from the stored held-out predictions. Nothing here is
 * hard-coded: PR/ROC curves, calibration bins, PR-AUC, the Brier score, the chosen operating
 * point, and the per-threshold confusion grid are all derived from {@code validation_predictions}
 * for the active model. If no predictions are loaded, {@link #metrics()} reports that honestly
 * instead of returning fabricated figures.
 */
@Service
public class ModelMetricsService {

    private static final double PRECISION_TARGET = 0.90; // the product is precision-first
    private static final int CALIBRATION_BINS = 10;
    private static final int GRID_STEPS = 100;           // slider resolution: thresholds 0.00..1.00
    private static final int MAX_CURVE_POINTS = 300;     // downsample curves for transport

    private final ModelMetricsRepository repo;

    public ModelMetricsService(ModelMetricsRepository repo) {
        this.repo = repo;
    }

    public ModelMetricsResponse metrics() {
        ActiveModel model = repo.activeModel();
        Predictions p = repo.predictionsFor(model.id());
        int n = p.size();
        if (n == 0) {
            return new ModelMetricsResponse(model.name(), model.version(), false,
                    0, 0, 0, null, null, null, null, null,
                    List.of(), List.of(), List.of(), List.of());
        }

        int[] yTrue = p.yTrue();
        double[] yScore = p.yScore();
        int positives = 0;
        for (int y : yTrue) {
            positives += y;
        }
        int negatives = n - positives;

        Sweep sweep = sweep(yTrue, yScore, positives, negatives);
        List<CalibrationBin> calibration = calibration(yTrue, yScore);
        double brier = brier(yTrue, yScore);
        List<ThresholdPoint> grid = grid(yTrue, yScore, positives, negatives);
        double noSkillFloor = (double) positives / n;

        return new ModelMetricsResponse(
                model.name(), model.version(), true,
                n, positives, negatives,
                sweep.prAuc, sweep.rocAuc, brier, noSkillFloor,
                sweep.operating,
                downsample(sweep.pr), downsample(sweep.roc), calibration, grid);
    }

    /** The confusion matrix recomputed at an arbitrary threshold from the stored predictions. */
    public ConfusionResponse confusionAt(double threshold) {
        ActiveModel model = repo.activeModel();
        Predictions p = repo.predictionsFor(model.id());
        int n = p.size();
        if (n == 0) {
            // No predictions loaded: report that honestly rather than a precision of 1.0 computed
            // from zero examples (brief: "if you cannot compute a number honestly, do not display it").
            return ConfusionResponse.empty(threshold);
        }
        int positives = 0;
        for (int y : p.yTrue()) {
            positives += y;
        }
        int negatives = n - positives;
        int[] c = confusionCounts(p.yTrue(), p.yScore(), threshold, positives, negatives);
        int tp = c[0];
        int fp = c[1];
        int fn = c[2];
        int tn = c[3];
        double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 1.0;
        double recall = positives > 0 ? (double) tp / positives : 0.0;
        return new ConfusionResponse(threshold, true, tp, fp, fn, tn, fp, fn,
                precision, recall, n, positives, negatives);
    }

    // predict positive iff score >= threshold. Returns [tp, fp, fn, tn].
    private static int[] confusionCounts(int[] yTrue, double[] yScore, double threshold,
                                         int positives, int negatives) {
        int tp = 0;
        int fp = 0;
        for (int i = 0; i < yTrue.length; i++) {
            if (yScore[i] >= threshold) {
                if (yTrue[i] == 1) {
                    tp++;
                } else {
                    fp++;
                }
            }
        }
        return new int[] {tp, fp, positives - tp, negatives - fp};
    }

    // One descending sweep over the scores → PR curve, ROC curve, their trapezoidal AUCs, and the
    // precision>=0.90 operating point. yScore is sorted descending by the repository.
    private static Sweep sweep(int[] yTrue, double[] yScore, int positives, int negatives) {
        List<PrPoint> pr = new ArrayList<>();
        List<RocPoint> roc = new ArrayList<>();
        pr.add(new PrPoint(0.0, 1.0));   // PR curve conventionally starts at recall 0, precision 1
        roc.add(new RocPoint(0.0, 0.0));

        double prAuc = 0;
        double rocAuc = 0;
        double prevRecall = 0;
        double prevPrecision = 1.0;
        double prevFpr = 0;
        double prevTpr = 0;

        OperatingPoint operating = null;
        double bestRecallAtTarget = -1;

        int tp = 0;
        int fp = 0;
        int i = 0;
        int n = yTrue.length;
        while (i < n) {
            double t = yScore[i];
            while (i < n && yScore[i] == t) { // include every example tied at this score
                if (yTrue[i] == 1) {
                    tp++;
                } else {
                    fp++;
                }
                i++;
            }
            double recall = positives > 0 ? (double) tp / positives : 0.0;
            double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 1.0;
            double fpr = negatives > 0 ? (double) fp / negatives : 0.0;
            double tpr = recall;

            prAuc += (recall - prevRecall) * (precision + prevPrecision) / 2.0;
            rocAuc += (fpr - prevFpr) * (tpr + prevTpr) / 2.0;

            pr.add(new PrPoint(recall, precision));
            roc.add(new RocPoint(fpr, tpr));

            if (precision >= PRECISION_TARGET && recall > bestRecallAtTarget) {
                bestRecallAtTarget = recall;
                operating = new OperatingPoint(t, precision, recall);
            }

            prevRecall = recall;
            prevPrecision = precision;
            prevFpr = fpr;
            prevTpr = tpr;
        }
        return new Sweep(pr, roc, prAuc, rocAuc, operating);
    }

    private static List<CalibrationBin> calibration(int[] yTrue, double[] yScore) {
        double[] sumPredicted = new double[CALIBRATION_BINS];
        int[] sumPositive = new int[CALIBRATION_BINS];
        int[] count = new int[CALIBRATION_BINS];
        for (int i = 0; i < yTrue.length; i++) {
            int b = (int) Math.floor(yScore[i] * CALIBRATION_BINS);
            if (b >= CALIBRATION_BINS) { // score == 1.0 lands in the top bin
                b = CALIBRATION_BINS - 1;
            }
            sumPredicted[b] += yScore[i];
            sumPositive[b] += yTrue[i];
            count[b]++;
        }
        List<CalibrationBin> bins = new ArrayList<>();
        for (int b = 0; b < CALIBRATION_BINS; b++) {
            if (count[b] == 0) {
                continue; // an empty bin has no honest point to plot
            }
            bins.add(new CalibrationBin(
                    sumPredicted[b] / count[b],
                    (double) sumPositive[b] / count[b],
                    count[b]));
        }
        return bins;
    }

    private static double brier(int[] yTrue, double[] yScore) {
        double sum = 0;
        for (int i = 0; i < yTrue.length; i++) {
            double d = yScore[i] - yTrue[i];
            sum += d * d;
        }
        return sum / yTrue.length;
    }

    private static List<ThresholdPoint> grid(int[] yTrue, double[] yScore,
                                             int positives, int negatives) {
        List<ThresholdPoint> grid = new ArrayList<>(GRID_STEPS + 1);
        for (int g = 0; g <= GRID_STEPS; g++) {
            double t = (double) g / GRID_STEPS;
            int[] c = confusionCounts(yTrue, yScore, t, positives, negatives);
            int tp = c[0];
            int fp = c[1];
            double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 1.0;
            double recall = positives > 0 ? (double) tp / positives : 0.0;
            grid.add(new ThresholdPoint(t, tp, fp, c[2], c[3], precision, recall));
        }
        return grid;
    }

    private static <T> List<T> downsample(List<T> points) {
        int n = points.size();
        if (n <= MAX_CURVE_POINTS) {
            return points;
        }
        List<T> out = new ArrayList<>(MAX_CURVE_POINTS);
        for (int k = 0; k < MAX_CURVE_POINTS; k++) {
            out.add(points.get((int) ((long) k * (n - 1) / (MAX_CURVE_POINTS - 1))));
        }
        return out;
    }

    private record Sweep(List<PrPoint> pr, List<RocPoint> roc,
                         double prAuc, double rocAuc, OperatingPoint operating) {}
}
