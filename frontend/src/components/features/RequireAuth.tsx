"use client";

import * as React from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useSession } from "@/lib/auth";
import { PanelSkeleton } from "@/components/features/Skeletons";

/**
 * Client-side gate for owner-only pages. It sends a signed-out visitor to /login with a `next` back
 * to here, and shows nothing sensitive in the meantime. This is UX, not the security boundary — the
 * API rejects an unauthenticated request regardless (401), and every owner-only query is scoped to
 * the caller's own id server-side.
 */
export function RequireAuth({ children }: { children: React.ReactNode }) {
  const { me, loading, signedIn, error, refresh } = useSession();
  const router = useRouter();
  const pathname = usePathname() ?? "/";
  const loginHref = `/login?next=${encodeURIComponent(pathname)}`;

  // Redirect on `signedIn`, never on `me`. Bouncing someone who *has* a Clerk session to /login
  // just sends them back here the moment Clerk resolves, and round it goes.
  React.useEffect(() => {
    if (!loading && !signedIn) router.replace(loginHref);
  }, [loading, signedIn, router, loginHref]);

  if (loading) {
    return <PanelSkeleton lines={2} label="Checking your session…" />;
  }
  if (!signedIn) {
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
  if (!me) {
    return (
      <div className="p7-empty">
        <p className="p7-empty-title">We couldn&apos;t load your account</p>
        <p className="p7-empty-body">
          You are signed in, but the server did not confirm who you are
          {error ? `: ${error.message}` : ""}. It may be waking up.{" "}
          <button type="button" className="ss-link" onClick={refresh}>
            Try again
          </button>
          .
        </p>
      </div>
    );
  }
  return <>{children}</>;
}
