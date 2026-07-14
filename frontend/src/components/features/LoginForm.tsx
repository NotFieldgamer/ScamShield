"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { login, register } from "@/lib/auth";

export function LoginForm() {
  const router = useRouter();
  const [mode, setMode] = React.useState<"login" | "register">("login");
  const [email, setEmail] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [error, setError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      if (mode === "register") {
        await register(email, password);
      }
      await login(email, password);
      router.push("/");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="p7-panel" style={{ maxWidth: "28rem" }}>
      <div className="p7-actions" style={{ marginBottom: "1.25rem" }}>
        <button
          type="button"
          className={`ss-btn ${mode === "login" ? "ss-btn-primary" : "ss-btn-ghost"}`}
          onClick={() => setMode("login")}
        >
          Sign in
        </button>
        <button
          type="button"
          className={`ss-btn ${mode === "register" ? "ss-btn-primary" : "ss-btn-ghost"}`}
          onClick={() => setMode("register")}
        >
          Create account
        </button>
      </div>

      <form className="p7-form" onSubmit={submit}>
        <div className="p7-field">
          <label className="p7-label" htmlFor="email">
            Email
          </label>
          <input
            id="email"
            className="p7-input"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </div>
        <div className="p7-field">
          <label className="p7-label" htmlFor="password">
            Password
          </label>
          <input
            id="password"
            className="p7-input"
            type="password"
            autoComplete={mode === "register" ? "new-password" : "current-password"}
            required
            minLength={8}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>
        {error && <p className="p7-form-error">{error}</p>}
        <div className="p7-actions">
          <button type="submit" className="ss-btn ss-btn-primary" disabled={busy}>
            {busy ? "Working…" : mode === "login" ? "Sign in" : "Create account"}
          </button>
        </div>
      </form>

      {mode === "register" && (
        <p className="p7-panel-note" style={{ marginTop: "1rem", marginBottom: 0 }}>
          Reporting a verdict needs an account at least 7 days old — a guard against scammers
          disputing their own postings.
        </p>
      )}
    </div>
  );
}
