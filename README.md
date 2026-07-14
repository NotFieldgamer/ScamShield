# Scam Shield

Paste a job posting or recruiter message and find out whether it's likely a scam — and see exactly
which signals gave it away. A Spring Boot API scores the text with an in-process ONNX model that
explains every verdict, and a Next.js frontend renders it. Verdicts are always hedged (_"Likely
scam — 87% confident"_), never a flat accusation.

- **Backend:** Java 21, Spring Boot 3, Postgres + pgvector, Redis, ONNX Runtime (no Python at request time)
- **Frontend:** Next.js 15, TypeScript, Tailwind, Radix primitives
- **Deploy:** Render (API) + Vercel (frontend). See [`docs/DEPLOY.md`](docs/DEPLOY.md).

---

## Prerequisites

- **Java 21**
- **Node 20+**
- **Docker** — runs Postgres + pgvector and Redis locally, and the backend tests need it (Testcontainers)
- **Git**
- **`minilm.onnx`** (~90 MB) — the MiniLM embedding model is **not** committed. Download it and place
  it at `backend/src/main/resources/models/minilm.onnx`, or set `EMBEDDING_MODEL_PATH` to its
  location. Without it the "similar scams" panel and the pipeline tests won't run.

## Clone

```bash
git clone <repo-url> ScamShield
cd ScamShield
```

## Environment variables

Copy the template and fill it in — `.env` is gitignored, never commit it:

```bash
cp .env.example .env
```

**Backend:**

| Variable                         | Required | Default (local)                               | What it is                                                                        |
| -------------------------------- | -------- | --------------------------------------------- | --------------------------------------------------------------------------------- |
| `JWT_SECRET`                     | **yes**  | _none — app won't start_                      | HMAC-SHA256 signing key, ≥ 32 bytes. Generate: `openssl rand -base64 48`          |
| `SPRING_DATASOURCE_URL`          | yes      | `jdbc:postgresql://localhost:5432/scamshield` | Postgres + pgvector JDBC URL (add `?sslmode=require` for managed hosts)           |
| `SPRING_DATASOURCE_USERNAME`     | yes      | `scamshield`                                  | Postgres user                                                                     |
| `SPRING_DATASOURCE_PASSWORD`     | yes      | `scamshield`                                  | Postgres password                                                                 |
| `CORS_ALLOWED_ORIGIN`            | yes      | `http://localhost:3000`                       | The one browser origin allowed to call the API                                    |
| `SPRING_DATA_REDIS_URL`          | no       | _(host/port below)_                           | Full Redis URL, e.g. Upstash `rediss://…`. Rate limiting fails open without Redis |
| `REDIS_HOST` / `REDIS_PORT`      | no       | `localhost` / `6379`                          | Local Redis when not using a URL                                                  |
| `PORT`                           | no       | `8080`                                        | Port the API binds. Render injects this automatically                             |
| `EMBEDDING_MODEL_PATH`           | no       | `/app/models/minilm.onnx`                     | Runtime path to `minilm.onnx`                                                     |
| `EMBEDDING_MODEL_URL`            | no       | _(unset)_                                     | **Build-time only** — URL the Docker build downloads MiniLM from                  |
| `JWT_ACCESS_TTL` / `REFRESH_TTL` | no       | `PT15M` / `P7D`                               | Token lifetimes (ISO-8601 durations)                                              |

**Frontend:**

| Variable                   | Default                 | What it is                  |
| -------------------------- | ----------------------- | --------------------------- |
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` | Base URL of the backend API |

## Run it locally

```bash
# 1. Data stores (Postgres + pgvector, Redis)
docker run -d --name ss-pg -e POSTGRES_USER=scamshield -e POSTGRES_PASSWORD=scamshield \
  -e POSTGRES_DB=scamshield -p 5432:5432 pgvector/pgvector:pg16
docker run -d --name ss-redis -p 6379:6379 redis:7-alpine

# 2. Backend (Flyway applies the schema on startup; binds ${PORT:8080})
JWT_SECRET=local-dev-only-jwt-secret-0123456789abcdef \
CORS_ALLOWED_ORIGIN=http://localhost:3000 \
  ./backend/gradlew -p backend bootRun
# → http://localhost:8080/actuator/health

# 3. Seed the confirmed-scam corpus (for the "similar scams" panel)
docker exec -i ss-pg psql -U scamshield -d scamshield < ml/out/known_scams_seed.sql

# 4. Frontend
npm --prefix frontend install
npm --prefix frontend run dev
# → http://localhost:3000
```

To populate the `/model` page with real EMSCAD test-split metrics, run `ml/notebooks/02` and load the
`validation_predictions_seed.sql` it produces (after Flyway `V4`). Until then `/model` honestly shows
a "predictions not loaded" state.

## Run the tests

```bash
./backend/gradlew -p backend test    # needs Docker (Testcontainers) + minilm.onnx present
```

## Deploy

API to **Render**, frontend to **Vercel**. Full steps in [`docs/DEPLOY.md`](docs/DEPLOY.md): connect
the GitHub repo on Render (it reads [`render.yaml`](render.yaml) and provisions the service), set the
env vars above as dashboard secrets, and deploy. Render injects `PORT` and health-checks
`/actuator/health`. Point the Vercel frontend at the API with `NEXT_PUBLIC_API_BASE_URL`.

---

## How it works

A single paste runs a nine-step pipeline: rate limit → normalize + content-hash (cache) →
Aho-Corasick scam-phrase scan → Levenshtein typosquat check → TF-IDF → ONNX logistic regression →
calibrated probability → contribution extraction (`coefficient × tfidf`, the exact terms the UI
shows) → salary plausibility → MiniLM embedding + pgvector search for similar confirmed scams →
persist + audit. The served model is linear on purpose: its per-feature contribution is exactly
`coefficient × tfidf`, so every flag shown is true, not a guess.

## Project layout

```
backend/    Spring Boot API (the mass of the project) — see backend/src/main/java/com/scamshield
frontend/   Next.js app
ml/         Offline Kaggle notebooks + model export
docs/       DEPLOY.md, MODEL_CARD.md, SECURITY.md
Dockerfile  Backend image (Render)
render.yaml Render blueprint
```

## Docs

- [`docs/DEPLOY.md`](docs/DEPLOY.md) — deploy the API to Render and the frontend to Vercel
- [`docs/MODEL_CARD.md`](docs/MODEL_CARD.md) — what the model does, measured performance, failure modes
- [`docs/SECURITY.md`](docs/SECURITY.md) — auth design and feedback-loop poisoning guards
- [`ml/README.md`](ml/README.md) — metrics table, calibration plot, reproduction steps
