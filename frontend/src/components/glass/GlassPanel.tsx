import * as React from "react";
import { cn } from "@/lib/utils";

/** A larger glass surface for grouping content (e.g. a verdict panel). Same recipe as GlassCard
 *  with more generous padding. */
export function GlassPanel({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("glass", "ss-glass-panel", className)} {...props} />;
}
