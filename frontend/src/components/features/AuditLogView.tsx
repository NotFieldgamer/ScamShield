"use client";

import * as React from "react";
import Link from "next/link";
import { auditLog, useSession, type AuditEntry } from "@/lib/auth";

export function AuditLogView() {
  const { me, loading } = useSession();
  const [entries, setEntries] = React.useState<AuditEntry[] | null>(null);
  const [error, setError] = React.useState<string | null>(null);

  const isAdmin = me?.role === "ADMIN";

  React.useEffect(() => {
    if (!isAdmin) return;
    auditLog(200)
      .then(setEntries)
      .catch((e) => setError(e.message ?? "Could not load the audit log."));
  }, [isAdmin]);

  if (loading) return <div className="p7-panel">Checking your session…</div>;

  if (!isAdmin) {
    return (
      <div className="p7-panel">
        <p className="p7-panel-title">Admins only</p>
        <p className="p7-panel-note">
          The audit log is restricted to admins. {me ? "Your account isn't an admin." : "You're not signed in."}
        </p>
        {!me && (
          <Link className="ss-btn ss-btn-primary" href="/login">
            Sign in
          </Link>
        )}
      </div>
    );
  }

  if (error) {
    return (
      <div className="p7-empty">
        <p className="p7-empty-title">Couldn&apos;t load the audit log</p>
        <p className="p7-empty-body">{error}</p>
      </div>
    );
  }
  if (!entries) return <div className="p7-panel">Loading audit log…</div>;

  return (
    <div className="p7-panel">
      <p className="p7-panel-title">Audit log</p>
      <p className="p7-panel-note">
        Append-only, newest first. The database enforces the append-only invariant with a trigger —
        rows cannot be updated or deleted, even by the application.
      </p>
      <div className="p7-table-wrap">
        <table className="p7-table">
          <thead>
            <tr>
              <th>When</th>
              <th>Actor</th>
              <th>Action</th>
              <th>Target</th>
              <th>IP</th>
            </tr>
          </thead>
          <tbody>
            {entries.map((e) => (
              <tr key={e.id}>
                <td className="p7-num" style={{ color: "var(--text-faint)", fontSize: "0.75rem" }}>
                  {new Date(e.createdAt).toLocaleString()}
                </td>
                <td className="p7-num">{e.actorId ?? "—"}</td>
                <td className="p7-mono">{e.action}</td>
                <td className="p7-mono" style={{ maxWidth: "22ch", overflow: "hidden", textOverflow: "ellipsis" }}>
                  {e.targetType}
                  {e.targetId ? `:${e.targetId}` : ""}
                </td>
                <td className="p7-num" style={{ color: "var(--text-faint)" }}>{e.ip ?? "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
