"use client";

import * as React from "react";
import Link from "next/link";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function emailProblem(value: string): string | null {
  const v = value.trim();
  if (!EMAIL_RE.test(v) || v.length > 320) {
    return "Enter a valid email address, like name@example.com";
  }
  return null;
}

export function ForgotPasswordForm() {
  const [email, setEmail] = React.useState("");
  const [touched, setTouched] = React.useState(false);
  const [submitted, setSubmitted] = React.useState(false);
  const [done, setDone] = React.useState(false);

  const emailError = emailProblem(email);
  const showEmailError = (touched || submitted) && emailError;

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitted(true);
    if (emailError) return;
    // There is no reset endpoint in this build. Do NOT claim an email was sent — switch to an
    // honest confirmation state instead.
    setDone(true);
  }

  if (done) {
    return (
      <div className="auth-card surface-card" role="status">
        <h2 className="auth-card-head">Reset isn&apos;t wired up yet</h2>
        <p className="auth-card-body">
          Password reset by email isn&apos;t enabled in this build. If you know your password, sign
          in — otherwise you can create a new account.
        </p>
        <div className="auth-links auth-links--inline">
          <Link href="/login">Sign in</Link>
          <Link href="/register">Create an account</Link>
        </div>
      </div>
    );
  }

  return (
    <form className="auth-card surface-card" onSubmit={onSubmit} noValidate>
      <div className="auth-field">
        <label className="auth-label" htmlFor="forgot-email">
          Email
        </label>
        <input
          id="forgot-email"
          className="auth-input"
          type="email"
          inputMode="email"
          autoComplete="email"
          autoCapitalize="none"
          spellCheck={false}
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          onBlur={() => setTouched(true)}
          aria-invalid={showEmailError ? "true" : undefined}
          aria-describedby={showEmailError ? "forgot-email-error" : undefined}
        />
        {showEmailError && (
          <p className="auth-error" id="forgot-email-error" role="alert">
            {emailError}
          </p>
        )}
      </div>

      <div className="auth-actions">
        <button type="submit" className="ss-btn ss-btn-primary">
          Continue
        </button>
      </div>
    </form>
  );
}
