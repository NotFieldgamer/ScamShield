# Deploying Scam Shield

The API runs on **Render** (Docker web service, free tier) and the frontend on **Vercel**. The two
managed data stores (Postgres + pgvector, Redis) run on their own free tiers.

## Architecture

```
Vercel (Next.js frontend)  ──HTTPS──►  Render (Spring Boot API, Docker)
                                            ├─► Postgres + pgvector  (Neon / Supabase)
                                            └─► Redis                (Upstash, optional)
```

The API image is built from the repo-root [`Dockerfile`](../Dockerfile) with the repo root as the
build context. Render reads [`render.yaml`](../render.yaml) to provision the service.

## Free-tier notes (Render)

- **512 MB RAM, 0.1 CPU.** The Dockerfile sets
  `-XX:MaxRAMPercentage=45 -XX:+UseSerialGC -Xss512k -XX:+ExitOnOutOfMemoryError` so the JVM fits
  inside 512 MB. Do not remove these — without a heap cap the JVM sizes its heap to the host and
  OOM-crashes on the first request. The heap is only part of the budget: ONNX Runtime holds the
  ~90 MB MiniLM model in **native** memory, which is why it is loaded from a file path and never
  read into a `byte[]` (that needs 90 MB of heap and briefly holds the model twice).
- **Sleeps after 15 minutes idle.** The first request after a sleep pays a cold start (JVM boot +
  model load). This is expected on the free tier.
- **No credit card required.**

## Prerequisites

1. A **Postgres database with pgvector** (Neon or Supabase free tier). Note its JDBC URL, username,
   and password. Managed hosts need `?sslmode=require` on the URL.
2. A **Redis instance** (Upstash free tier) — optional; rate limiting fails open without it.
3. A public **URL for the MiniLM model** (`minilm.onnx`, ~90 MB) that the build can `curl`. The
   model is not committed; host it as a GitHub release asset or any static URL.
4. The frontend deployed on Vercel (or its origin known), for CORS.

## Deploy the API to Render

1. **Connect the repo.** In the Render dashboard, choose **New → Blueprint** and select this GitHub
   repository. Render finds [`render.yaml`](../render.yaml) at the repo root and proposes the
   `scam-shield-api` web service (Docker environment, health check `/actuator/health`).

2. **Set the secret env vars.** Every var in `render.yaml` is declared with `sync: false`, so Render
   prompts for a value on first deploy (nothing sensitive lives in the repo). Fill in:

   | Variable | Value |
   | --- | --- |
   | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://…/db?sslmode=require` |
   | `SPRING_DATASOURCE_USERNAME` | your Postgres user |
   | `SPRING_DATASOURCE_PASSWORD` | your Postgres password |
   | `SPRING_DATA_REDIS_URL` | `rediss://default:…@….upstash.io:6379` (optional) |
   | `JWT_SECRET` | ≥ 32-byte random key — `openssl rand -base64 48` |
   | `CORS_ALLOWED_ORIGIN` | your Vercel origin, e.g. `https://scam-shield.vercel.app` |
   | `EMBEDDING_MODEL_URL` | public URL of `minilm.onnx` (used at **build** time) |

   > `PORT` is injected by Render automatically — do **not** set it. The app binds `${PORT:8080}`.

3. **Deploy.** Apply the blueprint. Render builds the Docker image (the build `curl`s the model from
   `EMBEDDING_MODEL_URL`), starts the container, and waits for `/actuator/health` to return `200`.
   Flyway applies the schema on first boot.

4. **Seed the confirmed-scam corpus** (for the "similar scams" panel), once, against your Postgres:
   ```bash
   psql "$SPRING_DATASOURCE_URL" < ml/out/known_scams_seed.sql
   ```
   To populate `/model` with real metrics, also load `validation_predictions_seed.sql` (produced by
   `ml/notebooks/02`) after Flyway `V4`. Until then `/model` shows an honest "predictions not
   loaded" state.

5. **Verify:** `curl https://<your-service>.onrender.com/actuator/health` → `{"status":"UP"}`.

## Deploy the frontend to Vercel

Deploy the API first: step 2 needs its URL.

1. **Import the repo.** Vercel dashboard → **Add New → Project** → pick this repository.

2. **Set Root Directory to `frontend`.** This is a monorepo; Vercel defaults to the repo root, where
   there is no Next.js app. The framework preset then auto-detects as Next.js — leave the build and
   output settings alone. No `vercel.json` is needed.

3. **Set one environment variable**, before the first deploy:

   | Variable | Value |
   | --- | --- |
   | `NEXT_PUBLIC_API_BASE_URL` | `https://<your-service>.onrender.com` (no trailing slash, no path) |

   It does two jobs: direct API calls read it at runtime, and it is the destination of the
   `/api/v1/auth/*` proxy rewrite in [`next.config.mjs`](../frontend/next.config.mjs). **It is
   inlined at build time** — after changing it you must **redeploy**, not just restart.

4. **Deploy**, then copy the production URL.

5. **Set `CORS_ALLOWED_ORIGIN` on Render** to that exact origin (`https://<project>.vercel.app`, no
   trailing slash). The name is **singular**; `CORS_ALLOWED_ORIGINS` is silently ignored and the app
   falls back to `http://localhost:3000`, which rejects every browser call.

Only the production domain works. The backend allows exactly one origin, and every Vercel preview
deployment gets a fresh URL, so previews cannot call the API.

### Why auth is proxied

The refresh cookie is `SameSite=Strict`, and `vercel.app` and `onrender.com` are different sites, so
a cookie set by the API directly would never be sent back — sessions would die at the 15-minute
access-token expiry. The frontend therefore routes `/api/v1/auth/*` through its own origin, making
the cookie first-party. The proxy is deliberately limited to the auth routes: proxying the analysis
endpoint would replace the caller's IP with Vercel's and collapse the per-IP rate limit into a
single shared bucket.

## Updating

Push to `main`. Render auto-deploys on push (the blueprint enables it by default). Config-only
changes to `render.yaml` are picked up on the next blueprint sync.
