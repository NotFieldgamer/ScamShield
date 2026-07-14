# Model card — Scam Shield fraud classifier

_Last reviewed: 2026-07-14._

This card describes the model that Scam Shield serves on the request path. It follows the spirit of
Google's Model Cards: what the model does, how well it does it, where it fails, and who it could
harm if it is wrong. If any claim here stops being true, fix the claim.

---

## What it does

Given the free text of a job posting or a recruiter's message, the served model outputs a
**calibrated probability that the posting is a recruitment scam**, plus the individual text
features that drove that probability. The product turns this into a hedged verdict —
_"Likely scam — 87% confident"_ — and never a flat assertion of fraud.

**Intended use.** A first-pass triage aid for job seekers evaluating a suspicious posting. It is
decision _support_, not a decision. A person should always read the flags and use judgement.

**Out of scope.** It is not an authority on whether a specific company is fraudulent, not a legal
determination, and not a substitute for reporting a scam to the relevant platform or authority. It
is trained on English-language, formal job ads and does not generalize to other languages.

---

## The served model

- **Architecture.** TF-IDF (word 1–2 grams + character 3–5 grams, each L2-normalized and
  concatenated) → **logistic regression** with `class_weight='balanced'` → **Platt/sigmoid
  calibration**. Character n-grams let it survive obfuscation like `e a r n   d a i l y`.
- **Why linear, when XGBoost scores higher?** For a linear model over TF-IDF, a feature's
  contribution to the score is exactly `coefficient × tfidf` — a log-odds term that is computable
  in Java in microseconds and is _actually true_. That is what the UI displays. XGBoost and a
  fine-tuned DistilBERT are trained as **challengers**; their scores are shown on the `/model`
  page, but they are not served because they cannot produce an explanation that is both exact and
  honest. We do not fabricate explanations for a black box.
- **Supporting signals (not the served classifier).** A Ridge regression flags implausible salary
  (`z > 3` above the fitted rate for the role); an Aho-Corasick pass matches a scam-phrase
  registry; a Levenshtein check flags look-alike domains; a MiniLM (`all-MiniLM-L6-v2`, 384-dim)
  embedding retrieves the nearest confirmed scams via pgvector.

Inference runs in-process via ONNX Runtime for Java — no Python at request time. A parity test
asserts the Java output equals the notebook's within `1e-6`.

---

## Training data

**EMSCAD** — the Employment Scam Aegean Dataset (`shivamb/real-or-fake-fake-jobposting-prediction`):
**17,880 job ads, 866 fraudulent (~4.84%)**, manually annotated by Workable staff, English,
roughly 2012–2014.

- The served model uses **only text columns** (title, company profile, description, requirements,
  benefits). Structured meta-features that are predictive in EMSCAD but unavailable or leaky at
  serve time (e.g. `has_company_logo`) are deliberately excluded to avoid train/serve skew.
- Exact-duplicate texts are dropped **before** the train/test split, so a reposted fraud cannot
  land in both and inflate the headline numbers. The split happens before any resampling; the test
  set is never resampled or calibrated on.

---

## Measured performance — and why accuracy is not reported

The positive class is 4.84% of the data. **A model that predicts "legitimate" for every posting
scores ~95% accuracy and catches zero scams.** That is why accuracy is never a headline metric
here.

| Majority-class baseline (test split) | value |
| --- | --- |
| Accuracy | **95.16%** |
| Fraud recall | **0.0000** |
| PR-AUC (no-skill floor) | **0.0484** (= prevalence) |

The served and challenger scores — PR-AUC (primary), F1, recall at precision ≥ 0.90, ROC-AUC, the
confusion matrix, and the calibration curve — are **produced by running `ml/notebooks/02` and are
reported in [`ml/README.md`](../ml/README.md)**. They are intentionally not hard-coded in prose:
per the project's rule, every number on screen must trace to a computation you can point to. The
live **`/model`** page recomputes precision, recall, false positives ("real jobs blocked") and
false negatives ("scams let through") from the model's stored held-out predictions as you move the
decision threshold. The operating threshold is chosen at **precision ≥ 0.90** — the product is
precision-first to avoid branding real employers as frauds.

**Calibration.** Probabilities are calibrated on a held-out fold, so "87% confident" means roughly
87% of such postings really are scams. An uncalibrated score is a number that looks like a
probability and isn't one.

---

## Known failure modes

These are real and expected. The product should never be trusted blindly in any of them.

- **Non-English postings.** EMSCAD is English. Scores on other languages are unreliable; the text
  features simply don't fire as intended.
- **Legitimate commission-only / sales roles.** Real commission-only sales and recruiting jobs use
  the same "unlimited earning", "be your own boss" language as scams. Expect false positives here —
  a genuine job flagged as likely scam.
- **Small companies with no company profile.** Fraudulent posts often omit a company profile, so
  the model learns to distrust its absence. A small or new legitimate employer with a thin posting
  can be penalized for looking like a scam, not for being one.
- **Recruiter DMs and pasted chat.** The model is trained on formal job ads; short, informal
  recruiter messages are out of distribution and score less reliably.
- **Character n-grams are not human-readable.** Some top drivers are sub-word fragments. The UI
  surfaces word-gram and matched-phrase evidence rather than raw character grams, but the score
  itself can lean on features a person wouldn't recognize.
- **Salary flag is sparse and noisy.** `salary_range` is populated in only ~16% of rows and carries
  no currency or pay period. It is a coarse outlier flag, not a wage oracle.
- **Prevalence is a dataset artifact.** 4.84% fraud is EMSCAD, not live traffic. Calibration is
  honest on this distribution but will drift on real data; monitor it.

---

## Who it could harm if it is wrong

- **A real employer, wrongly flagged.** Publicly branding a genuine company a fraudster is a
  defamation and reputational harm. **Mitigations:** the verdict is always hedged ("Likely scam,"
  with a confidence — never "This is a scam"); the operating point is chosen for high precision;
  the model card and `/model` page make the error rate visible; the product accepts pasted text and
  makes no company-level public accusation.
- **A job seeker, given false reassurance.** A missed scam ("Looks legitimate" on a real fraud) can
  lead someone to hand over money or personal data. **Mitigations:** the UI shows the flags and the
  confidence rather than a binary; recall at the chosen precision is shown honestly on `/model` so
  users understand scams _do_ slip through; the copy steers toward caution, not certainty.
- **Small and marginalized employers.** Businesses without a web presence, non-native English
  writers, and informal-sector roles are more likely to trip the model's learned proxies for
  "scam." This is a fairness concern, not just an accuracy one. **Mitigations:** documented here as
  a known failure mode; the community-report and moderation flow (below) exists to correct verdicts,
  and only an admin's decision — never an anonymous report — feeds retraining.

---

## Feedback, correction, and retraining

Users can dispute a verdict. The feedback loop is treated as an attack surface (a scammer will
report their own scam as legitimate), so: reports require an account at least 7 days old, a label
changes only on agreement between **two independent reporters** or an **admin's decision**, and
**retraining consumes only reports an admin has confirmed.** Details in
[`docs/SECURITY.md`](SECURITY.md). This is how a wrong verdict gets corrected without letting the
correction channel be poisoned.

---

## Reproducing the numbers

Everything above traces to `ml/notebooks/` (offline, Kaggle, seeded at 42) and the Java parity
tests in `backend/src/test`. See [`ml/README.md`](../ml/README.md) for the metrics table, the
calibration plot, and step-by-step reproduction.
