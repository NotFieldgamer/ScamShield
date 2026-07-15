import type { Metadata } from "next";
import Link from "next/link";
import { SignIn } from "@clerk/nextjs";
import { AuthShell } from "@/components/features/auth/AuthShell";
import { safeInternalPath } from "@/lib/utils";

export const metadata: Metadata = {
  title: "Sign in · Verity",
  description: "Sign in to dispute a verdict or, for admins, work the report queue.",
};

/**
 * Clerk owns the form; we own the page around it. The route is a catch-all because Clerk's flow
 * pushes sub-paths onto it (/login/factor-one, /login/reset-password) — a plain /login would 404
 * the moment a user hit two-factor or a password reset.
 *
 * Password reset lives inside Clerk's form, which is why there is no /forgot-password page any more.
 */
export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string }>;
}) {
  const { next } = await searchParams;
  // `next` is attacker-controllable, so it is never handed to Clerk raw: safeInternalPath rejects
  // anything that is not a path on this origin, which stops /login?next=https://evil.example
  // turning a real sign-in into an open redirect.
  const afterSignIn = safeInternalPath(next, "/dashboard");
  const registerHref = next ? `/register?next=${encodeURIComponent(afterSignIn)}` : "/register";

  return (
    <AuthShell
      heading="Sign in"
      sub="Sign in to dispute a verdict or, for admins, work the report queue."
      // Ours, not Clerk's: its footer is suppressed (duplicate heading + "Secured by Clerk"), so
      // the route to sign-up has to live here. Password reset stays inside Clerk's own form.
      footer={<Link href={registerHref}>New here? Create an account</Link>}
    >
      <SignIn signUpUrl={registerHref} fallbackRedirectUrl={afterSignIn} />
    </AuthShell>
  );
}
