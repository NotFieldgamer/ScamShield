import path from "node:path";
import { fileURLToPath } from "node:url";

const dir = path.dirname(fileURLToPath(import.meta.url));

// Trailing slash would produce "//api/..." in the rewrite destination.
const apiOrigin = (process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080").replace(/\/+$/, "");

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Resolve the "@/..." alias in webpack directly, rather than relying on the tsconfig
  // paths handoff (which is brittle with the installed TypeScript version).
  webpack: (config) => {
    config.resolve.alias = {
      ...config.resolve.alias,
      "@": path.resolve(dir, "src"),
    };
    return config;
  },
  /**
   * Proxy the auth endpoints — and only those — through this origin.
   *
   * The refresh token rides in a SameSite=Strict cookie. In production the frontend and the API
   * sit on different sites (vercel.app and onrender.com), and a browser never returns a Strict
   * cookie across sites, so a cookie set by the API directly would be unusable. Routed through
   * here, the browser sees it come from our own origin and treats it as first-party.
   *
   * Deliberately narrow: every other call goes straight to the API carrying a bearer header,
   * which needs no cookie. Proxying those would replace the caller's IP with this server's and
   * collapse the analysis endpoint's per-IP rate limit into one shared bucket.
   */
  async rewrites() {
    return [
      { source: "/api/v1/auth/:path*", destination: `${apiOrigin}/api/v1/auth/:path*` },
    ];
  },
};

export default nextConfig;
