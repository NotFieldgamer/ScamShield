import { ThemeToggle } from "@/components/primitives/ThemeToggle";
import { Gallery } from "./gallery";

export default function KitchenSinkPage() {
  return (
    <main
      style={{
        maxWidth: "1400px",
        margin: "0 auto",
        padding: "2rem 1.25rem 4rem",
      }}
    >
      <header
        style={{
          display: "flex",
          alignItems: "flex-end",
          justifyContent: "space-between",
          gap: "1rem",
          flexWrap: "wrap",
          marginBottom: "2rem",
        }}
      >
        <div>
          <h1 className="font-display" style={{ fontSize: "1.9rem", margin: 0 }}>
            Kitchen sink
          </h1>
          <p className="text-muted" style={{ margin: "0.4rem 0 0", maxWidth: "44ch", lineHeight: 1.5 }}>
            Every primitive, rendered in both themes at once. The page chrome follows the toggle;
            the two columns are pinned to dark and light for side-by-side comparison.
          </p>
        </div>
        <ThemeToggle />
      </header>

      <div
        style={{
          display: "grid",
          gap: "1.5rem",
          gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))",
        }}
      >
        <section data-theme="dark" className="ss-theme-col">
          <p className="data" style={{ margin: "0 0 1.25rem", color: "var(--text-faint)", fontSize: "0.75rem" }}>
            DARK THEME
          </p>
          <Gallery />
        </section>
        <section data-theme="light" className="ss-theme-col">
          <p className="data" style={{ margin: "0 0 1.25rem", color: "var(--text-faint)", fontSize: "0.75rem" }}>
            LIGHT THEME
          </p>
          <Gallery />
        </section>
      </div>
    </main>
  );
}
