import { cn } from "@/lib/utils";
import type { CSSProperties } from "react";

/**
 * A single skeleton block: the shape of content that hasn't arrived yet, not a spinner. A solid
 * inset fill with a UV highlight sweeping across it (motion only — the highlight is removed under
 * prefers-reduced-motion, leaving a static placeholder). Decorative, so aria-hidden; the wrapping
 * status region carries the "Loading" announcement.
 */
export function Skeleton({ className, style }: { className?: string; style?: CSSProperties }) {
  return <span className={cn("skeleton", className)} style={style} aria-hidden="true" />;
}
