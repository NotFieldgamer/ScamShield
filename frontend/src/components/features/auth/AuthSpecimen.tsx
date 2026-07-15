import * as React from "react";

/**
 * The right half of the auth spread: a specimen under examination.
 *
 * The auth screen is a dossier opened flat — the form is the intake sheet where you identify
 * yourself, this is the evidence pinned opposite. It is not decoration bolted onto empty space: it
 * is the product's signature moment (flagged phrases fluorescing in evidence ink) running exactly
 * as the analyzer runs it, so a first-time visitor learns what Verity does before they have an
 * account. Same mono face, same vermillion mark, same lighting order.
 *
 * Honesty: every phrase marked below is a real row in the matcher's registry (see V2's
 * `scam_phrases` seed — "work from home", "no experience needed", "immediate start", "earn daily",
 * "processing fee", "whatsapp"). Nothing here is invented, and deliberately **no score is shown** —
 * a probability on this page would be a hardcoded figure tracing to no computation, which the brief
 * forbids. The specimen shows *what* is caught, never a number we did not compute.
 *
 * Pure CSS, no client JS: it renders on the server and animates by staggered `--i`, so the auth
 * screen's cost stays where it belongs — the form.
 */

/** The posting fragment, split so flagged spans can be marked. `flag` = present in the registry. */
const SPECIMEN: ReadonlyArray<{ t: string; flag?: boolean }> = [
  { t: "Data Entry Assistant — " },
  { t: "work from home", flag: true },
  { t: ", " },
  { t: "no experience needed", flag: true },
  { t: ".\n" },
  { t: "immediate start", flag: true },
  { t: ". You can " },
  { t: "earn daily", flag: true },
  { t: ", paid every Friday.\n\nTo activate your account we ask a small " },
  { t: "processing fee", flag: true },
  { t: " of ₹1,500, refundable after your first week. Message our HR manager on " },
  { t: "whatsapp", flag: true },
  { t: " to begin." },
];

export function AuthSpecimen() {
  let lit = -1;
  return (
    // aria-hidden: this is an illustration of the product, and the sighted-only reading of it is
    // carried by the caption below. A screen reader hearing a scam posting read out mid-sign-in
    // would be noise, not information.
    <aside className="auth-specimen" aria-hidden="true">
      <div className="auth-specimen-sheet surface-card">
        <p className="auth-specimen-label">
          <span className="auth-specimen-dot" />
          Specimen · flagged phrases
        </p>
        <p className="auth-specimen-body">
          {SPECIMEN.map((part, i) => {
            if (!part.flag) return <span key={i}>{part.t}</span>;
            lit += 1;
            return (
              <mark
                key={i}
                className="auth-specimen-mark"
                style={{ "--i": lit } as React.CSSProperties}
              >
                {part.t}
              </mark>
            );
          })}
        </p>
      </div>
      <p className="auth-specimen-caption">
        This is a scan. Verity marks the phrases that pushed a posting toward{" "}
        <em>likely scam</em>, then shows exactly how much each one weighed — so you can judge the
        verdict instead of trusting it.
      </p>
    </aside>
  );
}
