export type ClassValue = string | false | null | undefined;

/** Minimal className joiner — our own, no clsx/tailwind-merge dependency. */
export function cn(...parts: ClassValue[]): string {
  return parts.filter(Boolean).join(" ");
}
