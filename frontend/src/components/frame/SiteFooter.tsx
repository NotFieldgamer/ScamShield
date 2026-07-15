import Link from "next/link";

type SiteFooterProps = {
  /**
   * The active model version shown in the meta column. A placeholder in the frame; once wired it
   * binds to GET /api/v1/models/active/metrics so the number traces to a real record.
   */
  modelVersion?: string;
};

const REPO_URL = "https://github.com/NotFieldgamer/ScamShield";

/**
 * Three columns on the ambient plane (no card), separated from the page by a hairline rule:
 * product links, the "what this is / isn't" line, and meta (model version, GitHub, year).
 */
export function SiteFooter({ modelVersion = "logreg · v1" }: SiteFooterProps) {
  const year = new Date().getFullYear();

  return (
    <footer className="frame-footer">
      <div className="frame-footer__inner">
        <div>
          <p className="frame-footer__col-title">Product</p>
          <div className="frame-footer__links">
            <Link href="/analyze">Analyze a posting</Link>
            <Link href="/model">Model performance</Link>
            <Link href="/trends">Trends</Link>
            <Link href="/campaigns">Campaigns</Link>
          </div>
        </div>

        <div>
          <p className="frame-footer__col-title">What this is</p>
          <p className="frame-footer__disclaimer">
            Verity <strong>estimates risk from language patterns.</strong> It can be wrong. Not
            legal advice.
          </p>
        </div>

        <div>
          <p className="frame-footer__col-title">Meta</p>
          <div className="frame-footer__meta">
            <span>model: {modelVersion}</span>
            <a href={REPO_URL} target="_blank" rel="noreferrer noopener">
              GitHub ↗
            </a>
            <span>© {year} Verity</span>
          </div>
        </div>
      </div>
    </footer>
  );
}
