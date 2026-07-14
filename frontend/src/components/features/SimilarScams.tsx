import { type SimilarScam, pct } from "@/lib/api";

/** The three nearest confirmed scams by pgvector cosine similarity. */
export function SimilarScams({ items }: { items: SimilarScam[] }) {
  if (items.length === 0) {
    return <p className="text-muted">No close matches in the confirmed-scam corpus.</p>;
  }
  return (
    <div className="az-similar">
      {items.map((s) => (
        <div className="az-similar-row" key={s.id}>
          <div>
            <div className="az-similar-pct">{pct(s.similarity)}</div>
            <span className="az-similar-cap">match</span>
          </div>
          <div>
            <p className="az-similar-snippet">{s.textSnippet}</p>
            <span className="az-similar-source">source: {s.source}</span>
          </div>
        </div>
      ))}
    </div>
  );
}
