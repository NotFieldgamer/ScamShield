"use client";

import * as React from "react";
import Link from "next/link";
import { type Claim } from "@/lib/api";
import { submitReport, useSession } from "@/lib/auth";
import { Toast, ToastTitle, ToastDescription, ToastAction } from "@/components/primitives/Toast";
import { cn } from "@/lib/utils";

type ToastState = { title: string; desc: string; tone: "ok" | "err" | "info"; needsSignin?: boolean };

/**
 * Inline verdict feedback. The buttons file a real report against the posting id; the outcome is a
 * toast. An anonymous or too-new account can't report (the poisoning guard), so we surface exactly
 * why and offer sign-in rather than pretending it worked.
 */
export function Feedback({ postingId }: { postingId: string }) {
  const { me } = useSession();
  const [open, setOpen] = React.useState(false);
  const [toast, setToast] = React.useState<ToastState>({ title: "", desc: "", tone: "info" });
  const [sending, setSending] = React.useState(false);

  function fire(next: ToastState) {
    setToast(next);
    setOpen(false);
    // Re-open on the next frame so an identical, repeat toast still animates in.
    requestAnimationFrame(() => setOpen(true));
  }

  async function report(claim: Claim) {
    if (!me) {
      fire({
        title: "Sign in to report",
        desc: "Reporting needs an account at least 7 days old — it keeps the feedback loop from being gamed.",
        tone: "info",
        needsSignin: true,
      });
      return;
    }
    setSending(true);
    try {
      const summary = await submitReport(postingId, claim);
      fire({
        title: "Report filed",
        desc:
          summary.status === "COMMUNITY_CONFIRMED"
            ? "A second independent report agreed — this posting is now community-flagged for admin review."
            : "It will be reviewed by an admin. Only admin-confirmed reports are ever used to retrain.",
        tone: "ok",
      });
    } catch (e) {
      fire({
        title: "Couldn't file the report",
        desc: e instanceof Error ? e.message : "Please try again.",
        tone: "err",
      });
    } finally {
      setSending(false);
    }
  }

  return (
    <div className="az-feedback-block">
      <p className="az-eyebrow">Was this right?</p>
      <p className="az-feedback-note">
        A label only changes on agreement between two independent reporters or an admin&apos;s
        decision — and only admin-confirmed reports are ever used to retrain.
      </p>
      <div className="az-feedback">
        <button
          type="button"
          className="ss-btn ss-btn-ghost"
          disabled={sending}
          onClick={() => report("FALSE_POSITIVE")}
        >
          This was actually real
        </button>
        <button
          type="button"
          className="ss-btn ss-btn-ghost"
          disabled={sending}
          onClick={() => report("CONFIRMED_SCAM")}
        >
          Confirm scam
        </button>
      </div>

      <Toast
        open={open}
        onOpenChange={setOpen}
        duration={6000}
        className={cn(toast.tone === "err" && "az-toast-err", toast.tone === "ok" && "az-toast-ok")}
      >
        <ToastTitle>{toast.title}</ToastTitle>
        <ToastDescription>{toast.desc}</ToastDescription>
        {toast.needsSignin && (
          <ToastAction altText="Sign in to report" asChild>
            <Link
              className="ss-btn ss-btn-primary"
              href={`/login?next=${encodeURIComponent(`/report/${postingId}`)}`}
            >
              Sign in
            </Link>
          </ToastAction>
        )}
      </Toast>
    </div>
  );
}
