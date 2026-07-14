"use client";

import * as React from "react";
import Link from "next/link";
import { AnimatePresence, MotionConfig, motion, useReducedMotion } from "framer-motion";

/* ---------------------------------------------------------------------------
 * /dashboard — a hub of action cards that expand IN PLACE into a detail panel
 * using Framer Motion shared layout. Each grid card and the expanded panel
 * share a layoutId, so the card appears to grow into the panel and shrink back.
 * The panel is absolutely positioned over a fixed-height stage, so opening a
 * card never grows the document or scrolls the page.
 * ------------------------------------------------------------------------- */

type CardId = "analyze" | "bulk" | "trends" | "model" | "campaigns";

type Card = {
  id: CardId;
  title: string;
  summary: string;
  icon: React.ReactNode;
  detail: React.ReactNode;
  cta: { href: string; label: string };
};

/* --- line icons, in the logo's visual language: geometric single-weight strokes --- */
const iconProps = {
  width: 26,
  height: 26,
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.8,
  strokeLinecap: "round" as const,
  strokeLinejoin: "round" as const,
  "aria-hidden": true,
};

const AnalyzeIcon = () => (
  <svg {...iconProps}>
    <circle cx="11" cy="11" r="6" />
    <line x1="15.5" y1="15.5" x2="20" y2="20" />
  </svg>
);

const BulkIcon = () => (
  <svg {...iconProps}>
    <rect x="4" y="5" width="16" height="14" rx="2" />
    <line x1="4" y1="10" x2="20" y2="10" />
    <line x1="4" y1="14.5" x2="20" y2="14.5" />
    <line x1="12" y1="10" x2="12" y2="19" />
  </svg>
);

const TrendsIcon = () => (
  <svg {...iconProps}>
    <polyline points="4 15 9 10 13 13 20 6" />
    <polyline points="15 6 20 6 20 11" />
  </svg>
);

const ModelIcon = () => (
  <svg {...iconProps}>
    <path d="M4 15a8 8 0 0 1 16 0" />
    <line x1="12" y1="15" x2="15.5" y2="10.5" />
    <circle cx="12" cy="15" r="1" />
  </svg>
);

const CampaignsIcon = () => (
  <svg {...iconProps}>
    <circle cx="12" cy="6" r="2.2" />
    <circle cx="5.5" cy="17" r="2.2" />
    <circle cx="18.5" cy="17" r="2.2" />
    <line x1="11" y1="7.8" x2="6.7" y2="15" />
    <line x1="13" y1="7.8" x2="17.3" y2="15" />
    <line x1="7.7" y1="17" x2="16.3" y2="17" />
  </svg>
);

const CloseIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <line x1="6" y1="6" x2="18" y2="18" />
    <line x1="18" y1="6" x2="6" y2="18" />
  </svg>
);

const CARDS: Card[] = [
  {
    id: "analyze",
    title: "Analyze a posting",
    summary: "Paste a job post or a recruiter message and get a verdict.",
    icon: <AnalyzeIcon />,
    detail: (
      <p className="dash-detail-body">
        Paste a job posting or a recruiter&apos;s message. The analyzer runs it through the full
        pipeline — phrase matching, the calibrated classifier, salary plausibility, and a search for
        similar confirmed scams — then shows the verdict with the exact signals behind it.
      </p>
    ),
    cta: { href: "/analyze", label: "Open the analyzer" },
  },
  {
    id: "bulk",
    title: "Bulk scan",
    summary: "Score a CSV of postings in one pass.",
    icon: <BulkIcon />,
    detail: (
      <>
        <p className="dash-detail-body">
          Bulk scanning runs every row through the same pipeline as a single paste, so a batch is
          scored exactly the way one posting would be.
        </p>
        <p className="dash-detail-note">
          The backend endpoint exists, but the upload screen isn&apos;t wired into the dashboard
          yet. For now, analyze postings one at a time.
        </p>
      </>
    ),
    cta: { href: "/analyze", label: "Analyze a single posting" },
  },
  {
    id: "trends",
    title: "Trends",
    summary: "Which scam patterns are rising.",
    icon: <TrendsIcon />,
    detail: (
      <p className="dash-detail-body">
        See which scam patterns are climbing, aggregated from the feature contributions already
        computed and stored for real verdicts. No invented deltas — every count traces to a figure
        we&apos;ve saved.
      </p>
    ),
    cta: { href: "/trends", label: "Open trends" },
  },
  {
    id: "model",
    title: "Model performance",
    summary: "PR curve, calibration, and the threshold slider.",
    icon: <ModelIcon />,
    detail: (
      <p className="dash-detail-body">
        Inspect the served model&apos;s precision, recall, and errors on held-out data. Move the
        threshold slider to watch the live tradeoff between blocking real jobs and letting scams
        through.
      </p>
    ),
    cta: { href: "/model", label: "Open model performance" },
  },
  {
    id: "campaigns",
    title: "Campaigns",
    summary: "The same scam reposted under many names.",
    icon: <CampaignsIcon />,
    detail: (
      <p className="dash-detail-body">
        Clusters of the same scam reposted under different company names, grouped by how alike their
        text is. Follow a campaign to see every posting we&apos;ve linked to it.
      </p>
    ),
    cta: { href: "/campaigns", label: "Open campaigns" },
  },
];

