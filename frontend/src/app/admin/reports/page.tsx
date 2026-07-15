import type { Metadata } from "next";
import { SiteNav } from "@/components/features/SiteNav";
import { ModerationQueue } from "@/components/features/ModerationQueue";

export const metadata: Metadata = {
  title: "Moderation · Verity",
  description: "The admin queue of community reports.",
};

export default function AdminReportsPage() {
  return (
    <main className="p7-shell">
      <SiteNav />
      <h1 className="p7-h1">Moderation queue</h1>
      <p className="p7-sub">
        Community reports awaiting a decision. An admin confirmation is the only signal
        retraining is allowed to trust.
      </p>
      <ModerationQueue />
    </main>
  );
}
