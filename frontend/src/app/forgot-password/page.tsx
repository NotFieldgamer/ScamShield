import type { Metadata } from "next";
import Link from "next/link";
import { AuthShell } from "@/components/features/auth/AuthShell";
import { ForgotPasswordForm } from "@/components/features/ForgotPasswordForm";

export const metadata: Metadata = {
  title: "Reset password · Verity",
  description: "Recover access to your Verity account.",
};

export default function ForgotPasswordPage() {
  return (
    <AuthShell
      heading="Reset your password"
      sub="Enter the email on your account to continue."
      footer={<Link href="/login">Remembered it? Sign in</Link>}
    >
      <ForgotPasswordForm />
    </AuthShell>
  );
}
