import type { Metadata } from "next";
import { PageFrame } from "@/components/frame/PageFrame";
import { ModelExplorer } from "@/components/features/ModelExplorer";

export const metadata: Metadata = {
  title: "Model performance · Scam Shield",
  description:
    "The served model's precision, recall, and errors — and a threshold slider showing the live tradeoff between blocking real jobs and letting scams through.",
};

export default function ModelPage() {
  return (
    <PageFrame>
      <h1 className="p7-h1">Model performance</h1>
      <p className="p7-sub">
        This page exists because the product&apos;s credibility depends on it. Every number is
        recomputed from the served model&apos;s held-out predictions — move the threshold and watch
        the tradeoff shift in real terms: real jobs blocked versus scams let through.
      </p>
      <ModelExplorer />
    </PageFrame>
  );
}
