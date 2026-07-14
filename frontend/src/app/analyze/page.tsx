import type { Metadata } from "next";
import { SiteHeader } from "@/components/frame/SiteHeader";
import { Analyzer } from "@/components/features/Analyzer";

export const metadata: Metadata = {
  title: "Analyze a posting · Scam Shield",
  description: "Paste a job post or recruiter message and see if it's likely a scam — and why.",
};

export default function AnalyzePage() {
  return (
    <>
      <SiteHeader />
      <main className="az-shell">
        <Analyzer />
      </main>
    </>
  );
}
