"use client";

import * as React from "react";
import * as SwitchPrimitive from "@radix-ui/react-switch";
import { cn } from "@/lib/utils";

export function Switch({
  className,
  ...props
}: React.ComponentPropsWithoutRef<typeof SwitchPrimitive.Root>) {
  return (
    <SwitchPrimitive.Root className={cn("ss-switch", className)} {...props}>
      <SwitchPrimitive.Thumb className="ss-switch-thumb" />
    </SwitchPrimitive.Root>
  );
}
