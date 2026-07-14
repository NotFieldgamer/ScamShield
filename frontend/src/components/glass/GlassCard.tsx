import * as React from "react";
import { cn } from "@/lib/utils";

/** The primary inspection surface: one glass layer, rounded, with the specular edge and noise
 *  from glass.css. `overflow: hidden` (via ss-glass-card) clips an inner UV sweep. */
export function GlassCard({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("glass", "ss-glass-card", className)} {...props} />;
}
