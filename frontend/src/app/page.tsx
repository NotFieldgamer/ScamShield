import type { Metadata } from "next";
import { Landing } from "@/components/features/Landing";

export const metadata: Metadata = {
  title: "Verity — is this job real?",
  description:
    "Paste any job post or recruiter message and find out, in under a second, whether it's likely a scam — and exactly what gave it away.",
};

export default function Home() {
  return <Landing />;
}
