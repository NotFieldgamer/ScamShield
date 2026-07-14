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

- **512 MB RAM, 0.1 CPU.** The Dockerfile sets `-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k`
  so the JVM fits inside 512 MB. Do not remove these — without a heap cap the JVM sizes its heap to
  the host and OOM-crashes on the first request.
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

## Point the frontend at the API

On Vercel, set `NEXT_PUBLIC_API_BASE_URL` to the Render service URL
(`https://<your-service>.onrender.com`) and redeploy. Make sure `CORS_ALLOWED_ORIGIN` on Render
exactly matches the Vercel origin, or browser calls are rejected.

## Updating

Push to `main`. Render auto-deploys on push (the blueprint enables it by default). Config-only
changes to `render.yaml` are picked up on the next blueprint sync.
