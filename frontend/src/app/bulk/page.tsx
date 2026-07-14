import type { Metadata } from "next";
import { PageFrame } from "@/components/frame/PageFrame";
import { RequireAuth } from "@/components/features/RequireAuth";
import { BulkScanView } from "@/components/features/BulkScanView";

export const metadata: Metadata = {
  title: "Bulk scan · Scam Shield",
  description: "Score a CSV of postings in one pass, through the same pipeline as a single paste.",
};

export default function BulkPage() {
  return (
    <PageFrame>
      <h1 className="p7-h1">Bulk scan</h1>
      <p className="p7-sub">
        Score a whole CSV of postings at once. Each row runs through the same pipeline as a single
        paste — phrase matching, the calibrated classifier, salary plausibility — and lands in your
        history.
      </p>
      <RequireAuth>
        <BulkScanView />
      </RequireAuth>
    </PageFrame>
  );
}
