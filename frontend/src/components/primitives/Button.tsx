import * as React from "react";
import { cn } from "@/lib/utils";

type ButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "ghost";
  size?: "sm" | "md";
};

/** A native <button>, styled by us — not from any component library. forwardRef so Radix
 *  triggers can attach to it via `asChild`. */
export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { className, variant = "primary", size = "md", type = "button", ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      className={cn(
        "ss-btn",
        variant === "primary" ? "ss-btn-primary" : "ss-btn-ghost",
        size === "sm" && "ss-btn-sm",
        className,
      )}
      {...props}
    />
  );
});
