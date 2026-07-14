/**
 * The right panel before submit — ambient content that gives the empty state a reason to exist:
 * a one-line description of how a verdict reads, one illustrative flag (clearly an example), and
 * the training-corpus size. None of this is per-request data; the sample flag is labelled as an
 * example so it can never be mistaken for a real score.
 */
export function AmbientPanel() {
  return (
    <div className="az-panel-inner az-ambient">
      <p className="az-eyebrow">Before you paste</p>
      <p className="az-ambient-lede">
        Every verdict shows its work — the exact phrases that triggered it, each feature&apos;s
        weight in the score, and the nearest confirmed scams.
      </p>

      <div className="az-ambient-flag" aria-hidden="true">
        <span className="az-ambient-flag-term">&ldquo;processing fee&rdquo;</span>
        <span className="az-flag-kind">term</span>
        <span className="az-ambient-flag-val">+0.41</span>
      </div>
      <p className="az-ambient-caption">
        An example of a single flag. Real values come only from the model.
      </p>

      <div className="az-stat">
        <div className="az-stat-item">
          <span className="az-stat-num">17,880</span>
          <span className="az-stat-cap">real job ads</span>
        </div>
        <div className="az-stat-item">
          <span className="az-stat-num">866</span>
          <span className="az-stat-cap">confirmed scams</span>
        </div>
      </div>
      <p className="az-ambient-caption">The model&apos;s training corpus (EMSCAD) — 4.84% fraud.</p>
    </div>
  );
}
