import type { ReactNode } from "react";
import { Skeleton } from "@/components/primitives/Skeleton";

/**
 * Skeleton loaders for the app's async surfaces — content-shaped placeholders that hold layout while
 * data loads, instead of a spinner. Each is a polite status region: screen readers hear the label,
 * sighted users see the shape. They degrade to static blocks under prefers-reduced-motion (the sweep
 * is CSS, disabled there).
 */
function Status({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div role="status" aria-busy="true" aria-label={label}>
      {children}
      <span className="ss-sr-only">{label}</span>
    </div>
  );
}

/** A single flat panel with a title bar and a few text lines. For the model page and detail views. */
export function PanelSkeleton({ lines = 3, label = "Loading…" }: { lines?: number; label?: string }) {
  return (
    <Status label={label}>
      <div className="p7-panel">
        <Skeleton style={{ height: 18, width: "38%" }} />
        {Array.from({ length: lines }).map((_, i) => (
          <Skeleton key={i} style={{ height: 12, width: `${88 - i * 11}%`, marginTop: i === 0 ? 16 : 9 }} />
        ))}
      </div>
    </Status>
  );
}

/** A responsive grid of card-shaped placeholders. For history and campaigns. */
export function CardGridSkeleton({ count = 6, label = "Loading…" }: { count?: number; label?: string }) {
  return (
    <Status label={label}>
      <div className="hist-grid">
        {Array.from({ length: count }).map((_, i) => (
          <div className="surface-card sk-card" key={i}>
            <Skeleton style={{ height: 16, width: "52%" }} />
            <Skeleton style={{ height: 12, width: "100%", marginTop: 14 }} />
            <Skeleton style={{ height: 12, width: "84%", marginTop: 8 }} />
            <Skeleton style={{ height: 12, width: "42%", marginTop: 16 }} />
          </div>
        ))}
      </div>
    </Status>
  );
}

/** A panel with a title bar and full-width rows. For the trends and bulk-results tables. */
export function TableSkeleton({ rows = 6, label = "Loading…" }: { rows?: number; label?: string }) {
  return (
    <Status label={label}>
      <div className="p7-panel">
        <Skeleton style={{ height: 18, width: "34%", marginBottom: 18 }} />
        {Array.from({ length: rows }).map((_, i) => (
          <Skeleton key={i} style={{ height: 14, width: "100%", marginTop: i === 0 ? 0 : 11 }} />
        ))}
      </div>
    </Status>
  );
}
