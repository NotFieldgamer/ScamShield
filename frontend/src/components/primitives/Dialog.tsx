"use client";

import * as React from "react";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { cn } from "@/lib/utils";

export const Dialog = DialogPrimitive.Root;
export const DialogTrigger = DialogPrimitive.Trigger;
export const DialogClose = DialogPrimitive.Close;

/**
 * The Dialog surface. Glass by default — one inspection layer floating over the dimmed overlay.
 * Pass `surface="flat"` for data-dense overlays (e.g. a campaign cluster) that follow the rule that
 * tables and lists stay opaque.
 */
export function DialogContent({
  className,
  surface = "glass",
  children,
  ...props
}: React.ComponentPropsWithoutRef<typeof DialogPrimitive.Content> & {
  surface?: "glass" | "flat";
}) {
  return (
    <DialogPrimitive.Portal>
      <DialogPrimitive.Overlay className="ss-overlay" />
      <DialogPrimitive.Content
        className={cn(surface === "flat" ? "surface-card" : "glass", "ss-dialog", className)}
        {...props}
      >
        {children}
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  );
}

export function DialogTitle({
  className,
  ...props
}: React.ComponentPropsWithoutRef<typeof DialogPrimitive.Title>) {
  return <DialogPrimitive.Title className={cn("ss-dialog-title", className)} {...props} />;
}

export function DialogDescription({
  className,
  ...props
}: React.ComponentPropsWithoutRef<typeof DialogPrimitive.Description>) {
  return <DialogPrimitive.Description className={cn("ss-dialog-desc", className)} {...props} />;
}
