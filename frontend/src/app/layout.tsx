import type { Metadata } from "next";
import { type ReactNode } from "react";
import { ClerkProvider } from "@clerk/nextjs";
import { Public_Sans } from "next/font/google";
import localFont from "next/font/local";
import { Providers } from "./providers";
import { clerkAppearance } from "@/lib/clerk-appearance";
import "@/styles/globals.css";

// Body — Public Sans (USWDS): the typeface of official government records. Not Inter.
const body = Public_Sans({ subsets: ["latin"], variable: "--font-public", display: "swap" });

// Display — Redaction: a serif built around document redaction / photocopy degradation.
// Self-hosted (not on Google Fonts); woff2 files live in ./fonts.
const display = localFont({
  src: [
    { path: "./fonts/Redaction-Regular.woff2", weight: "400", style: "normal" },
    { path: "./fonts/Redaction-Bold.woff2", weight: "700", style: "normal" },
  ],
  variable: "--font-redaction",
  display: "swap",
  fallback: ["Georgia", "Times New Roman", "serif"],
});

// Data — Commit Mono: an under-used humane monospace. Numbers read as evidence, not JetBrains.
const mono = localFont({
  src: [
    { path: "./fonts/CommitMono-Regular.woff2", weight: "400", style: "normal" },
    { path: "./fonts/CommitMono-Bold.woff2", weight: "700", style: "normal" },
  ],
  variable: "--font-commit",
  display: "swap",
  fallback: ["ui-monospace", "SFMono-Regular", "monospace"],
});

export const metadata: Metadata = {
  title: "Verity",
  description: "Paste any job post. Find out if it's fake, and see exactly what gave it away.",
};

// Runs before first paint: applies the stored (or system) theme to <html> so there is no
// white flash. localStorage first, then prefers-color-scheme, defaulting to dark.
const themeScript = `(function(){try{var k='verity-theme';var t=localStorage.getItem(k);if(t!=='light'&&t!=='dark'){t=window.matchMedia('(prefers-color-scheme: light)').matches?'light':'dark';}document.documentElement.setAttribute('data-theme',t);}catch(e){document.documentElement.setAttribute('data-theme','dark');}})();`;

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html
      lang="en"
      suppressHydrationWarning
      className={`${display.variable} ${body.variable} ${mono.variable}`}
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeScript }} />
      </head>
      <body>
        {/* Inside <body>, per Clerk's requirement. */}
        <ClerkProvider appearance={clerkAppearance}>
          <Providers>{children}</Providers>
        </ClerkProvider>
      </body>
    </html>
  );
}
