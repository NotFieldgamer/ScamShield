import type { Metadata } from "next";
import { SiteHeader } from "@/components/frame/SiteHeader";
import { Dashboard } from "@/components/features/Dashboard";

export const metadata: Metadata = {
  title: "Dashboard · Verity",
  description: "Your hub for Verity — analyze a posting, review trends, model performance, and campaigns.",
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
