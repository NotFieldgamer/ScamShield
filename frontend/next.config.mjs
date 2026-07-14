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
};

export default nextConfig;
