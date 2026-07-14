import type { Metadata } from "next";
import { SiteHeader } from "@/components/frame/SiteHeader";
import { Dashboard } from "@/components/features/Dashboard";

export const metadata: Metadata = {
  title: "Dashboard · Scam Shield",
  description: "Your hub for Scam Shield — analyze a posting, review trends, model performance, and campaigns.",
};

export default function DashboardPage() {
  return (
    <>
      <SiteHeader />
      <main>
        <Dashboard />
      </main>
    </>
  );
}
