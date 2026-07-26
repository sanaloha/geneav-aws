import type { MetadataRoute } from "next";
import { posts } from "./blog/posts";
import { SITE_URL } from "./site";

// Served at /sitemap.xml — enumerates every crawlable page.
export default function sitemap(): MetadataRoute.Sitemap {
  const now = new Date();
  const routes: { path: string; priority: number; changeFrequency: "monthly" | "weekly" }[] = [
    { path: "/", priority: 1.0, changeFrequency: "weekly" },
    { path: "/features", priority: 0.8, changeFrequency: "monthly" },
    { path: "/developers", priority: 0.9, changeFrequency: "monthly" },
    { path: "/about", priority: 0.6, changeFrequency: "monthly" },
    // Buyers reach /security straight from questionnaires and vendor reviews,
    // so it earns a higher priority than the other trust pages.
    { path: "/security", priority: 0.5, changeFrequency: "monthly" },
    // The blog is the acquisition channel, so the index tracks the posts it
    // lists and is crawled at the same cadence as the landing page.
    { path: "/blog", priority: 0.7, changeFrequency: "weekly" },
    { path: "/terms", priority: 0.3, changeFrequency: "monthly" },
    { path: "/legal", priority: 0.3, changeFrequency: "monthly" },
    { path: "/privacy", priority: 0.3, changeFrequency: "monthly" },
  ];

  const staticPages = routes.map(({ path, priority, changeFrequency }) => ({
    url: `${SITE_URL}${path}`,
    lastModified: now,
    changeFrequency,
    priority,
  }));

  // Posts carry their own publication date rather than `now`, so a crawler is
  // not told that every article changed on every deploy.
  const postPages = posts.map((post) => ({
    url: `${SITE_URL}/blog/${post.slug}`,
    lastModified: new Date(`${post.date}T00:00:00Z`),
    changeFrequency: "monthly" as const,
    priority: 0.6,
  }));

  return [...staticPages, ...postPages];
}
