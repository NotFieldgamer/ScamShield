package com.verity.modelregistry;

import com.verity.modelregistry.dto.ConfusionResponse;
import com.verity.modelregistry.dto.ModelMetricsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, read-only model-performance surface (the /model transparency page). Both endpoints
 * derive every figure from the stored held-out predictions, so the threshold slider's four
 * numbers trace to real data.
 */
@RestController
@RequestMapping("/api/v1/models/active")
public class ModelMetricsController {

    private final ModelMetricsService metrics;

    public ModelMetricsController(ModelMetricsService metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/metrics")
    public ModelMetricsResponse metrics() {
        return metrics.metrics();
    }

    /** The confusion matrix recomputed at {@code threshold} (clamped to [0,1]) from stored predictions. */
    @GetMapping("/confusion")
    public ConfusionResponse confusion(@RequestParam(defaultValue = "0.5") double threshold) {
        double clamped = Math.max(0.0, Math.min(1.0, threshold));
        return metrics.confusionAt(clamped);
    }
}
