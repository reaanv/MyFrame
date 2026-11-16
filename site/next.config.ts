import type { NextConfig } from "next"

// Fully static export: Vercel serves plain files from `out/`. Nothing runs on a server.
const nextConfig: NextConfig = {
  output: "export",
  images: { unoptimized: true },
  // Tailwind CSS v4 runs through this Turbopack loader (no PostCSS config in this template).
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
}

export default nextConfig
