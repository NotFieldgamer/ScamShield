import Link from "next/link";
import { TopBar } from "@/components/features/TopBar";
import { VerdictView } from "@/components/features/VerdictView";
import { GlassCard } from "@/components/glass/GlassCard";
import { getAnalysis } from "@/lib/api";

export default async function AnalysisPermalink({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;

  let data;
  try {
    data = await getAnalysis(id);
  } catch {
    return (
      <main className="az-shell">
        <TopBar />
        <GlassCard style={{ textAlign: "center" }}>
          <h1 className="az-section-title">Verdict not found</h1>
          <p className="text-muted" style={{ marginBottom: "1.25rem" }}>
            This analysis link is invalid or has expired.
          </p>
          <Link className="ss-btn ss-btn-primary" href="/analyze">
            Analyze a posting
          </Link>
        </GlassCard>
      </main>
    );
  }

  return (
    <main className="az-shell">
      <TopBar />
      <VerdictView data={data} />
    </main>
  );
}
