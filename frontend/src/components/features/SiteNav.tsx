"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useClerk } from "@clerk/nextjs";
import { ThemeToggle } from "@/components/primitives/ThemeToggle";
import { useSession } from "@/lib/auth";
import { cn } from "@/lib/utils";

const LINKS = [
  { href: "/analyze", label: "Analyze" },
  { href: "/model", label: "Model" },
  { href: "/trends", label: "Trends" },
  { href: "/campaigns", label: "Campaigns" },
];

export function SiteNav() {
  const path = usePathname() ?? "";
  const { me } = useSession();
  const { signOut } = useClerk();
  const isMod = me?.role === "ADMIN";

  return (
    <nav className="p7-nav">
      <Link href="/" className="p7-brand">
        Verity
      </Link>
      <div className="p7-nav-links">
        {LINKS.map((l) => (
          <Link
            key={l.href}
            href={l.href}
            className={cn("p7-nav-link", path.startsWith(l.href) && "is-active")}
          >
            {l.label}
          </Link>
        ))}
        {isMod && (
          <Link
            href="/admin/reports"
            className={cn("p7-nav-link", path.startsWith("/admin/reports") && "is-active")}
          >
            Reports
          </Link>
        )}
        {me?.role === "ADMIN" && (
          <Link
            href="/admin/audit"
            className={cn("p7-nav-link", path.startsWith("/admin/audit") && "is-active")}
          >
            Audit
          </Link>
        )}
      </div>
      <div className="p7-nav-spacer" />
      {me ? (
        <button
          type="button"
          className="ss-btn ss-btn-ghost"
          onClick={() => signOut({ redirectUrl: "/" })}
        >
          Sign out
        </button>
      ) : (
        <Link href="/login" className={cn("p7-nav-link", path.startsWith("/login") && "is-active")}>
          Sign in
        </Link>
      )}
      <ThemeToggle />
    </nav>
  );
}
