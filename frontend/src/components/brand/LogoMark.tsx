type LogoMarkProps = {
  size?: number;
  className?: string;
  /** When set, the mark is a standalone image with this accessible name. Omit inside the wordmark. */
  title?: string;
};

/**
 * Scam Shield mark — a shield holding a magnifier lens: protection plus inspection. Geometric,
 * single-weight strokes, monochrome; it inherits color via `currentColor` (the wordmark sets that
 * to the accent). Decorative by default (aria-hidden), because the wordmark carries the name.
 */
export function LogoMark({ size = 32, className, title }: LogoMarkProps) {
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 32 32"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.9}
      strokeLinecap="round"
      strokeLinejoin="round"
      role={title ? "img" : undefined}
      aria-hidden={title ? undefined : true}
      aria-label={title}
    >
      {title ? <title>{title}</title> : null}
      {/* shield */}
      <path d="M16 3.4 26.4 7.2 V15.2 C26.4 21.8 22 26.4 16 28.6 C10 26.4 5.6 21.8 5.6 15.2 V7.2 Z" />
      {/* magnifier lens + handle, inspecting inside the shield */}
      <circle cx="14" cy="13.4" r="4.4" />
      <line x1="17.3" y1="16.7" x2="21.2" y2="20.6" />
    </svg>
  );
}
