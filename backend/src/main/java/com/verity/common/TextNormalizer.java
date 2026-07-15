package com.verity.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Normalizes posting text for deduplication and computes its content hash. */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    /** Trim and collapse internal whitespace, so trivially-different pastes dedupe to one verdict. */
    public static String normalize(String raw) {
        return raw.strip().replaceAll("\\s+", " ");
    }

    /** SHA-256 hex of the normalized text (64 chars, matches postings.content_hash CHAR(64)). */
    public static String contentHash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalize(raw).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
