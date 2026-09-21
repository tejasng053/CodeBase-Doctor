import { NextRequest } from "next/server";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
const jobId = "[a-zA-Z0-9-]{1,80}";
const readRoute = new RegExp(`^(?:health|jobs|jobs/${jobId}(?:/(?:events|report|report\\.html|diff))?)$`);
const writeRoute = new RegExp(`^(?:jobs|jobs/${jobId}/(?:approve|cancel|publish))$`);

type Context = { params: Promise<{ path: string[] }> };
async function proxy(request: NextRequest, context: Context) {
  const { path } = await context.params;
  const route = path.join("/");
  if (!(request.method === "GET" ? readRoute : writeRoute).test(route)) {
    return Response.json({ error: "Unknown Codebase Doctor endpoint." }, { status: 404 });
  }
  const expectedOrigin = process.env.DOCTOR_FRONTEND_ORIGIN || "http://127.0.0.1:3000";
  const accepted = new URL(expectedOrigin);
  if (!["127.0.0.1", "localhost", "[::1]"].includes(accepted.hostname)) {
    return Response.json({ error: "The frontend must be configured on a loopback address." }, { status: 503 });
  }
  // Reject DNS rebinding and browser cross-origin writes before attaching the server token.
  if (request.headers.get("host") !== accepted.host ||
      (request.headers.get("sec-fetch-site") && !["same-origin", "none"].includes(request.headers.get("sec-fetch-site")!))) {
    return Response.json({ error: "Only the configured local application may access this API." }, { status: 403 });
  }
  if (request.method === "POST" && request.headers.get("origin") !== accepted.origin) {
    return Response.json({ error: "This action requires the configured local application origin." }, { status: 403 });
  }
  const token = process.env.DOCTOR_API_TOKEN;
  if (!token || token.length < 32) return Response.json({ error: "Set DOCTOR_API_TOKEN in the frontend server environment to match the backend." }, { status: 503 });
  const backend = new URL(process.env.DOCTOR_BACKEND_URL || "http://127.0.0.1:8080");
  if (backend.protocol !== "http:" || !["127.0.0.1", "localhost", "[::1]"].includes(backend.hostname) || backend.username || backend.password || backend.pathname !== "/") {
    return Response.json({ error: "The backend URL must be an HTTP loopback origin." }, { status: 503 });
  }
  let body: string | undefined;
  if (request.method === "POST") {
    if (!request.headers.get("content-type")?.startsWith("application/json")) return Response.json({ error: "JSON body required." }, { status: 415 });
    if (Number(request.headers.get("content-length")) > 24_000) return Response.json({ error: "Request is too large." }, { status: 413 });
    const reader = request.body?.getReader();
    let size = 0;
    const chunks: Uint8Array[] = [];
    if (reader) {
      while (true) {
        const next = await reader.read();
        if (next.done) break;
        size += next.value.length;
        if (size > 24_000) { await reader.cancel(); return Response.json({ error: "Request is too large." }, { status: 413 }); }
        chunks.push(next.value);
      }
    }
    body = Buffer.concat(chunks).toString("utf8");
    try { JSON.parse(body); } catch { return Response.json({ error: "Invalid JSON body." }, { status: 400 }); }
  }
  try {
    const response = await fetch(new URL(`/api/${route}`, backend), {
      method: request.method, body, redirect: "error", cache: "no-store",
      headers: { "X-Doctor-Token": token, ...(body ? { "Content-Type": "application/json" } : {}) },
      signal: route.endsWith("/events") ? request.signal : AbortSignal.any([request.signal, AbortSignal.timeout(route.endsWith("/publish") ? 175000 : 12000)]),
    });
    const headers = new Headers({ "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" });
    for (const key of ["content-type", "content-disposition", "content-security-policy"]) {
      const value = response.headers.get(key); if (value) headers.set(key, value);
    }
    if (route.endsWith("/events")) headers.set("X-Accel-Buffering", "no");
    return new Response(response.body, { status: response.status, headers });
  } catch {
    return Response.json({ error: "The local backend is unavailable. Start the Codebase Doctor backend and retry." }, { status: 503 });
  }
}
export const GET = proxy;
export const POST = proxy;
