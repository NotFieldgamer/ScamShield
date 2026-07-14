package com.scamshield.matching;

/**
 * Levenshtein (edit) distance: the minimum number of single-character insertions, deletions, or
 * substitutions to turn one string into another. Used to detect typosquatted domains
 * ({@code linkedln.com} vs {@code linkedin.com} = distance 1).
 *
 * <p>Classic dynamic program. {@code dp[i][j]} is the distance between the first {@code i} chars
 * of {@code a} and the first {@code j} of {@code b}; each cell is the cheapest of: delete from
 * {@code a}, insert into {@code a}, or match/substitute. Only the previous row is ever needed, so
 * two rolling rows give O(a·b) time and O(min(a,b)) space.
 */
public final class Levenshtein {

    private Levenshtein() {
    }

    public static int distance(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }

        int[] previous = new int[m + 1];
        int[] current = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            previous[j] = j; // distance from "" to b[0..j)
        }

        for (int i = 1; i <= n; i++) {
            current[0] = i; // distance from a[0..i) to ""
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int substitutionCost = (ca == b.charAt(j - 1)) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + substitutionCost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[m];
    }

    /** Early-exit variant: stops once the distance provably exceeds {@code maxDistance}. */
    public static int distanceWithin(String a, String b, int maxDistance) {
        if (Math.abs(a.length() - b.length()) > maxDistance) {
            return maxDistance + 1;
        }
        int n = a.length();
        int m = b.length();
        int[] previous = new int[m + 1];
        int[] current = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            current[0] = i;
            int rowMin = current[0];
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
                rowMin = Math.min(rowMin, current[j]);
            }
            if (rowMin > maxDistance) {
                return maxDistance + 1;
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[m];
    }
}
