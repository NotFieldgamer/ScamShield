/**
 * Dresses Clerk's prebuilt auth components in our design system, so the one screen we no longer own
 * still belongs to the product.
 *
 * Styling goes through `elements` **class names**, not `variables`. That is not a preference — it is
 * the only thing that works here. Clerk parses `variables` colours to derive hover/shade ramps, so a
 * `var(--accent)` reference does not resolve: it silently yields a transparent button with black
 * text. Literal hex would parse, but would then be frozen to one theme, and the toggle rewrites our
 * tokens on `<html>`. Class names hand styling back to our CSS, which reads the tokens live and so
 * follows light/dark for free — and makes the sign-in button the *same* button as everywhere else,
 * rather than a lookalike.
 *
 * Clerk's `cl-*` classes are a documented styling API; the rest is dressed in `styles/clerk.css`.
 */
export const clerkAppearance = {
  elements: {
    // Our page already renders the heading, the sub and the card. Clerk's own must be removed
    // outright, not merely hidden: `ss-sr-only` still leaves them in the accessibility tree, which
    // gave the page two <h1> "Create your account" announcements.
    card: "clerk-card",
    header: "clerk-gone",
    footer: "clerk-gone",

    // The real button from our design system — pill, vermillion, correct focus ring.
    formButtonPrimary: "ss-btn ss-btn-primary clerk-submit",

    formFieldLabel: "clerk-label",
    formFieldInput: "clerk-input",
    socialButtonsBlockButton: "ss-btn ss-btn-ghost clerk-social",
    dividerLine: "clerk-divider-line",
    dividerText: "clerk-divider-text",
    footerActionLink: "ss-link",
    identityPreviewEditButton: "ss-link",
    formResendCodeLink: "ss-link",
    otpCodeFieldInput: "clerk-input clerk-otp",
  },
};
