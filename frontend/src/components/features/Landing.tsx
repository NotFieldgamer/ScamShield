"use client";

import * as React from "react";
import Link from "next/link";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { SiteHeader } from "@/components/frame/SiteHeader";
import { SiteFooter } from "@/components/frame/SiteFooter";
import { LogoMark } from "@/components/brand/LogoMark";
import { HeroPaste } from "./HeroPaste";

/* The four flag categories, each with an icon drawn in the logo's language: geometric,
 * single-weight strokes, inheriting the accent via currentColor. Every line of copy is real. */
const CATCHES = [
  {
    key: "phrases",
    title: "Known scam phrases",
    body: "Advance-fee asks, requests for bank details, “guaranteed income” — matched against a registry of fraud phrases in a single pass of the text.",
    icon: (
      <>
        <path d="M5 6H21a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H11l-4 3v-3H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2Z" />
        <path d="M7 10H19M7 13H15" />
      </>
    ),
  },
  {
    key: "domains",
    title: "Look-alike domains",
    body: "A recruiter linking linkedln.com instead of linkedin.com is one edit away from the real thing — and edit distance catches it.",
    icon: (
      <>
        <circle cx="14" cy="14" r="9" />
        <path d="M14 5c-4 3.5-4 14 0 18M14 5c4 3.5 4 14 0 18" />
        <path d="M5 14H23" />
      </>
    ),
  },
  {
    key: "pay",
    title: "Pay that doesn't add up",
    body: "“Data entry, $5,000 a week” is caught by arithmetic: pay more than three standard deviations above the going rate for the role.",
    icon: (
      <>
        <path d="M4 22H24" />
        <path d="M4 11H24" strokeDasharray="2 2" />
        <path d="M8 22V17M14 22V15M20 22V6" />
      </>
    ),
  },
  {
    key: "language",
    title: "Language the model has learned",
    body: "Word and character patterns weighted as fraud during training — each shown with its exact contribution to the score, coefficient × tf-idf.",
    icon: (
      <>
        <path d="M14 3 22 6V13C22 19 18.5 22.5 14 24 9.5 22.5 6 19 6 13V6Z" />
        <path d="M10 11H18M10 14H15M10 17H16" />
      </>
    ),
  },
];

function CatchIcon({ children }: { children: React.ReactNode }) {
  return (
    <svg
      className="lp-catch-icon"
      width="28"
      height="28"
      viewBox="0 0 28 28"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {children}
    </svg>
  );
}

/**
 * The landing page. The tool's home is /analyze; the hero carries a live paste box that hands off
 * there. Exactly one orchestrated scroll reveal exists on the whole page — the "What it catches"
 * cards (section 3) — and it degrades to plain visible content under prefers-reduced-motion.
 */
