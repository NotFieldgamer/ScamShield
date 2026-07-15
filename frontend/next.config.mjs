import path from "node:path";
import { fileURLToPath } from "node:url";

const dir = path.dirname(fileURLToPath(import.meta.url));

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
  // No API proxy. It existed only to make the old SameSite=Strict refresh cookie first-party;
  // Clerk keeps its session cookie on this origin and hands out bearer tokens, so nothing needs
  // to be tunnelled. Every API call goes straight to Spring, which also means the analysis
  // endpoint keeps seeing the caller's real IP for rate limiting.
};

export default nextConfig;
