import type { Metadata } from "next";
import { PageFrame } from "@/components/frame/PageFrame";
import { CampaignDetailView } from "@/components/features/CampaignDetailView";

export const metadata: Metadata = {
  title: "Campaign · Scam Shield",
  description: "A cluster of near-duplicate scam postings.",
};

export default function CampaignPage() {
  return (
    <PageFrame>
      <h1 className="p7-h1">Campaign</h1>
      <CampaignDetailView />
    </PageFrame>
  );
}
