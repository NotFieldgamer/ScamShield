import type { Metadata } from "next";
import { type ReactNode } from "react";
import { Bricolage_Grotesque, Inter, JetBrains_Mono } from "next/font/google";
import { Providers } from "./providers";
import "@/styles/globals.css";

const display = Bricolage_Grotesque({
  subsets: ["latin"],
  variable: "--font-bricolage",
  display: "swap",
});
const sans = Inter({ subsets: ["latin"], variable: "--font-inter", display: "swap" });
const mono = JetBrains_Mono({ subsets: ["latin"], variable: "--font-jetbrains", display: "swap" });

export const metadata: Metadata = {
  title: "Scam Shield",
  description: "Paste any job post. Find out if it's fake, and see exactly what gave it away.",
};

// Runs before first paint: applies the stored (or system) theme to <html> so there is no
// white flash. localStorage first, then prefers-color-scheme, defaulting to dark.
const themeScript = `(function(){try{var k='scam-shield-theme';var t=localStorage.getItem(k);if(t!=='light'&&t!=='dark'){t=window.matchMedia('(prefers-color-scheme: light)').matches?'light':'dark';}document.documentElement.setAttribute('data-theme',t);}catch(e){document.documentElement.setAttribute('data-theme','dark');}})();`;

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html
      lang="en"
      suppressHydrationWarning
      className={`${display.variable} ${sans.variable} ${mono.variable}`}
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeScript }} />
      </head>
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
