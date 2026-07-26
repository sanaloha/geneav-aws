import type { Metadata } from "next";
import type { ComponentType } from "react";
import { ArrowLeft, ArrowRight } from "lucide-react";
import Link from "next/link";
import { notFound } from "next/navigation";
import Badge from "../../ui/Badge";
import { ButtonLink } from "../../ui/Button";
import Section from "../../ui/Section";
import { SITE_NAME, SITE_URL } from "../../site";
import { findPost, formatPostDate, posts, type PostSlug } from "../posts";
import { Prose } from "../prose";
import UploadEndpointHasNoAntivirus from "../content/your-file-upload-endpoint-has-no-antivirus";
import WhatItCostsToSelfHostClamAv from "../content/what-it-costs-to-self-host-clamav";

/**
 * Slug → body. Typed as `Record<PostSlug, …>`, so adding an entry to
 * `posts.ts` without writing its body is a compile error rather than a 404
 * discovered in production.
 */
const bodies: Record<PostSlug, ComponentType> = {
  "your-file-upload-endpoint-has-no-antivirus": UploadEndpointHasNoAntivirus,
  "what-it-costs-to-self-host-clamav": WhatItCostsToSelfHostClamAv,
};

export function generateStaticParams() {
  return posts.map(({ slug }) => ({ slug }));
}

export async function generateMetadata({
  params,
}: {
  params: { slug: string };
}): Promise<Metadata> {
  const post = findPost(params.slug);
  if (!post) return {};

  const url = `${SITE_URL}/blog/${post.slug}`;
  return {
    title: post.title,
    description: post.description,
    alternates: { canonical: `/blog/${post.slug}` },
    openGraph: {
      type: "article",
      url,
      title: post.title,
      description: post.description,
      publishedTime: post.date,
      siteName: SITE_NAME,
      locale: "en_US",
    },
    twitter: {
      card: "summary_large_image",
      title: post.title,
      description: post.description,
    },
  };
}

export default function BlogPost({ params }: { params: { slug: string } }) {
  const post = findPost(params.slug);
  if (!post) notFound();

  const Body = bodies[post.slug as PostSlug];

  // Per-post structured data. The site-wide graph in layout.tsx describes the
  // product; this describes the article, which is what earns the rich result.
  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "BlogPosting",
    headline: post.title,
    description: post.description,
    datePublished: post.date,
    dateModified: post.date,
    url: `${SITE_URL}/blog/${post.slug}`,
    mainEntityOfPage: `${SITE_URL}/blog/${post.slug}`,
    author: { "@type": "Organization", name: SITE_NAME, url: SITE_URL },
    publisher: { "@type": "Organization", name: SITE_NAME, url: SITE_URL },
  };

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />

      <Section>
        <Link
          href="/blog"
          className="inline-flex items-center gap-1.5 text-[15px] font-semibold text-ink-muted no-underline hover:text-ink hover:no-underline"
        >
          <ArrowLeft size={16} aria-hidden /> All posts
        </Link>

        <article className="mt-8">
          <header className="max-w-[68ch]">
            <div className="flex flex-wrap items-center gap-3">
              <Badge tone="neutral" uppercase>
                {post.tag}
              </Badge>
              <time dateTime={post.date} className="text-[13px] text-ink-subtle">
                {formatPostDate(post.date)}
              </time>
              <span className="text-[13px] text-ink-subtle">· {post.readingMinutes} min read</span>
            </div>
            <h1 className="mt-4 text-3xl font-extrabold leading-tight tracking-tight text-ink sm:text-4xl">
              {post.title}
            </h1>
          </header>

          <Prose>
            <Body />
          </Prose>
        </article>

        {/* Shared close. Deliberately low-pressure: much of this blog's audience
            is running ClamAV themselves, and selling at them converts worse than
            being the most useful page they read today. */}
        <aside className="mt-14 max-w-[68ch] rounded-card border border-line bg-surface-subtle p-6">
          <h2 className="m-0 text-lg font-bold text-ink">Want to try it instead of building it?</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            geneav is a REST API that does the above — one <code className="font-mono">POST</code>,
            a clean-or-infected verdict, no daemon of your own to run. Detection is ClamAV&apos;s,
            exactly. The free tier is 500 scans a month and needs no card.
          </p>
          <ButtonLink href="/developers" variant="primary" className="mt-5">
            Try a scan <ArrowRight size={18} aria-hidden />
          </ButtonLink>
        </aside>
      </Section>
    </>
  );
}
