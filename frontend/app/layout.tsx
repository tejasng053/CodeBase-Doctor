import type { Metadata } from "next";
import "./globals.css";
export const metadata: Metadata = { title: "Codebase Doctor — Java & Spring workbench", description: "Understand, diagnose, and repair a Java repository with a reviewable record of every change.", icons: { icon: "/favicon.svg" } };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body>{children}</body></html>;
}
