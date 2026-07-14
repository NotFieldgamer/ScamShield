"use client";

import * as React from "react";
import * as ScrollAreaPrimitive from "@radix-ui/react-scroll-area";
import { cn } from "@/lib/utils";

/** Note: the scroll viewport is a flat, opaque surface — glass is never applied to a scrolling
 *  list (backdrop-filter composites badly per item). */
export function ScrollArea({
  className,
  children,
  ...props
}: React.ComponentPropsWithoutRef<typeof ScrollAreaPrimitive.Root>) {
  return (
    <ScrollAreaPrimitive.Root className={cn("ss-scrollarea", className)} {...props}>
      <ScrollAreaPrimitive.Viewport className="ss-scrollarea-viewport">
        {children}
      </ScrollAreaPrimitive.Viewport>
      <ScrollAreaPrimitive.Scrollbar orientation="vertical" className="ss-scrollbar">
        <ScrollAreaPrimitive.Thumb className="ss-scrollbar-thumb" />
      </ScrollAreaPrimitive.Scrollbar>
      <ScrollAreaPrimitive.Scrollbar orientation="horizontal" className="ss-scrollbar">
        <ScrollAreaPrimitive.Thumb className="ss-scrollbar-thumb" />
      </ScrollAreaPrimitive.Scrollbar>
      <ScrollAreaPrimitive.Corner />
    </ScrollAreaPrimitive.Root>
  );
}
