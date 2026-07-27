/**
 * The blog's single source of truth. The index page, the per-post route and
 * `sitemap.ts` all read from here, so a post cannot exist in one and be missing
 * from another.
 *
 * Metadata only — no JSX. `sitemap.ts` imports this module, and keeping post
 * bodies out of it means the sitemap never pulls a React tree in to render an
 * XML file. Bodies live in `content/` and are wired up in `[slug]/page.tsx`,
 * where the type system requires every slug here to have one.
 *
 * House rule, inherited from the landing page: every claim in a post must be
 * true of the current build. No invented customers, testimonials, user counts,
 * or uptime figures. See docs/marketing-plan.md §1.
 */
export type Post = {
  slug: string;
  title: string;
  /** Used as the meta description and as the index-card summary. */
  description: string;
  /** ISO date. Drives sort order and the <time> element. */
  date: string;
  /**
   * Word count at ~220 wpm, rounded up to allow for tables and code blocks,
   * which nobody reads at prose speed. Honest estimate, not a growth lever —
   * recount when a post's body changes rather than leaving it flattering.
   */
  readingMinutes: number;
  tag: string;
};

export const posts = [
  {
    slug: "what-it-costs-to-self-host-clamav",
    title: "What it actually costs to self-host ClamAV",
    description:
      "The engine is free; the scanning service around it is not. A full cost model — the VM, the " +
      "integration build, and the operations time — with every assumption stated.",
    date: "2026-07-26",
    readingMinutes: 6,
    tag: "Economics",
  },
  {
    slug: "your-file-upload-endpoint-has-no-antivirus",
    title: "Your file upload endpoint has no antivirus",
    description:
      "Endpoint antivirus protects devices. A file arriving at a cloud API never touches one — " +
      "which means most upload pipelines are an AV coverage gap by construction.",
    date: "2026-07-26",
    readingMinutes: 4,
    tag: "Architecture",
  },
] as const satisfies readonly Post[];

/** Union of every published slug — used to force body coverage at compile time. */
export type PostSlug = (typeof posts)[number]["slug"];

/** Newest first. The index and any future feed should both use this. */
export const postsByNewest = [...posts].sort((a, b) => b.date.localeCompare(a.date));

export function findPost(slug: string): Post | undefined {
  return posts.find((p) => p.slug === slug);
}

/** e.g. "26 July 2026" — matches the date style used across the docs. */
export function formatPostDate(iso: string): string {
  return new Date(`${iso}T00:00:00Z`).toLocaleDateString("en-GB", {
    day: "numeric",
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  });
}
