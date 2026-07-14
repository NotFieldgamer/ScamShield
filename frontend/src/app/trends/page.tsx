import type { Metadata } from "next";
import { SiteNav } from "@/components/features/SiteNav";
import { TrendsView } from "@/components/features/TrendsView";

export const metadata: Metadata = {
  title: "Trends · Scam Shield",
  description: "Which scam patterns are rising, aggregated from stored verdict features.",
};

export default function TrendsPage() {
  return (
    <main className="p7-shell">
      <SiteNav />
      <h1 className="p7-h1">Trends</h1>
      <p className="p7-sub">
        Which scam patterns are rising. Every count is aggregated from the feature contributions
        we&apos;ve already computed and stored for real verdicts — no invented deltas.
      </p>
      <TrendsView />
    </main>
  );
}
