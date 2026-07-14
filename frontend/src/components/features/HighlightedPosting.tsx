"use client";

import * as React from "react";
import { type PhraseHit } from "@/lib/api";
import { cn } from "@/lib/utils";

function escapeRegExp(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

type Segment = { text: string; flagged: boolean };

/** Splits the posting into flagged / unflagged runs using the phrases the API matched. */
function segment(text: string, phrases: PhraseHit[]): Segment[] {
  const terms = phrases
    .map((p) => p.phrase)
    .filter((p) => p && p.trim().length > 0)
    // longest first so "bank account details" wins over "account"
    .sort((a, b) => b.length - a.length);
  if (terms.length === 0) return [{ text, flagged: false }];

  const re = new RegExp(`(${terms.map(escapeRegExp).join("|")})`, "gi");
  const segs: Segment[] = [];
  let last = 0;
  let m: RegExpExecArray | null;
  while ((m = re.exec(text)) !== null) {
    if (m.index > last) segs.push({ text: text.slice(last, m.index), flagged: false });
    segs.push({ text: m[0], flagged: true });
    last = m.index + m[0].length;
    if (m.index === re.lastIndex) re.lastIndex++; // guard against zero-length matches
  }
  if (last < text.length) segs.push({ text: text.slice(last), flagged: false });
  return segs;
}

export function HighlightedPosting({
  text,
  phrases,
  lit,
}: {
  text: string;
  phrases: PhraseHit[];
  lit: boolean;
}) {
  const segments = React.useMemo(() => segment(text, phrases), [text, phrases]);
  return (
    <p className={cn("az-posting", lit && "is-lit")}>
      {segments.map((s, i) =>
        s.flagged ? (
          <mark key={i} className="az-flagword">
            {s.text}
          </mark>
        ) : (
          <React.Fragment key={i}>{s.text}</React.Fragment>
        ),
      )}
    </p>
  );
}
