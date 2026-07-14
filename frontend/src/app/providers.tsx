"use client";

import { type ReactNode } from "react";
import { ThemeProvider } from "@/lib/theme-provider";
import { TooltipProvider } from "@/components/primitives/Tooltip";
import { ToastProvider } from "@/components/primitives/Toast";

/** Single client boundary: theme state, plus the Tooltip and Toast providers the primitives need. */
export function Providers({ children }: { children: ReactNode }) {
  return (
    <ThemeProvider>
      <TooltipProvider delayDuration={200} skipDelayDuration={300}>
        <ToastProvider>{children}</ToastProvider>
      </TooltipProvider>
    </ThemeProvider>
  );
}
