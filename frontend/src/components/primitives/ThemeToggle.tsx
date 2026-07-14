"use client";

import { Switch } from "@/components/primitives/Switch";
import { useTheme } from "@/lib/theme-provider";
import { cn } from "@/lib/utils";

/** Our theme toggle — a styled Radix Switch bound to the ThemeProvider. Not a library widget. */
export function ThemeToggle() {
  const { theme, setTheme } = useTheme();
  const isLight = theme === "light";
  return (
    <div className="ss-theme-toggle">
      <span className={cn("ss-theme-label", !isLight && "is-active")}>Dark</span>
      <Switch
        checked={isLight}
        onCheckedChange={(checked) => setTheme(checked ? "light" : "dark")}
        aria-label="Toggle light theme"
      />
      <span className={cn("ss-theme-label", isLight && "is-active")}>Light</span>
    </div>
  );
}
