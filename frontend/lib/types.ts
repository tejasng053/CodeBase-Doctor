export type Status = "QUEUED" | "RUNNING" | "AWAITING_APPROVAL" | "COMPLETED" | "FAILED" | "CANCELLED";
export type Finding = { title: string; type: string; severity: string; file: string; line: number | null; evidence: string; explanation: string; suggestion: string };
export type CodeSymbol = { name: string; packageName: string; kind: string; layer: string; file: string; line: number; lines: number; annotations: string[]; dependencies: string[] };
export type Analysis = { language: string; javaVersion: string; buildTool: string; springBootVersion: string; modules: string[]; importantFiles: string[]; files: string[]; symbols: CodeSymbol[]; findings: Finding[]; observations: string[] };
export type Verification = { status: string; tests: number | null; failures: number | null; skipped: number | null; output: string; durationMs: number };
export type Plan = { summary: string; steps: string[]; files: string[]; risks: string[] };
export type Job = {
  id: string; repository: string; objective: string; mode: "SCAN" | "SOLVE"; status: Status; stage: string; createdAt: string; updatedAt: string;
  branch: string | null; error: string | null; diff: string; reportMarkdown: string; changeSummary: string; approvalDigest: string | null;
  analysis: Analysis | null; plan: Plan | null; baselineBuild: Verification | null; baselineTests: Verification | null; finalBuild: Verification | null; finalTests: Verification | null;
  events: { sequence: number; timestamp: string; stage: string; tool: string; status: string; message: string; durationMs: number }[];
  changes: { file: string; summary: string; reason: string; additions: number; deletions: number }[];
  inspectedFiles: string[]; concerns: string[]; cancelled: boolean;
};
export type Health = { executionEnabled?: boolean; message?: string; sandbox: { available: boolean; message: string; [key: string]: unknown }; groqConfigured: boolean };
