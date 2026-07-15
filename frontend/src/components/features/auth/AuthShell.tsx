import * as React from "react";
import Link from "next/link";
import { LogoMark } from "@/components/brand/LogoMark";

type AuthShellProps = {
  /** Page heading, set in the display serif. */
  heading: string;
  /** One-line supporting sentence under the heading. */
  sub: string;
  /** The card slot — the form (its own `.auth-card surface-card` element). */
  children: React.ReactNode;
  /** Footer links slot, rendered below the card. */
  footer?: React.ReactNode;
};

/**
 * Shared chromeless layout for the auth screens. No SiteHeader: a home-linked logo mark on top,
 * then the heading, a one-line sub, the card, and an optional footer-links row. The card sits
 * offset toward the upper-left of the viewport (see .auth-shell) rather than centred in a void.
 */
export function AuthShell({ heading, sub, children, footer }: AuthShellProps) {
  return (
    <main className="auth-shell">
      <div className="auth-inner">
        <Link href="/" className="auth-mark" aria-label="Verity — home">
          <LogoMark size={40} />
        </Link>
        <h1 className="auth-head">{heading}</h1>
        <p className="auth-sub">{sub}</p>
        {children}
        {footer ? <div className="auth-links">{footer}</div> : null}
      </div>
    </main>
  );
}

/** Placeholder card shown inside the Suspense boundary while a search-param-reading form loads. */
export function AuthCardFallback() {
  return (
    <div className="auth-card surface-card" aria-hidden="true">
      <div className="az-skel" style={{ height: 44, marginBottom: "1.1rem" }} />
      <div className="az-skel" style={{ height: 44, marginBottom: "1.1rem" }} />
      <div className="az-skel" style={{ height: 42, width: "100%" }} />
    </div>
  );
}
