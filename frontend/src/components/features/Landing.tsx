"use client";

import * as React from "react";
import Link from "next/link";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { SiteNav } from "@/components/features/SiteNav";
import { Analyzer } from "@/components/features/Analyzer";

/**
 * The marketing page. The hero is a real, working analyzer — not a screenshot of one. Scroll
 * reveals are the only GSAP on the site; they degrade to plain visible content under
 * prefers-reduced-motion (and if JS never runs, since the sections are visible by default).
 */
export function Landing() {
  const root = React.useRef<HTMLDivElement>(null);

  React.useEffect(() => {
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduce) {
      return; // sections stay visible; no motion
    }
    gsap.registerPlugin(ScrollTrigger);
    const ctx = gsap.context(() => {
      gsap.utils.toArray<HTMLElement>(".lp-reveal").forEach((el) => {
        gsap.from(el, {
          opacity: 0,
          y: 40,
          duration: 0.7,
          ease: "power2.out",
          scrollTrigger: { trigger: el, start: "top 82%", once: true },
        });
      });
    }, root);
    return () => ctx.revert();
  }, []);

  return (
    <div className="lp" ref={root}>
      <SiteNav />

      <section className="lp-hero">
        <p className="lp-eyebrow">Online recruitment fraud, caught in under a second</p>
        <div className="lp-hero-analyzer">
          <Analyzer />
        </div>
        <p className="lp-scrollcue">Scroll to see how it works ↓</p>
      </section>

      <section className="lp-section lp-reveal">
        <h2>It reads a posting the way a forensic examiner would</h2>
        <p className="lp-lead">
          One paste runs a nine-step pipeline — none of it a black box. Every signal below is a real
          computation you can trace, and the flags you see are the exact features that moved the
          score.
        </p>
        <div className="lp-grid">
          <div className="lp-card">
            <span className="lp-tag">Aho–Corasick</span>
            <h3>Known scam phrases</h3>
            <p>
              Thousands of fraud phrases matched in a single pass of the text — &quot;processing
              fee&quot;, &quot;send your bank details&quot;, &quot;guaranteed income&quot;.
            </p>
          </div>
          <div className="lp-card">
            <span className="lp-tag">Levenshtein</span>
            <h3>Look-alike domains</h3>
            <p>
              A recruiter linking <span className="lp-mono">linkedln.com</span> instead of{" "}
              <span className="lp-mono">linkedin.com</span> is one edit away — and one red flag.
            </p>
          </div>
          <div className="lp-card">
            <span className="lp-tag">Calibrated logistic regression</span>
            <h3>A score you can explain</h3>
            <p>
              A linear model over word + character TF-IDF. Each flag&apos;s weight is exactly
              coefficient × tfidf — a true contribution, not a guess about a black box.
            </p>
          </div>
          <div className="lp-card">
            <span className="lp-tag">Ridge regression</span>
            <h3>Pay that doesn&apos;t add up</h3>
            <p>
              &quot;Data entry, $5,000 a week&quot; is caught by arithmetic: a salary more than three
              standard deviations above the going rate for the role.
            </p>
          </div>
          <div className="lp-card">
            <span className="lp-tag">pgvector · MiniLM</span>
            <h3>Scams it has seen before</h3>
            <p>
              The posting is embedded and matched against confirmed frauds by cosine similarity — so
              you see the three it most resembles.
            </p>
          </div>
          <div className="lp-card">
            <span className="lp-tag">Union-Find</span>
            <h3>Reposts, clustered</h3>
            <p>
              Near-identical postings are grouped into campaigns — the same scam under a dozen
              company names.
            </p>
          </div>
        </div>
      </section>

      <section className="lp-section lp-reveal">
        <h2>Why you can trust the number</h2>
        <p className="lp-quote">
          A model that always says &quot;legitimate&quot; is <b>95.2% accurate</b> on this data — and
          catches <b>zero</b> scams.
        </p>
        <p className="lp-lead">
          That is why we never headline accuracy. Only 4.8% of the postings are fraudulent, so the
          honest questions are: of the jobs we flag, how many are really scams (precision), and of
          all the scams, how many do we catch (recall)? The probabilities are calibrated — when it
          says 87%, roughly 87% of such postings really are scams. Move the threshold yourself and
          watch the tradeoff between blocking real jobs and letting scams through.
        </p>
        <div className="lp-actions">
          <Link className="ss-btn ss-btn-primary" href="/model">
            See the model&apos;s real performance
          </Link>
          <Link className="ss-btn ss-btn-ghost" href="/trends">
            What&apos;s rising this month
          </Link>
        </div>
      </section>

      <section className="lp-section lp-reveal">
        <h2>It never says &quot;this is a scam&quot;</h2>
        <p className="lp-lead">
          The verdict is always &quot;Likely scam&quot;, with a confidence — never a flat assertion.
          Branding a real employer a fraudster is a defamation risk we won&apos;t take, and a
          probability is the honest thing to show. The product protects job seekers without
          pretending to be certain.
        </p>
        <div className="lp-actions">
          <Link className="ss-btn ss-btn-primary" href="/analyze">
            Analyze a posting
          </Link>
          <Link className="ss-btn ss-btn-ghost" href="/campaigns">
            Browse scam campaigns
          </Link>
        </div>
      </section>

      <footer className="lp-footer">
        <div className="lp-footer-links">
          <Link href="/analyze">Analyze</Link>
          <Link href="/model">Model</Link>
          <Link href="/trends">Trends</Link>
          <Link href="/campaigns">Campaigns</Link>
          <Link href="/login">Sign in</Link>
        </div>
        <p style={{ margin: 0 }}>
          Java · Spring Boot · ONNX Runtime · PostgreSQL + pgvector · Redis · Next.js. Every number on
          screen traces to a computation. We accept pasted text only — we never scrape.
        </p>
      </footer>
    </div>
  );
}
