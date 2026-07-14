"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Logo } from "@/components/brand/Logo";
import { ThemeToggle } from "./ThemeToggle";
import { cn } from "@/lib/utils";

type SiteHeaderProps = {
  /** Stick to the top and react to scroll (default). Pass false to embed it inline for a demo. */
  sticky?: boolean;
  /** Force the scrolled treatment regardless of scroll position — used by the kitchen sink. */
  forceScrolled?: boolean;
};

const NAV = [
  { label: "How it works", href: "/#how-it-works" },
  { label: "Model", href: "/model" },
  { label: "Trends", href: "/trends" },
];

/**
 * The slim site header: logo + wordmark left, nav centered, theme toggle + Sign in right. On
 * scroll it gains a backdrop blur, a translucent fill, and a hairline bottom border.
 */
export function SiteHeader({ sticky = true, forceScrolled = false }: SiteHeaderProps) {
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    if (forceScrolled || !sticky) return;
    const onScroll = () => setScrolled(window.scrollY > 4);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, [forceScrolled, sticky]);

  const isScrolled = forceScrolled || scrolled;

  return (
    <header
      className={cn("frame-header", !sticky && "frame-header--static")}
      data-scrolled={isScrolled ? "true" : "false"}
    >
      <div className="frame-header__inner">
        <div className="frame-header__brand">
          <Logo />
        </div>
        <nav className="frame-header__nav" aria-label="Primary">
          {NAV.map((item) => (
            <Link key={item.href} href={item.href} className="frame-nav__link">
              {item.label}
            </Link>
          ))}
        </nav>
        <div className="frame-header__actions">
          <ThemeToggle />
          <Link href="/login" className="btn-signin">
            Sign in
          </Link>
        </div>
      </div>
    </header>
  );
}
