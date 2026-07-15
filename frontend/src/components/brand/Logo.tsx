import Link from "next/link";
import { LogoMark } from "./LogoMark";
import { cn } from "@/lib/utils";

/** The full lockup: the mark left of the "Verity" wordmark in the display face. */
export function Logo({ href = "/", className }: { href?: string; className?: string }) {
  return (
    <Link href={href} className={cn("frame-logo", className)} aria-label="Verity — home">
      <LogoMark size={30} className="frame-logo__mark" />
      <span className="frame-logo__word">Verity</span>
    </Link>
  );
}
