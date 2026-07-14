"use client";

import * as React from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { login } from "@/lib/auth";
import { ApiError } from "@/lib/api";
import { safeInternalPath } from "@/lib/utils";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function emailProblem(value: string): string | null {
  const v = value.trim();
  if (!EMAIL_RE.test(v) || v.length > 320) {
    return "Enter a valid email address, like name@example.com";
  }
  return null;
}

export function LoginForm() {
  const router = useRouter();
  const next = useSearchParams().get("next");

  const [email, setEmail] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [touched, setTouched] = React.useState<{ email?: boolean; password?: boolean }>({});
  const [submitted, setSubmitted] = React.useState(false);
  const [formError, setFormError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);

  const emailError = emailProblem(email);
  const passwordError = password.length === 0 ? "Enter your password" : null;

  const showEmailError = (touched.email || submitted) && emailError;
  const showPasswordError = (touched.password || submitted) && passwordError;

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitted(true);
    setFormError(null);
    if (emailError || passwordError) return;
    setBusy(true);
    try {
      await login(email.trim(), password);
      router.push(safeInternalPath(next));
    } catch (err) {
      setFormError(
        err instanceof ApiError
          ? err.message
          : "Couldn't reach the server. Check your connection and try again.",
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="auth-card surface-card" onSubmit={onSubmit} noValidate>
      <div className="auth-field">
        <label className="auth-label" htmlFor="login-email">
          Email
        </label>
        <input
          id="login-email"
          className="auth-input"
          type="email"
          inputMode="email"
          autoComplete="email"
          autoCapitalize="none"
          spellCheck={false}
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          onBlur={() => setTouched((t) => ({ ...t, email: true }))}
          aria-invalid={showEmailError ? "true" : undefined}
          aria-describedby={showEmailError ? "login-email-error" : undefined}
        />
        {showEmailError && (
          <p className="auth-error" id="login-email-error" role="alert">
            {emailError}
          </p>
        )}
      </div>

      <div className="auth-field">
        <label className="auth-label" htmlFor="login-password">
          Password
        </label>
        <input
          id="login-password"
          className="auth-input"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          onBlur={() => setTouched((t) => ({ ...t, password: true }))}
          aria-invalid={showPasswordError ? "true" : undefined}
          aria-describedby={showPasswordError ? "login-password-error" : undefined}
        />
        {showPasswordError && (
          <p className="auth-error" id="login-password-error" role="alert">
            {passwordError}
          </p>
        )}
      </div>

      {formError && (
        <p className="auth-formerror" role="alert">
          {formError}
        </p>
      )}

      <div className="auth-actions">
        <button type="submit" className="ss-btn ss-btn-primary" disabled={busy}>
          {busy ? "Signing in…" : "Sign in"}
        </button>
      </div>
    </form>
  );
}