const SPRING = { type: "spring" as const, stiffness: 400, damping: 34 };

export function Dashboard() {
  const [selected, setSelected] = React.useState<CardId | null>(null);
  const [settled, setSettled] = React.useState(false);
  const reduce = useReducedMotion();

  const closeRef = React.useRef<HTMLButtonElement>(null);
  const panelRef = React.useRef<HTMLDivElement>(null);
  const cardRefs = React.useRef<Record<string, HTMLButtonElement | null>>({});

  const active = CARDS.find((c) => c.id === selected) ?? null;

  function open(id: CardId) {
    setSettled(false);
    setSelected(id);
  }

  function close() {
    const from = selected;
    setSettled(false);
    setSelected(null);
    // Return focus to the card that opened the panel; it is always mounted in the grid.
    if (from) {
      requestAnimationFrame(() => cardRefs.current[from]?.focus());
    }
  }

  // Move focus into the dialog when it opens.
  React.useEffect(() => {
    if (selected) closeRef.current?.focus();
  }, [selected]);

  // Trap Tab within the open dialog so focus can't escape to controls hidden behind the scrim.
  // (The grid is also set `inert` while open — belt and suspenders for AT.)
  function onPanelKeyDown(e: React.KeyboardEvent) {
    if (e.key !== "Tab" || !panelRef.current) return;
    const focusables = panelRef.current.querySelectorAll<HTMLElement>(
      'button:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])',
    );
    if (focusables.length === 0) return;
    const first = focusables[0];
    const last = focusables[focusables.length - 1];
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault();
      first.focus();
    }
  }

  // Under reduced motion the morph is instant, so settle immediately (onLayoutAnimationComplete
  // may not fire when layout animations are disabled). Otherwise the panel's callback settles it.
  React.useEffect(() => {
    if (selected && reduce) setSettled(true);
  }, [selected, reduce]);

  // Escape closes.
  React.useEffect(() => {
    if (!selected) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") {
        e.stopPropagation();
        close();
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selected]);

  return (
    <div className="dash-shell">
      <header className="dash-head">
        <p className="dash-eyebrow">Dashboard</p>
        <h1 className="dash-title">Where do you want to go?</h1>
        <p className="dash-lede">
          Everything Scam Shield can do, in one place. Open a card to see what it does, then jump in.
        </p>
      </header>

      <MotionConfig reducedMotion="user">
        <div className="dash-stage">
          <div className="dash-grid" inert={selected ? true : undefined}>
            {CARDS.map((card) => (
              <motion.button
                key={card.id}
                ref={(el) => {
                  cardRefs.current[card.id] = el;
                }}
                type="button"
                layoutId={`dash-${card.id}`}
                transition={SPRING}
                className="dash-card surface-card"
                aria-expanded={selected === card.id}
                onClick={() => open(card.id)}
              >
                <span className="dash-card-icon">{card.icon}</span>
                <h3>{card.title}</h3>
                <p>{card.summary}</p>
                <span className="dash-card-cue">Open</span>
              </motion.button>
            ))}
          </div>

          <AnimatePresence>
            {selected && active && (
              <>
                <motion.div
                  key="scrim"
                  className="dash-scrim"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  transition={{ duration: 0.2 }}
                  onClick={close}
                />
                <motion.div
                  key={`panel-${selected}`}
                  ref={panelRef}
                  layoutId={`dash-${selected}`}
                  transition={SPRING}
                  className="dash-expanded surface-card"
                  role="dialog"
                  aria-modal="true"
                  aria-label={active.title}
                  onKeyDown={onPanelKeyDown}
                  onLayoutAnimationComplete={() => setSettled(true)}
                >
                  <button
                    ref={closeRef}
                    type="button"
                    className="dash-expanded-close"
                    onClick={close}
                    aria-label="Close"
                  >
                    <CloseIcon />
                  </button>

                  <motion.div
                    className="dash-detail"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: settled ? 1 : 0 }}
                    transition={{ duration: 0.22, ease: "easeOut" }}
                  >
                    <div className="dash-detail-head">
                      <span className="dash-detail-icon">{active.icon}</span>
                      <h2>{active.title}</h2>
                    </div>
                    <p className="dash-detail-summary">{active.summary}</p>
                    {active.detail}
                    <div className="dash-cta-row">
                      <Link href={active.cta.href} className="dash-cta ss-btn ss-btn-primary">
                        {active.cta.label}
                      </Link>
                    </div>
                  </motion.div>
                </motion.div>
              </>
            )}
          </AnimatePresence>
        </div>
      </MotionConfig>
    </div>
  );
}
