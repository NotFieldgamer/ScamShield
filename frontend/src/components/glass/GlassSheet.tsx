import * as React from "react";
import { cn } from "@/lib/utils";

/** A full-height glass sheet for side rails and drawers. Presentational for now; the animated
 *  drawer behaviour is wired when the sheet UX lands. */
export function GlassSheet({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("glass", "ss-glass-sheet", className)} {...props} />;
}
