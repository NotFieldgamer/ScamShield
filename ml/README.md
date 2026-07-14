# Scam Shield — ML pipeline (Phase 2)

Offline, reproducible training for the served fraud classifier, the salary-plausibility
model, and the embedding seed. Everything runs on **Kaggle**, free tier. The Java backend
consumes only the exported artifacts — no Python at request time.

Dataset: [`shivamb/real-or-fake-fake-jobposting-prediction`](https://www.kaggle.com/datasets/shivamb/real-or-fake-fake-jobposting-prediction)
— the Employment Scam Aegean Dataset (EMSCAD): **17,880 job ads, 866 fraudulent (4.84%),
18 columns** (`job_id` + label + 16 predictors), manually annotated, English, ~2012–2014.

---

## The imbalance trap — read first

The positive class is 4.84% of the data. A model that predicts "legitimate" for **every**
posting scores:

| Majority-class baseline | value |
| --- | --- |
| Accuracy | **95.16%** (`17,014 / 17,880`) |
| Fraud recall | **0.0000** — catches zero scams |
| PR-AUC (no-skill floor) | **0.0484** (= prevalence) |

**This is why accuracy is never reported as a headline metric in this project.** A 95%-accurate
model here can be completely useless. `01_eda.ipynb` prints this baseline explicitly, and
`02_train_classifier.ipynb` prints it *in the same table* as the real model scores below.

---

## Metrics

Reported on the **untouched 20% test split**. Primary metric: **PR-AUC**. The operating
threshold is chosen on a separate calibration split at **precision ≥ 0.90** (the product is
precision-first to avoid defaming real employers), then applied to the test set.

> The model rows below are **populated by running `02_train_classifier.ipynb`**, which prints
> this exact table and writes `metrics_table.md`. They are intentionally *not* hard-coded here:
> per the project's rule, every number on screen must trace to a computation you can point to.
> Paste the notebook's table between the markers below after your Kaggle run.

<!-- METRICS:START -->

| Model | PR-AUC | F1 | Recall@P≥0.90 | ROC-AUC | Threshold |
| --- | --- | --- | --- | --- | --- |
| Majority-class baseline | 0.0484 | 0.0000 | 0.0000 | 0.5000 | — |
| Calibrated LogReg (served) | _run 02_ | _run 02_ | _run 02_ | _run 02_ | _run 02_ |
| XGBoost (challenger) | _run 02_ | _run 02_ | _run 02_ | _run 02_ | _run 02_ |
| DistilBERT (challenger, optional) | _run 02_ | _run 02_ | _run 02_ | _run 02_ | _run 02_ |

<!-- METRICS:END -->

Also produced by `02`: the **confusion matrix** at the chosen threshold, a **calibration
curve** with the **Brier score** for the served model (`calibration_served.png`), and
`metrics.json`.

### How to read it like an interviewer

- **PR-AUC** is primary; the baseline's PR-AUC equals the prevalence (0.0484) — the honest floor.
- **Recall@precision≥0.90** is the number that matters for the product: at a precision high
  enough not to brand real employers as frauds, *how many scams do we still catch?* Expect it
  to be well under 1.0. That gap is the real tradeoff, shown live on the `/model` page.
- Challengers (XGBoost, DistilBERT) usually beat the served model on PR-AUC. They are **not
  served** because they cannot yield the exact, true `coef × tfidf` explanation the linear
  model gives in Java. We show their scores; we don't fabricate explanations for them.

---

## Models

**Served classifier (`02`, `04`).** Word (1–2 gram) + character (3–5 gram, `char_wb`) TF-IDF,
two blocks each L2-normalized independently, then concatenated → **logistic regression**,
`class_weight='balanced'` → **Platt/sigmoid calibration** (`cv='prefit'`, so one coefficient
vector survives for the contributions). Character n-grams survive obfuscation like
`e a r n   d a i l y`.

- **No SMOTE.** `class_weight='balanced'` handles the imbalance; SMOTE on high-dimensional
  sparse TF-IDF fabricates implausible documents. The test set is never resampled.
- **Dedup before splitting.** Exact-duplicate constructed texts are dropped first (EMSCAD
  reposts the same scam under many names); the notebook prints the count removed. This stops
  a reposted fraud from landing in both train and test and inflating the headline numbers.
- **Split before anything.** Stratified 80/20 train/test; a calibration slice is carved from
  train only. Threshold selection and calibration never see the test set.
- **Why linear, not XGBoost?** ONNX can't emit SHAP in Java. For this linear model, a feature's
  contribution to the score is exactly `coefficient × tfidf` — a **log-odds** term, computable
  and *true* in Java in microseconds. That is what the UI displays (labeled as log-odds, not
  as an additive share of the probability).

**Salary plausibility (`03`, `04`).** Ridge regression of `log1p(midpoint of salary_range)` on
title (word TF-IDF), required experience, required education, and location (country). Persists
the **residual σ** so Java computes `z = (log1p(promised) − predicted) / σ` and flags `z > 3`.
Caveat, stated up front: `salary_range` is populated in only ~16% of rows and has **no currency
and no pay period** (bare `"min-max"`, mixing hourly and annual) — a coarse outlier flag, not a
wage oracle.

**Embeddings (`04`).** The 866 confirmed frauds are embedded with `all-MiniLM-L6-v2` (384-d) and
written to `known_scams_seed.sql` for the pgvector "similar confirmed scams" panel.

---

## Artifacts (written to `/kaggle/working`)

| File | Consumed by | In git? |
| --- | --- | --- |
| `classifier.onnx` | Java inference (parity test) | **no** (`*.onnx` gitignored) |
| `vocab.json` | Java rebuilds the TF-IDF vector | yes |
| `coef.json` | Java contributions + calibrated probability | yes |
| `salary_params.json` | Java salary flag (categories + title IDF + Ridge coef + residual σ) | yes |
| `salary.onnx` | Java salary flag (optional bonus; export is best-effort) | **no** |
| `salary_meta.json` | residual σ, z-threshold | yes |
| `fixed_test_vector.json` | Java ONNX parity test | yes |
| `known_scams_seed.sql` | pgvector seed (load after `V1__init.sql`) | seed file (large) |
| `validation_predictions.csv` / `validation_predictions_seed.sql` | the `/model` threshold slider (load after `V4__phase7_model_and_reports.sql`) | seed file |
| `metrics.json`, `metrics_table.md`, `*.png` | this README / `/model` page | yes |

`classifier.onnx` is the **raw** logistic regression (skl2onnx has no `FrozenEstimator`
converter, so the calibrated wrapper falls back to the raw LR); Java applies the Platt `(a,b)`
from `coef.json` on top. The salary model is guaranteed loadable from `salary_params.json`;
`salary.onnx` is a bonus that may be skipped if skl2onnx rejects the string pipeline.

**Why the split (vocab in JSON, classifier in ONNX):** `skl2onnx` cannot convert a character
n-gram TF-IDF vectorizer (`analyzer='char_wb'` raises `NotImplementedError`; `char` is not
exact). So the vectorizer stays in Java (rebuilt from `vocab.json`) and only the classifier is
exported to ONNX. This is also why the ONNX parity test feeds the *exact* vector from
`fixed_test_vector.json` — it isolates ONNX-runtime parity from the Java TF-IDF reimplementation.

---

## Reproduction

Each notebook is **self-contained** and runs top-to-bottom in a fresh Kaggle session.

1. New Kaggle notebook → **Settings → Internet: On** (for `kagglehub` + model downloads).
   Add a **GPU** only if you want the DistilBERT challenger and faster embeddings.
2. Run in order:
   - `01_eda.ipynb` — shape check, majority-class baseline, missing-value profile, class plot.
   - `02_train_classifier.ipynb` — the served model + challengers, the metrics table, the
     calibration curve. Copy its printed table into the METRICS block above.
   - `03_train_salary.ipynb` — the salary model and residual σ.
   - `04_export_onnx.ipynb` — ONNX + JSON exports, the MiniLM seed SQL, and the **fixed test
     vector with its exact probability**.
3. Download `/kaggle/working`. Copy `classifier.onnx`, `salary.onnx`, `vocab.json` into
   `backend/src/main/resources/models/` (gitignored); keep `fixed_test_vector.json` for the
   Java parity test; load `known_scams_seed.sql` after the Flyway migration.

Seeds are fixed (`SEED = 42`); `04` re-fits the served model with the same split as `02`, so its
exported coefficients match the evaluated model.

---

## The Java parity contract

`04` prints and saves, for a fixed input string, the exact positive-class probability:

- `classifier.onnx` takes the **TF-IDF vector** as input. The Java test reconstructs the vector
  from `fixed_test_vector.json` (`nonzero_indices` / `nonzero_values`) and asserts the ONNX
  output equals `onnx_probability_pos` within **1e-6** — both run the same float32 ONNX runtime,
  so this bound is tight. (The notebook's own sklearn-vs-ONNX check uses 1e-4, because sklearn
  runs float64.)
- Java independently rebuilds the vector from `vocab.json` (a separate, looser-tolerance check),
  and computes the log-odds contributions and the calibrated probability from `coef.json`
  (`p = 1 / (1 + exp(a·f + b))`, `f = intercept + Σ coefᵢ·tfidfᵢ`).
- `served_calibrated_probability` is the end-to-end target for `FIXED_TEXT`.

---

## Known limitations (for `docs/MODEL_CARD.md`)

- **Contributions are log-odds, not probabilities.** `coef × tfidf` sums (with the intercept)
  to the logit; it does not add up to the displayed percentage. The UI must label it as such.
- **Char n-gram features are not human-readable phrases.** The served model's top drivers can be
  sub-word fragments; the UI should surface word-gram / matched-phrase evidence, not raw char grams.
- **`salary_range` is ~84% missing, no currency/period.** The salary flag has low coverage and
  is noisy by construction.
- **EMSCAD is English, formal job ads, ~2012–2014.** Expect a train/serve gap on pasted recruiter
  DMs and non-English text (known failure modes).
- **4.84% is a dataset artifact, not real-world prevalence.** Calibration is honest on this
  distribution but will not transfer perfectly to live traffic; monitor drift.
- **Retrieval corpus is only 866 vectors.** The "similar scams" panel needs a cosine-distance
  threshold, or it will always return three loosely-related frauds.
