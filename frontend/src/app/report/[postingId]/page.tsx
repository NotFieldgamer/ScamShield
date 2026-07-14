import type { Metadata } from "next";
import { SiteNav } from "@/components/features/SiteNav";
import { ReportForm } from "@/components/features/ReportForm";

export const metadata: Metadata = {
  title: "Report a verdict · Scam Shield",
  description: "Dispute a verdict. Guarded against abuse of the feedback loop.",
};

export default function ReportPage() {
  return (
    <main className="p7-shell">
      <SiteNav />
      <h1 className="p7-h1">Report a verdict</h1>
      <p className="p7-sub">
        Think we got it wrong — or want to confirm a scam? Your report helps, and it&apos;s guarded:
        new accounts can&apos;t report, agreement needs two independent voices, and only moderators
        change the training set.
      </p>
      <ReportForm />
    </main>
  );
}
