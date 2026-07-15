import { SiteHeader } from "@/components/frame/SiteHeader";
import { SiteFooter } from "@/components/frame/SiteFooter";
import { ThemeToggle } from "@/components/frame/ThemeToggle";
import { Logo } from "@/components/brand/Logo";
import { LogoMark } from "@/components/brand/LogoMark";

type Theme = "dark" | "light";

const RAW_SWATCHES = [
  { label: "pitch-800", value: "var(--pitch-800)" },
  { label: "umber-700", value: "var(--umber-700)" },
  { label: "bone-100", value: "var(--bone-100)" },
  { label: "vermillion", value: "var(--vermillion-500)" },
  { label: "saffron", value: "var(--saffron-500)" },
  { label: "verdigris", value: "var(--verdigris-500)" },
];

const SEMANTIC_SWATCHES = [
  { label: "accent", value: "var(--accent)" },
  { label: "danger", value: "var(--signal-danger)" },
  { label: "caution", value: "var(--signal-caution)" },
  { label: "verified", value: "var(--signal-verified)" },
  { label: "raised", value: "var(--surface-raised)" },
  { label: "text", value: "var(--text)" },
];

function Swatches({ items }: { items: { label: string; value: string }[] }) {
  return (
    <div className="ks-swatches">
      {items.map((s) => (
        <div key={s.label} className="ks-swatch">
          <div className="ks-swatch__chip" style={{ background: s.value }} />
          <div className="ks-swatch__name">{s.label}</div>
        </div>
      ))}
    </div>
  );
}

/** Every frame element, rendered inside a forced-theme column so its tokens resolve to that theme. */
function ThemeColumn({ theme }: { theme: Theme }) {
  return (
    <section data-theme={theme} className="ks-col">
      <p className="ks-eyebrow">{theme} theme</p>

      <div className="ks-section">
        <p className="ks-section__label">Logo mark + wordmark</p>
        <div className="ks-row" style={{ color: "var(--accent)" }}>
          <LogoMark size={24} title="Verity (24px)" />
          <LogoMark size={32} title="Verity (32px)" />
          <LogoMark size={48} title="Verity (48px)" />
        </div>
        <div className="ks-row" style={{ marginTop: "0.85rem" }}>
          <Logo />
        </div>
      </div>

      <div className="ks-section">
        <p className="ks-section__label">Header — rest, then scrolled</p>
        <div className="surface-card surface-card--sm" style={{ overflow: "hidden" }}>
          <SiteHeader sticky={false} />
          <div style={{ borderTop: "1px solid var(--hairline)" }} />
          <SiteHeader sticky={false} forceScrolled />
        </div>
      </div>

      <div className="ks-section">
        <p className="ks-section__label">Theme toggle (controls the whole page)</p>
        <div className="ks-row">
          <ThemeToggle />
        </div>
      </div>

      <div className="ks-section">
        <p className="ks-section__label">Depth — content-layer cards</p>
        <div className="ks-stack">
          <div className="surface-card ks-card-demo">
            <h4>Cast shadow, top-edge highlight</h4>
            <p>
              A solid card on the ambient plane. It lifts with a shadow and catches a 1px highlight
              along its top edge — two planes only, no glass.
            </p>
          </div>
          <div className="surface-card ks-card-demo">
            <h4>Same treatment, tighter radius</h4>
            <p>The card is the single depth primitive the frame is built from.</p>
          </div>
        </div>
      </div>

      <div className="ks-section">
        <p className="ks-section__label">Palette — raw ramp</p>
        <Swatches items={RAW_SWATCHES} />
      </div>

      <div className="ks-section">
        <p className="ks-section__label">Palette — semantic</p>
        <Swatches items={SEMANTIC_SWATCHES} />
      </div>

      <div className="ks-section">
        <p className="ks-section__label">Type — Redaction / Public Sans / Commit Mono</p>
        <p className="ks-type-display">Likely scam</p>
        <p className="ks-type-body">
          The verdict is always hedged, with a confidence — never a flat assertion of fraud.
        </p>
        <p className="ks-type-mono">87.4% · z=4.2 · +0.41</p>
      </div>

      <SiteFooter />
    </section>
  );
}

export default function KitchenSinkPage() {
  return (
    <>
      {/* Live, sticky page chrome — scroll the page to see it gain blur + a hairline border. */}
      <SiteHeader />

      <main className="ks-page">
        <h1 className="ks-type-display" style={{ fontSize: "var(--text-2xl)" }}>
          Kitchen sink — the frame
        </h1>
        <p className="ks-lede">
          The redesign foundations: logo, scroll-aware header, footer, theme toggle, and the
          content-layer card, each rendered in both themes at once. The header at the top of this
          page is live — scroll to see it gain a backdrop blur and a hairline border. Every control
          shows a vermillion focus ring on keyboard focus.
        </p>

        <div className="ks-cols">
          <ThemeColumn theme="dark" />
          <ThemeColumn theme="light" />
        </div>
      </main>

      <SiteFooter />
    </>
  );
}
