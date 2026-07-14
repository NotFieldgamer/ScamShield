import type { ReactNode } from "react";
import { SiteHeader } from "./SiteHeader";
import { SiteFooter } from "./SiteFooter";

/**
 * The shared shell for content pages: the scroll-aware site header, a centered content column, and
 * the three-column footer on the ambient plane. Transparency and community pages render inside this
 * so they match the dashboard and analyzer chrome. The column is flat (no glass) per the design rule
 * that data-dense surfaces stay opaque.
 */
export function PageFrame({ children }: { children: ReactNode }) {
  return (
    <>
      <SiteHeader />
      <main className="p7-shell">{children}</main>
      <SiteFooter />
    </>
  );
}
