import type { Metadata } from "next";
import Link from "next/link";
import { SignUp } from "@clerk/nextjs";
import { AuthShell } from "@/components/features/auth/AuthShell";
import { safeInternalPath } from "@/lib/utils";

export const metadata: Metadata = {
  title: "Create account · Verity",
  description: "Create an account to dispute a verdict and keep the postings you've analysed.",
};

/**
 * Catch-all for the same reason as /login: Clerk's sign-up flow pushes sub-paths for email
 * verification. The local account row is not created here — the API provisions it on this user's
 * first authenticated request.
 */
export default async function RegisterPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string }>;
}) {
  const { next } = await searchParams;
  const afterSignUp = safeInternalPath(next, "/dashboard");
  const loginHref = next ? `/login?next=${encodeURIComponent(afterSignUp)}` : "/login";

  return (
    <AuthShell
      heading="Create your account"
      sub="One account lets you dispute a verdict and keep the postings you've analysed."
      // Ours, not Clerk's: its footer is suppressed (it carried a duplicate heading and a
      // "Secured by Clerk" badge), and this link has to survive that.
      footer={<Link href={loginHref}>Already have an account? Sign in</Link>}
    >
      <SignUp signInUrl={loginHref} fallbackRedirectUrl={afterSignUp} />
    </AuthShell>
  );
}
