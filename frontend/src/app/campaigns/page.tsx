import type { Metadata } from "next";
import { SiteNav } from "@/components/features/SiteNav";
import { CampaignsView } from "@/components/features/CampaignsView";

export const metadata: Metadata = {
  title: "Campaigns · Scam Shield",
  description: "The same scam, reposted under many company names — clustered by Union-Find.",
};

export default function CampaignsPage() {
  return (
    <main className="p7-shell">
      <SiteNav />
      <h1 className="p7-h1">Campaigns</h1>
      <p className="p7-sub">
        One scam is rarely posted once. Near-duplicate postings are grouped with Union-Find over
        their embeddings, surfacing the same fraud reposted under a dozen company names.
      </p>
      <CampaignsView />
    </main>
  );
}
