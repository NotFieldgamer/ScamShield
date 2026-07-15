import type { Metadata } from "next";
import { SiteNav } from "@/components/features/SiteNav";
import { AuditLogView } from "@/components/features/AuditLogView";

export const metadata: Metadata = {
  title: "Audit log · Verity",
  description: "The append-only audit trail.",
};

export default function AdminAuditPage() {
  return (
    <main className="p7-shell">
      <SiteNav />
      <h1 className="p7-h1">Audit log</h1>
      <p className="p7-sub">
        Every privileged action — logins, refreshes, reports, moderation decisions, reclustering —
        lands here, append-only, enforced at the database.
      </p>
      <AuditLogView />
    </main>
  );
}
