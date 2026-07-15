import * as React from "react";
import Link from "next/link";
import { LogoMark } from "@/components/brand/LogoMark";
import { AuthSpecimen } from "./AuthSpecimen";

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
 * Shared chromeless layout for the auth screens: a dossier opened flat.
 *
 * Left is the intake sheet — logo mark, heading, one-line sub, the form. Right is the specimen it
 * is about. The spread is centred as a single object, so the form still reads left-of-centre — the
 * empty half was always the other page of the dossier, not space to be reclaimed.
 *
 * The specimen is the first thing dropped when space runs out (see .auth-specimen): below ~64rem
 * the form is the only thing that matters, and it takes the full column rather than being pushed
 * down the page by an illustration.
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
      <AuthSpecimen />
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
