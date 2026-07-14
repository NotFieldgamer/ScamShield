"use client";

import * as React from "react";
import * as ProgressPrimitive from "@radix-ui/react-progress";
import { cn } from "@/lib/utils";

export function Progress({
  className,
  value,
  ...props
}: React.ComponentPropsWithoutRef<typeof ProgressPrimitive.Root>) {
  const pct = value ?? 0;
  return (
    <ProgressPrimitive.Root className={cn("ss-progress", className)} value={value} {...props}>
      <ProgressPrimitive.Indicator
        className="ss-progress-bar"
        style={{ transform: `translateX(-${100 - pct}%)` }}
      />
    </ProgressPrimitive.Root>
  );
}
