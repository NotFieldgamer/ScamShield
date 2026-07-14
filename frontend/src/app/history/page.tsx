import type { Metadata } from "next";
import { PageFrame } from "@/components/frame/PageFrame";
import { RequireAuth } from "@/components/features/RequireAuth";
import { HistoryView } from "@/components/features/HistoryView";

export const metadata: Metadata = {
  title: "Your history · Scam Shield",
  description: "The postings and messages you've analyzed while signed in.",
};

export default function HistoryPage() {
  return (
    <PageFrame>
      <h1 className="p7-h1">Your history</h1>
      <p className="p7-sub">
        Every posting and message you&apos;ve analyzed while signed in, newest first. Filter by
        verdict or search the text. Open one to see the full analysis again.
      </p>
      <RequireAuth>
        <HistoryView />
      </RequireAuth>
    </PageFrame>
  );
}
