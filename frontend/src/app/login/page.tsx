import { Suspense } from "react";
import type { Metadata } from "next";
import Link from "next/link";
import { AuthShell, AuthCardFallback } from "@/components/features/auth/AuthShell";
import { LoginForm } from "@/components/features/LoginForm";

export const metadata: Metadata = {
  title: "Sign in · Verity",
  description: "Sign in to dispute a verdict or, for admins, work the report queue.",
};

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string }>;
}) {
  const { next } = await searchParams;
  const registerHref = next ? `/register?next=${encodeURIComponent(next)}` : "/register";

  return (
    <AuthShell
      heading="Sign in"
      sub="Sign in to dispute a verdict or, for admins, work the report queue."
      footer={
        <>
          <Link href="/forgot-password">Forgot password?</Link>
          <Link href={registerHref}>New here? Create an account</Link>
        </>
      }
    >
      <Suspense fallback={<AuthCardFallback />}>
        <LoginForm />
      </Suspense>
    </AuthShell>
  );
}
