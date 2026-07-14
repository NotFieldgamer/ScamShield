"use client";

import * as React from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useSession } from "@/lib/auth";

/**
 * Client-side gate for owner-only pages. It sends a signed-out visitor to /login with a `next` back
 * to here, and shows nothing sensitive in the meantime. This is UX, not the security boundary — the
 * API rejects an unauthenticated request regardless (401), and every owner-only query is scoped to
 * the caller's own id server-side.
 */
export function RequireAuth({ children }: { children: React.ReactNode }) {
  const { me, loading } = useSession();
  const router = useRouter();
  const pathname = usePathname() ?? "/";
  const loginHref = `/login?next=${encodeURIComponent(pathname)}`;

  React.useEffect(() => {
    if (!loading && !me) router.replace(loginHref);
  }, [loading, me, router, loginHref]);

  if (loading) {
    return <div className="p7-panel">Checking your session…</div>;
  }
  if (!me) {
    return (
      <div className="p7-empty">
        <p className="p7-empty-title">Sign in to continue</p>
        <p className="p7-empty-body">
          This page shows your own analyses, so it needs an account.{" "}
          <Link href={loginHref} className="ss-link">
            Sign in
          </Link>{" "}
          to see it.
        </p>
      </div>
    );
  }
  return <>{children}</>;
}
