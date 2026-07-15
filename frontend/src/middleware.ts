import { clerkMiddleware } from "@clerk/nextjs/server";

/**
 * Makes Clerk's session available to the app. Named `middleware.ts` because this is Next.js 15;
 * Next 16 renames the file to `proxy.ts`.
 *
 * Deliberately does not gate routes. Authorisation lives where it can be trusted: the Spring API
 * decides what a caller may read or write, and `RequireAuth` only handles what the page shows. A
 * check here would protect the page shell and nothing else — the data is not served from Next.
 */
export default clerkMiddleware();

export const config = {
  matcher: [
    // Everything except Next internals and static files...
    "/((?!_next|[^?]*\\.(?:html?|css|js(?!on)|jpe?g|webp|png|gif|svg|ttf|woff2?|ico|csv|docx?|xlsx?|zip|webmanifest)).*)",
    // ...plus API routes and Clerk's own handshake endpoints.
    "/(api|trpc)(.*)",
    "/__clerk/(.*)",
  ],
};
