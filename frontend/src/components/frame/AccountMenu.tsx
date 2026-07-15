"use client";

import Link from "next/link";
import { useClerk } from "@clerk/nextjs";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/primitives/DropdownMenu";
import { useSession } from "@/lib/auth";

/**
 * The header's account control: "Sign in" with no session, the caller's own menu with one.
 *
 * Built from our DropdownMenu, not Clerk's <UserButton/>: Clerk owns the sign-in and sign-up
 * screens and nothing else, so every other surface stays ours.
 *
 * While the session resolves it renders an inert placeholder of the same size rather than "Sign
 * in". Showing "Sign in" to someone who is signed in — for the ~200ms the /me call takes — is both
 * a lie and a layout jump.
 */
export function AccountMenu() {
  const { me, loading } = useSession();
  const { signOut } = useClerk();

  if (loading) {
    return <span className="account-trigger account-trigger--pending" aria-hidden="true" />;
  }

  if (!me) {
    return (
      <Link href="/login" className="btn-signin">
        Sign in
      </Link>
    );
  }

  const initial = me.email.trim().charAt(0).toUpperCase() || "?";

  return (
    <DropdownMenu>
      <DropdownMenuTrigger className="account-trigger" aria-label={`Account — ${me.email}`}>
        <span className="account-initial" aria-hidden="true">
          {initial}
        </span>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuLabel>{me.email}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <Link href="/dashboard">Dashboard</Link>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <Link href="/history">Your history</Link>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <Link href="/bulk">Bulk scan</Link>
        </DropdownMenuItem>
        {me.role === "ADMIN" && (
          <>
            <DropdownMenuSeparator />
            <DropdownMenuItem asChild>
              <Link href="/admin/reports">Reports</Link>
            </DropdownMenuItem>
            <DropdownMenuItem asChild>
              <Link href="/admin/audit">Audit</Link>
            </DropdownMenuItem>
          </>
        )}
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => void signOut({ redirectUrl: "/" })}>
          Sign out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
