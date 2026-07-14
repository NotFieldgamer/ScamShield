export type ClassValue = string | false | null | undefined;

/** Minimal className joiner — our own, no clsx/tailwind-merge dependency. */
export function cn(...parts: ClassValue[]): string {
  return parts.filter(Boolean).join(" ");
}

/**
 * Constrain a post-auth redirect target (the `?next` param) to a same-origin internal path.
 * Rejects absolute URLs, protocol-relative "//host", backslash tricks "/\\host", and scheme URLs
 * (javascript:, data:) — anything that could send a freshly-authenticated user off-origin. This is
 * the open-redirect guard; only a single-leading-slash path is allowed through.
 */
export function safeInternalPath(raw: string | null | undefined, fallback = "/"): string {
  if (!raw) return fallback;
  if (raw.startsWith("/") && !raw.startsWith("//") && !raw.startsWith("/\\")) return raw;
  return fallback;
}
