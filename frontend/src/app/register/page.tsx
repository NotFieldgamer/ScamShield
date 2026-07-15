import { Suspense } from "react";
import type { Metadata } from "next";
import Link from "next/link";
import { AuthShell, AuthCardFallback } from "@/components/features/auth/AuthShell";
import { RegisterForm } from "@/components/features/RegisterForm";

export const metadata: Metadata = {
  title: "Create account · Verity",
  description: "Create an account to dispute a verdict and keep the postings you've analysed.",
};

export default async function RegisterPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string }>;
}) {
  const { next } = await searchParams;
  const loginHref = next ? `/login?next=${encodeURIComponent(next)}` : "/login";

  return (
    <AuthShell
      heading="Create your account"
      sub="One account lets you dispute a verdict and keep the postings you've analysed."
      footer={<Link href={loginHref}>Already have an account? Sign in</Link>}
    >
      <Suspense fallback={<AuthCardFallback />}>
        <RegisterForm />
      </Suspense>
    </AuthShell>
  );
}
