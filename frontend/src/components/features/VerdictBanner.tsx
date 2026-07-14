import { type AnalysisResponse, confidence, pct } from "@/lib/api";
import { cn } from "@/lib/utils";

const LABELS: Record<AnalysisResponse["label"], { text: string; tone: string; sub: string; cap: string }> = {
  // Never the words "this is a scam" — always hedged.
  LIKELY_SCAM: {
    text: "Likely scam",
    tone: "az-scam",
    sub: "We'd steer clear. Here's what gave it away.",
    cap: "confidence",
  },
  LIKELY_LEGITIMATE: {
    text: "Looks legitimate",
    tone: "az-legit",
    sub: "Nothing here matched our fraud signals.",
    cap: "confidence",
  },
  UNCERTAIN: {
    text: "Uncertain",
    tone: "az-uncertain",
    sub: "The signals are mixed — weigh the flags below yourself.",
    cap: "scam probability",
  },
};

function Icon({ label }: { label: AnalysisResponse["label"] }) {
  const common = {
    width: 40,
    height: 40,
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 1.6,
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
    "aria-hidden": true,
  };
  const shield = <path d="M12 3l7 3v5c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6l7-3z" />;
  if (label === "LIKELY_SCAM")
    return (
      <svg {...common} className="az-scam">
        {shield}
        <path d="M12 8v4M12 15h.01" />
      </svg>
    );
  if (label === "LIKELY_LEGITIMATE")
    return (
      <svg {...common} className="az-legit">
        {shield}
        <path d="M9 12l2 2 4-4" />
      </svg>
    );
  return (
    <svg {...common} className="az-uncertain">
      {shield}
      <path d="M9.5 10a2.5 2.5 0 013.5-2.3c1 .5 1.5 1.6 1 2.8-.4.9-1.5 1.2-2 2M12 15h.01" />
    </svg>
  );
}

export function VerdictBanner({ data }: { data: AnalysisResponse }) {
  const meta = LABELS[data.label];
  const bannerTone =
    data.label === "LIKELY_SCAM"
      ? "az-banner-scam"
      : data.label === "LIKELY_LEGITIMATE"
        ? "az-banner-legit"
        : "az-banner-uncertain";
  return (
    <div className={cn("az-banner", bannerTone)}>
      <div className="az-banner-left">
        <span className="az-banner-icon">
          <Icon label={data.label} />
        </span>
        <div>
          <p className={cn("az-verdict-label", meta.tone)}>{meta.text}</p>
          <p className="az-verdict-sub">{meta.sub}</p>
        </div>
      </div>
      <div className="az-confidence">
        <span className={cn("az-confidence-num", meta.tone)}>{pct(confidence(data), 1)}</span>
        <span className="az-confidence-cap">{meta.cap}</span>
      </div>
    </div>
  );
}
