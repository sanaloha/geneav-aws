/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  experimental: {
    // lucide-react is a ~1500-icon barrel file; without this every dev compile
    // walks all of it.
    optimizePackageImports: ["lucide-react"],
  },
  env: {
    // Base URL of the Spring Boot scan API. Override in .env.local for other environments.
    NEXT_PUBLIC_API_BASE_URL: process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080",
    // Umami website id. Deliberately has no default: unset means no analytics
    // tag is rendered, which is what we want locally and on a fresh deploy
    // before the site has been created in Umami.
    NEXT_PUBLIC_UMAMI_WEBSITE_ID: process.env.NEXT_PUBLIC_UMAMI_WEBSITE_ID || "",
  },
};

export default nextConfig;
