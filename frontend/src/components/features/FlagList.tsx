import { type AnalysisResponse, signed } from "@/lib/api";
import { cn } from "@/lib/utils";

/** The ranked model explanation: each term's coefficient×tfidf contribution, in log-odds. */
export function FlagList({ data }: { data: AnalysisResponse }) {
  const flags = data.topContributions;
  if (flags.length === 0) {
    return <p className="text-muted">No single term contributed strongly to this score.</p>;
  }
  const max = Math.max(...flags.map((f) => Math.abs(f.contribution)), 1e-9);

  return (
    <>
      <div className="az-flags">
        {flags.map((f, i) => {
          const positive = f.contribution >= 0;
          const width = `${Math.max(4, (Math.abs(f.contribution) / max) * 100)}%`;
          return (
            <div className="az-flag-row" key={`${f.feature}-${i}`}>
              <span
                className={cn("az-flag-bar", positive ? "az-flag-bar-pos" : "az-flag-bar-neg")}
                style={{ width }}
                aria-hidden="true"
              />
              <span className="az-flag-main">
                <span className="az-flag-term">&ldquo;{f.feature}&rdquo;</span>
                <span className="az-flag-kind">{f.charNgram ? "char n-gram" : "term"}</span>
              </span>
              <span className={cn("az-flag-value", positive ? "az-flag-pos" : "az-flag-neg")}>
                {signed(f.contribution)}
              </span>
            </div>
          );
        })}
      </div>
      <p className="az-flags-caption">
        Each value is that feature&apos;s contribution to the risk score in log-odds (coefficient ×
        tf-idf). Positive pushes toward scam, negative toward legitimate.
      </p>
    </>
  );
}
