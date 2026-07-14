"use client";

import * as React from "react";
import * as ToggleGroupPrimitive from "@radix-ui/react-toggle-group";
import { cn } from "@/lib/utils";

export function ToggleGroup({
  className,
  ...props
}: React.ComponentPropsWithoutRef<typeof ToggleGroupPrimitive.Root>) {
  return <ToggleGroupPrimitive.Root className={cn("ss-toggle-group", className)} {...props} />;
}

export function ToggleGroupItem({
  className,
  ...props
}: React.ComponentPropsWithoutRef<typeof ToggleGroupPrimitive.Item>) {
  return <ToggleGroupPrimitive.Item className={cn("ss-toggle", className)} {...props} />;
}
