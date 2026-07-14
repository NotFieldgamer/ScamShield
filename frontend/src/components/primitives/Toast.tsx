"use client";

import * as React from "react";
import * as ToastPrimitive from "@radix-ui/react-toast";
import { cn } from "@/lib/utils";

/** Wraps Radix's Provider and renders the single Viewport for us. */
export function ToastProvider({
  children,
  ...props
}: React.ComponentPropsWithoutRef<typeof ToastPrimitive.Provider>) {
  return (
    <ToastPrimitive.Provider swipeDirection="right" {...props}>
      {children}
      <ToastPrimitive.Viewport className="ss-toast-viewport" />
    </ToastPrimitive.Provider>
  );
}

export function Toast({
  className,
  ...props
}: React.ComponentPropsWithoutRef<typeof ToastPrimitive.Root>) {
  return <ToastPrimitive.Root className={cn("ss-raised ss-toast", className)} {...props} />;
}

export function ToastTitle({
  className,
  ...props
}: React.ComponentPropsWithoutRef<typeof ToastPrimitive.Title>) {
  return <ToastPrimitive.Title className={cn("ss-toast-title", className)} {...props} />;
}

export function ToastDescription({
  className,
  ...props
}: React.ComponentPropsWithoutRef<typeof ToastPrimitive.Description>) {
  return <ToastPrimitive.Description className={cn("ss-toast-desc", className)} {...props} />;
}

export const ToastClose = ToastPrimitive.Close;
export const ToastAction = ToastPrimitive.Action;
