import type { Metadata } from "next";
import { SiteNav } from "@/components/features/SiteNav";
import { CampaignDetailView } from "@/components/features/CampaignDetailView";

export const metadata: Metadata = {
  title: "Campaign · Scam Shield",
  description: "A cluster of near-duplicate scam postings.",
};

export default function CampaignPage() {
  return (
    <main className="p7-shell">
      <SiteNav />
      <h1 className="p7-h1">Campaign</h1>
      <CampaignDetailView />
    </main>
  );
}
