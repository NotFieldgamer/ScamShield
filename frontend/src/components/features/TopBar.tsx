import Link from "next/link";
import { ThemeToggle } from "@/components/primitives/ThemeToggle";

export function TopBar() {
  return (
    <header className="az-topbar">
      <Link href="/analyze" className="az-brand">
        Verity
      </Link>
      <ThemeToggle />
    </header>
  );
}
