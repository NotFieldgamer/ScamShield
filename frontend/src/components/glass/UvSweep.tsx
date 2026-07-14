"use client";

import { motion, useReducedMotion } from "framer-motion";

/**
 * The signature moment: a band of light passing down a glass card. Animates transform and
 * opacity only, in under 900ms. Re-runs whenever `runId` changes (the parent increments it).
 * Under prefers-reduced-motion the travel is dropped for an instant state change (a brief flash).
 *
 * Place inside a positioned, overflow-hidden glass surface (GlassCard provides both).
 */
export function UvSweep({ runId }: { runId: number }) {
  const reduce = useReducedMotion();
  if (runId <= 0) return null;

  return (
    <motion.span
      key={runId}
      className="ss-sweep"
      aria-hidden="true"
      initial={{ y: "-100%", opacity: 0 }}
      animate={
        reduce
          ? { y: "-100%", opacity: [0.55, 0] }
          : { y: "250%", opacity: [0, 0.9, 0.9, 0] }
      }
      transition={
        reduce
          ? { duration: 0.15, times: [0, 1] }
          : { duration: 0.8, ease: "easeInOut", times: [0, 0.15, 0.85, 1] }
      }
    />
  );
}
