import type { Job } from "./types";
export async function api<T>(path: string, body?: object, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`/api/${path}`, {
    cache: "no-store", signal: signal || AbortSignal.timeout(path.endsWith("/publish") ? 180000 : 20000),
    ...(body ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {}),
  });
  const value = await response.json();
  if (!response.ok) throw new Error(value.error || value.message || `Request failed (${response.status}).`);
  return value;
}
export function repoName(repository: string) { try { return new URL(repository).pathname.replace(/^\//, "").replace(/\.git$/, ""); } catch { return repository; } }
export function label(value: string) { return value.toLowerCase().replaceAll("_", " "); }
export function isActive(job: Job) { return ["QUEUED", "RUNNING", "AWAITING_APPROVAL"].includes(job.status); }
export function date(value: string) { return new Date(value).toLocaleString([], { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" }); }
export function safeGithubLink(value: string | undefined) { try { const url = new URL(value || ""); return url.protocol === "https:" && url.hostname === "github.com" && !url.username && !url.password ? url.href : null; } catch { return null; } }
