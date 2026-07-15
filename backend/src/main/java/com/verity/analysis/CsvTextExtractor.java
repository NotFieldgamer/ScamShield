package com.verity.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Extracts posting text from an uploaded CSV: the first column of each record. A tolerant
 * RFC-4180-ish reader — it honours double-quoted fields (with embedded commas, newlines, and {@code ""}
 * escapes), so a posting that itself contains commas or line breaks survives intact. The first
 * non-blank cell is treated as a header and skipped when it is one of a few known column names;
 * blank rows are dropped; and the number of extracted rows is capped so one upload cannot fan out
 * into unbounded pipeline work.
 */
public final class CsvTextExtractor {

    private static final Set<String> HEADER_NAMES =
            Set.of("text", "description", "posting", "message", "job_description", "body", "content");

    private CsvTextExtractor() {
    }

    /** The first column of each data record, header and blank rows removed, at most {@code maxRows}. */
    public static List<String> firstColumn(String csv, int maxRows) {
        List<String> out = new ArrayList<>();
        boolean headerChecked = false;
        for (List<String> record : parse(csv)) {
            if (record.isEmpty()) {
                continue;
            }
            String cell = record.get(0);
            String trimmed = cell == null ? "" : cell.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!headerChecked) {
                headerChecked = true;
                if (HEADER_NAMES.contains(trimmed.toLowerCase())) {
                    continue; // a header row, not a posting
                }
            }
            out.add(cell);
            if (out.size() >= maxRows) {
                break;
            }
        }
        return out;
    }

    private static List<List<String>> parse(String csv) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean recordStarted = false;
        int n = csv.length();
        for (int i = 0; i < n; i++) {
            char c = csv.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && csv.charAt(i + 1) == '"') {
                        field.append('"'); // escaped quote
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
                recordStarted = true;
            } else {
                switch (c) {
                    case '"' -> {
                        inQuotes = true;
                        recordStarted = true;
                    }
                    case ',' -> {
                        record.add(field.toString());
                        field.setLength(0);
                        recordStarted = true;
                    }
                    case '\r' -> {
                        // ignore; the record ends on the following \n (or at end of input)
                    }
                    case '\n' -> {
                        record.add(field.toString());
                        field.setLength(0);
                        records.add(record);
                        record = new ArrayList<>();
                        recordStarted = false;
                    }
                    default -> {
                        field.append(c);
                        recordStarted = true;
                    }
                }
            }
        }
        if (recordStarted || field.length() > 0 || !record.isEmpty()) {
            record.add(field.toString());
            records.add(record);
        }
        return records;
    }
}
