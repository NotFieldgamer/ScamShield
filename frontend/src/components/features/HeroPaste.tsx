"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/primitives/Button";
import { ANALYZE_HANDOFF_KEY, CANONICAL_SCAM } from "@/lib/handoff";

/**
 * The live paste box in the hero. It doesn't analyse in place — the tool's home is /analyze — it
 * hands the text off there (via sessionStorage) so the full two-column verdict resolves on the
 * product screen. Empty offers the canonical example.
 */
export function HeroPaste() {
  const router = useRouter();
  const [text, setText] = React.useState("");

  function handOff(value: string) {
    if (value.trim().length === 0) return;
    try {
      sessionStorage.setItem(ANALYZE_HANDOFF_KEY, value);
    } catch {
      /* sessionStorage unavailable — /analyze will just open empty */
    }
    router.push("/analyze");
  }

  return (
    <div className="lp-hero-box surface-card">
      <label className="lp-hero-box-label" htmlFor="hero-paste">
        Paste a posting
      </label>
      <textarea
        id="hero-paste"
        className="az-textarea lp-hero-textarea"
        placeholder="Paste the posting or message here…"
        value={text}
        onChange={(e) => setText(e.target.value)}
        aria-label="Posting or message text"
      />
      <div className="lp-hero-box-row">
        {text.trim().length === 0 && (
          <button type="button" className="az-example-link" onClick={() => handOff(CANONICAL_SCAM)}>
            Try an example
          </button>
        )}
        <Button onClick={() => handOff(text)} disabled={text.trim().length === 0}>
          Analyze posting
        </Button>
      </div>
    </div>
  );
}
