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
  },
};

export default nextConfig;
