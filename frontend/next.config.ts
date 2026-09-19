import type { NextConfig } from "next";
const nextConfig: NextConfig = {
  output: "standalone",
  // Use the compiler API: this local environment drops captured detached CLI output.
  experimental: { useTypeScriptCli: false },
  poweredByHeader: false,
  images: { unoptimized: true },
  async headers() {
    return [{ source: "/(.*)", headers: [
      { key: "X-Content-Type-Options", value: "nosniff" },
      { key: "X-Frame-Options", value: "DENY" },
      { key: "Referrer-Policy", value: "no-referrer" },
      { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
      { key: "Content-Security-Policy", value: "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'" },
    ] }];
  },
};
export default nextConfig;
