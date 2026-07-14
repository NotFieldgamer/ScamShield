// A tiny leaf module shared by the landing hero and the analyzer, so importing the example text or
// the hand-off key never pulls in the whole Analyzer component.

/** sessionStorage key: the landing hero stashes pasted text here, /analyze reads and runs it once. */
export const ANALYZE_HANDOFF_KEY = "ss_analyze_handoff";

/** The canonical scam used by "Try an example" — a compact, unmistakable recruitment fraud. */
export const CANONICAL_SCAM =
  "URGENT hiring! Work from home and earn $5000 per week, no experience needed. We are recruiting " +
  "immediately for a data entry role. To activate your account, send your bank account details and " +
  "a $200 processing fee to start immediately. Guaranteed income. Contact us on WhatsApp today.";
