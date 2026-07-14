"use client";

import * as React from "react";
import * as SliderPrimitive from "@radix-ui/react-slider";
import { cn } from "@/lib/utils";

export function Slider({
  className,
  ...props
}: React.ComponentPropsWithoutRef<typeof SliderPrimitive.Root>) {
  const value = props.value ?? props.defaultValue ?? [0];
  const thumbCount = Array.isArray(value) ? value.length : 1;
  return (
    <SliderPrimitive.Root className={cn("ss-slider", className)} {...props}>
      <SliderPrimitive.Track className="ss-slider-track">
        <SliderPrimitive.Range className="ss-slider-range" />
      </SliderPrimitive.Track>
      {Array.from({ length: thumbCount }).map((_, i) => (
        <SliderPrimitive.Thumb key={i} className="ss-slider-thumb" aria-label="Value" />
      ))}
    </SliderPrimitive.Root>
  );
}
