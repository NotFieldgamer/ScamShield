"use client";

import * as React from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { login, register } from "@/lib/auth";
import { ApiError } from "@/lib/api";
import { cn, safeInternalPath } from "@/lib/utils";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function emailProblem(value: string): string | null {
  const v = value.trim();
  if (!EMAIL_RE.test(v) || v.length > 320) {
    return "Enter a valid email address, like name@example.com";
  }
  return null;
}

/** A checklist row: a check when met, a dash while pending. Icon inherits the row's color. */
function CheckRow({ ok, children }: { ok: boolean; children: React.ReactNode }) {
  return (
    <li className={cn("auth-check", ok && "is-ok")}>
      <span className="auth-check__icon">
        {ok ? (
          <svg
            width="16"
            height="16"
            viewBox="0 0 16 16"
            fill="none"
            stroke="currentColor"
            strokeWidth={2}
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M3.5 8.4 6.4 11.3 12.5 4.7" />
          </svg>
        ) : (
          <svg
            width="16"
            height="16"
            viewBox="0 0 16 16"
            fill="none"
            stroke="currentColor"
            strokeWidth={2}
            strokeLinecap="round"
            aria-hidden="true"
          >
            <line x1="4" y1="8" x2="12" y2="8" />
          </svg>
        )}
      </span>
      <span>{children}</span>
      <span className="auth-sr">{ok ? "met" : "not met yet"}</span>
    </li>
  );
}

export function RegisterForm() {
  const router = useRouter();
  const next = useSearchParams().get("next");

  const [email, setEmail] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [confirm, setConfirm] = React.useState("");
  const [touched, setTouched] = React.useState<{ email?: boolean }>({});
  const [submitted, setSubmitted] = React.useState(false);
  const [formError, setFormError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);

  const emailError = emailProblem(email);
  const showEmailError = (touched.email || submitted) && emailError;

  const lengthOk = password.length >= 8;
  const tooLong = password.length > 200;
  const matchOk = confirm.length > 0 && password === confirm;

  const canSubmit = !emailError && lengthOk && !tooLong && matchOk;

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitted(true);
    setFormError(null);
    if (!canSubmit) return;
    setBusy(true);
    try {
      await register(email.trim(), password);
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
        <label className="auth-label" htmlFor="register-email">
          Email
        </label>
        <input
          id="register-email"
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
          aria-describedby={showEmailError ? "register-email-error" : undefined}
        />
        {showEmailError && (
          <p className="auth-error" id="register-email-error" role="alert">
            {emailError}
          </p>
        )}
      </div>

      <div className="auth-field">
        <label className="auth-label" htmlFor="register-password">
          Password
        </label>
        <input
          id="register-password"
          className="auth-input"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          aria-invalid={tooLong ? "true" : undefined}
          aria-describedby={tooLong ? "register-password-error" : undefined}
        />
        {tooLong && (
          <p className="auth-error" id="register-password-error" role="alert">
            Password must be 200 characters or fewer.
          </p>
        )}
      </div>

      <div className="auth-field">
        <label className="auth-label" htmlFor="register-confirm">
          Confirm password
        </label>
        <input
          id="register-confirm"
          className="auth-input"
          type="password"
          autoComplete="new-password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
        />
      </div>

      <ul className="auth-checklist" aria-label="Password requirements">
        <CheckRow ok={lengthOk && !tooLong}>At least 8 characters</CheckRow>
        <CheckRow ok={matchOk}>Passwords match</CheckRow>
      </ul>

      {formError && (
        <p className="auth-formerror" role="alert">
          {formError}
        </p>
      )}

      <div className="auth-actions">
        <button type="submit" className="ss-btn ss-btn-primary" disabled={!canSubmit || busy}>
          {busy ? "Creating your account…" : "Create account"}
        </button>
      </div>

      <p className="auth-note">
        Reporting a verdict needs an account at least 7 days old — a guard against scammers disputing
        their own postings.
      </p>
    </form>
  );
}
