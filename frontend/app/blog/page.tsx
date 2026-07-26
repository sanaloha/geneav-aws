import { ArrowRight } from "lucide-react";
import Link from "next/link";
import Badge from "../ui/Badge";
import Card from "../ui/Card";
import PageHeader from "../ui/PageHeader";
import Section from "../ui/Section";
import { formatPostDate, postsByNewest } from "./posts";

const DESCRIPTION =
  "Notes on scanning file uploads for malware — running ClamAV in production, what self-hosting " +
  "actually costs, and how to wire a scan into an upload pipeline.";

export const metadata = {
  title: "Blog",
  description: DESCRIPTION,
  alternates: { canonical: "/blog" },
  // Without this the index inherits the site-level card, so a shared /blog link
  // reads as the landing page rather than as the blog. Individual posts set
  // their own in [slug]/page.tsx.
  openGraph: {
    type: "website",
    url: "/blog",
    title: "Blog · geneav",
    description: DESCRIPTION,
  },
};

export default function Blog() {
  return (
    <Section>
      <PageHeader
        title="Blog"
        lead="Notes on scanning uploads for malware — what running ClamAV in production actually involves, and how to put a scan in front of user-generated files."
      />

      <ul className="m-0 grid list-none gap-5 p-0">
        {postsByNewest.map((post) => (
          <li key={post.slug}>
            <Link href={`/blog/${post.slug}`} className="block no-underline hover:no-underline">
              <Card interactive>
                <div className="flex flex-wrap items-center gap-3">
                  <Badge tone="neutral" uppercase>
                    {post.tag}
                  </Badge>
                  <time dateTime={post.date} className="text-[13px] text-ink-subtle">
                    {formatPostDate(post.date)}
                  </time>
                  <span className="text-[13px] text-ink-subtle">
                    · {post.readingMinutes} min read
                  </span>
                </div>

                <h2 className="mt-3 text-xl font-extrabold tracking-tight text-ink">
                  {post.title}
                </h2>
                <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
                  {post.description}
                </p>
                <span className="mt-4 inline-flex items-center gap-1.5 text-[15px] font-semibold text-brand">
                  Read <ArrowRight size={16} aria-hidden />
                </span>
              </Card>
            </Link>
          </li>
        ))}
      </ul>
    </Section>
  );
}
