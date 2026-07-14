import type { Metadata } from "next";
import { SiteNav } from "@/components/features/SiteNav";
import { LoginForm } from "@/components/features/LoginForm";

export const metadata: Metadata = {
  title: "Sign in · Scam Shield",
  description: "Sign in to report verdicts and, for admins, review the queue.",
};

export default function LoginPage() {
  return (
    <main className="p7-shell">
      <SiteNav />
      <h1 className="p7-h1">Sign in</h1>
      <p className="p7-sub">
        An account lets you dispute a verdict and, for admins, work the report queue. The
        password is hashed with BCrypt; the session refresh token lives only in an httpOnly cookie.
      </p>
      <LoginForm />
    </main>
  );
}