export function Landing() {
  const root = React.useRef<HTMLDivElement>(null);

  React.useEffect(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return; // stay visible
    gsap.registerPlugin(ScrollTrigger);
    const ctx = gsap.context(() => {
      // ONLY section 3 animates on scroll. Each category reveals as it is reached.
      gsap.utils.toArray<HTMLElement>(".lp-catch-card").forEach((el) => {
        gsap.from(el, {
          opacity: 0,
          y: 28,
          duration: 0.6,
          ease: "power2.out",
          scrollTrigger: { trigger: el, start: "top 85%", once: true },
        });
      });
    }, root);
    return () => ctx.revert();
  }, []);

  return (
    <div ref={root}>
      <SiteHeader />

      <main className="lp">
        {/* 1 — HERO: asymmetric, left-weighted. Not centered. */}
        <section className="lp-hero">
          <div className="lp-hero-copy">
            <LogoMark size={44} className="lp-hero-mark" />
            <p className="lp-eyebrow">Recruitment-fraud detection</p>
            <h1 className="lp-hero-title">Is this job real?</h1>
            <p className="lp-hero-sub">
              Paste a job posting or a recruiter&apos;s message. In under a second you&apos;ll see
              whether it&apos;s likely a scam — and exactly what gave it away.
            </p>
          </div>
          <div className="lp-hero-boxwrap">
            <HeroPaste />
          </div>
        </section>

        {/* 2 — HOW IT WORKS: three honest, ordered steps. */}
        <section className="lp-section" id="how-it-works">
          <p className="lp-kicker">How it works</p>
          <ol className="lp-steps">
            <li className="lp-step">
              <span className="lp-step-num">1</span>
              <div className="lp-step-body">
                <h3>Paste the posting</h3>
                <p>
                  A job ad or a recruiter&apos;s message, in any format. It&apos;s analysed the moment
                  you submit; nothing is stored against your name unless you sign in.
                </p>
              </div>
            </li>
            <li className="lp-step">
              <span className="lp-step-num">2</span>
              <div className="lp-step-body">
                <h3>The model and the rules read it together</h3>
                <p>
                  A calibrated logistic-regression model over word and character tf-idf scores the
                  language, while an Aho–Corasick pass matches known scam phrases, a Levenshtein check
                  catches look-alike domains, and a salary check flags pay that doesn&apos;t add up —
                  all in one request.
                </p>
              </div>
            </li>
            <li className="lp-step">
              <span className="lp-step-num">3</span>
              <div className="lp-step-body">
                <h3>You get a verdict, with its reasons</h3>
                <p>
                  A hedged “Likely scam” with a calibrated confidence, the exact phrases that
                  triggered it, each feature&apos;s weight in the score, and the confirmed scams it
                  most resembles.
                </p>
              </div>
            </li>
          </ol>
        </section>

        {/* 3 — WHAT IT CATCHES: the one scroll reveal on the page. */}
        <section className="lp-section">
          <p className="lp-kicker">What it catches</p>
          <div className="lp-catch">
            {CATCHES.map((c) => (
              <div className="lp-catch-card surface-card" key={c.key}>
                <CatchIcon>{c.icon}</CatchIcon>
                <h3>{c.title}</h3>
                <p>{c.body}</p>
              </div>
            ))}
          </div>
        </section>

        {/* 4 — SEE IT WORK: a cropped real verdict. Proof, not a promise. */}
        <section className="lp-section">
          <p className="lp-kicker">See it work</p>
          <figure className="lp-proof">
            <img
              className="lp-proof-img"
              src="/verdict-sample.png"
              alt="A Scam Shield verdict: a Likely-scam banner at 99.5% confidence, the posting with flagged phrases highlighted, and the ranked feature contributions with their coefficient-times-tf-idf weights."
              width={1024}
              height={900}
              loading="lazy"
            />
            <figcaption className="lp-proof-cap">
              A verdict on the canonical example posting — the flagged phrases and each feature&apos;s
              contribution to the score (coefficient × tf-idf). Paste your own and the analyzer
              computes the same for it.
            </figcaption>
          </figure>
        </section>

        {/* 5 — HONEST LIMITS: required, from the model card. The differentiator. */}
        <section className="lp-section lp-limits">
          <p className="lp-kicker">Honest limits</p>
          <p className="lp-limits-text">
            It can be evaded by rewording, and it reads language patterns — not a company registry. It
            can tell you a posting resembles known fraud; it can never confirm that a company is real,
            or that a job is safe. That is exactly why the verdict is always{" "}
            <em>“Likely scam,”</em> with a confidence, and never <em>“This is a scam.”</em>
          </p>
        </section>

        {/* 6 — CTA */}
        <section className="lp-section lp-cta">
          <h2 className="lp-cta-title">Check a posting</h2>
          <p className="lp-lead">
            Paste a job ad or a recruiter&apos;s message and see what the model finds.
          </p>
          <div className="lp-cta-actions">
            <Link className="ss-btn ss-btn-primary" href="/analyze">
              Check a posting
            </Link>
            <Link className="ss-btn ss-btn-ghost" href="/login">
              Sign in for history
            </Link>
          </div>
        </section>
      </main>

      <SiteFooter />
    </div>
  );
}
