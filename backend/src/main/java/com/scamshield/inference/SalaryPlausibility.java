package com.scamshield.inference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coarse salary-plausibility flag. It extracts the largest promised pay figure from the pasted
 * text and z-scores it against the offline salary model's expectation (log space, using the
 * persisted residual σ): {@code z = (log1p(amount) − expected) / σ}, flagged when {@code z > 3}.
 *
 * <p>Honest limits: the model was trained on EMSCAD {@code salary_range} — populated in ~16% of
 * rows, no currency, no pay period (raw numbers mixing hourly and annual). At serve time we have
 * no role/experience features, so the expectation collapses to the population mean (the model
 * intercept). This is a weak, conservative signal that contributes to the flag list — never a
 * standalone verdict. The raw z-score is returned so the number is visible, not hidden.
 */
public class SalaryPlausibility {

    // Money: optional currency symbol/code, digits with separators, optional k/m multiplier.
    private static final Pattern MONEY = Pattern.compile(
            "(?:\\$|₹|€|£|usd|inr|rs\\.?)\\s?([0-9][0-9,]*(?:\\.[0-9]+)?)\\s?([km])?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PERIOD = Pattern.compile(
            "\\b(hour|hr|day|daily|week|weekly|month|monthly|year|yearly|annually|annum|pa)\\b",
            Pattern.CASE_INSENSITIVE);

    private final double expectedLogPay; // model intercept (population baseline at serve time)
    private final double residualStd;
    private final double zThreshold;

    private SalaryPlausibility(double expectedLogPay, double residualStd, double zThreshold) {
        this.expectedLogPay = expectedLogPay;
        this.residualStd = residualStd;
        this.zThreshold = zThreshold;
    }

    public static SalaryPlausibility fromClasspath(String resource) throws IOException {
        try (InputStream in = SalaryPlausibility.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("salary params not found on classpath: " + resource);
            }
            JsonNode root = new ObjectMapper().readTree(in);
            double intercept = root.get("ridge_intercept").asDouble();
            double sigma = root.get("residual_std_log").asDouble();
            double z = root.has("z_flag_threshold") ? root.get("z_flag_threshold").asDouble() : 3.0;
            return new SalaryPlausibility(intercept, sigma, z);
        }
    }

    public Optional<SalaryFlag> assess(String text) {
        double amount = largestAmount(text);
        if (amount <= 0.0) {
            return Optional.empty();
        }
        String period = detectPeriod(text);
        double z = (Math.log1p(amount) - expectedLogPay) / residualStd;
        return Optional.of(new SalaryFlag(amount, period, z, z > zThreshold));
    }

    private static double largestAmount(String text) {
        double max = 0.0;
        Matcher m = MONEY.matcher(text);
        while (m.find()) {
            double value = Double.parseDouble(m.group(1).replace(",", ""));
            String mult = m.group(2);
            if (mult != null) {
                value *= mult.equalsIgnoreCase("m") ? 1_000_000 : 1_000;
            }
            max = Math.max(max, value);
        }
        return max;
    }

    private static String detectPeriod(String text) {
        Matcher m = PERIOD.matcher(text);
        return m.find() ? m.group(1).toLowerCase(Locale.ROOT) : null;
    }

    /** amount = extracted figure; period = per-unit if stated; z = standard deviations above expected. */
    public record SalaryFlag(double amount, String period, double zScore, boolean implausible) {}
}
