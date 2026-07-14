package com.scamshield.matching;

/** A weighted scam phrase from the {@code scam_phrases} registry. */
public record ScamPhrase(long id, String phrase, double weight, String category) {}
