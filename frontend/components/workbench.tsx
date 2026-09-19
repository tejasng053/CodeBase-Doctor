"use client";

import { useCallback, useEffect, useState } from "react";
import type { Health } from "@/lib/types";

const stages = ["Clone", "Detect", "Analyze", "Diagnose", "Plan", "Repair", "Verify", "Review"];
export default function Workbench() {
  const [health, setHealth] = useState<Health | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [mode, setMode] = useState("SCAN");
  const [tab, setTab] = useState("Workspace");
  const [repository, setRepository] = useState("");
  const [objective, setObjective] = useState("");
  const refresh = useCallback(async () => {
    setLoading(true); setError("");
    try {
      const response = await fetch("/api/health", { cache: "no-store", signal: AbortSignal.timeout(15000) });
      const data = await response.json();
      if (!response.ok) throw new Error(data.error || "The local backend could not be reached.");
      setHealth(data);
    } catch (cause) { setHealth(null); setError(cause instanceof Error ? cause.message : "Connection failed."); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void refresh(); }, [refresh]);
  return <div className="app-shell">
    <aside className="sidebar">
      <a href="/" className="brand" aria-label="Codebase Doctor home"><span className="brand-mark">+</span><span>codebase<span className="brand-light">doctor</span></span></a>
      <div className="sidebar-label">LOCAL WORKSPACE</div>
      <nav aria-label="Workspace sections">{["Workspace", "Documentation", "Activity"].map((name, index) => <button key={name} className={tab === name ? "nav-item active" : "nav-item"} onClick={() => setTab(name)} aria-current={tab === name ? "page" : undefined}><span className="nav-icon">{["⌘", "▤", "≡"][index]}</span>{name}{name === "Workspace" && <span className="nav-count">01</span>}</button>)}</nav>
      <div className="sidebar-label recent-label">RECENT REPOSITORIES</div>
      <p className="sidebar-empty">Your repositories will appear here after your first analysis.</p>
      <div className="sidebar-bottom"><div className="local-icon">⌂</div><div><strong>Local environment</strong><span>Private to this computer</span></div></div>
    </aside>
    <div className="main-shell">
      <header className="topbar"><div>Workspace <span className="crumb">/</span> <strong>{tab}</strong></div><span className="foundation-pill"><span /> Foundation · Milestone 01</span></header>
      <main>
        <div className="page-heading"><div className="eyebrow">JAVA + SPRING, FIRST</div><h1>{tab === "Documentation" ? "A clear record of every change." : tab === "Activity" ? "Every action, accounted for." : "Understand. Repair. Verify."}</h1><p>{tab === "Documentation" ? "Repository context and change explanations, grounded in real execution." : tab === "Activity" ? "Operations and their results will appear here when workflows are enabled." : "A focused workspace for healthier Java and Spring codebases."}</p></div>
        <div className="milestone-note"><span className="note-icon">i</span><div><strong>The foundation is ready to review.</strong><p>Repository workflows are disabled until the next milestones are built, tested, and approved.</p></div><span className="small-label">NO REPO EXECUTION</span></div>
        {error && <div className="error-notice" role="alert"><strong>Backend connection unavailable</strong><p>{error}</p><button className="text-button" onClick={() => void refresh()}>Retry connection</button></div>}
        {tab === "Workspace" && <>
          <div className="workspace-grid"><section className="panel intake"><div className="panel-heading"><div><span className="section-number">01</span><h2>Start with a repository</h2></div><span className="tag">PUBLIC GITHUB</span></div>
            <form onSubmit={(event) => event.preventDefault()}>
              <label htmlFor="repository">Repository URL</label><div className="input-with-icon"><span aria-hidden="true">↗</span><input id="repository" type="url" placeholder="https://github.com/owner/repository" value={repository} onChange={event => setRepository(event.target.value)} autoComplete="off" /></div>
              <p className="field-note">Your original repository will require approval before any changes.</p>
              <fieldset><legend>What would you like to do?</legend><div className="mode-options"><button type="button" className={mode === "SCAN" ? "mode-card selected" : "mode-card"} onClick={() => setMode("SCAN")} aria-pressed={mode === "SCAN"}><span className="radio-mark" /><strong>Doctor Scan</strong><span>Understand the repo. Find evidence.</span></button><button type="button" className={mode === "SOLVE" ? "mode-card selected" : "mode-card"} onClick={() => setMode("SOLVE")} aria-pressed={mode === "SOLVE"}><span className="radio-mark" /><strong>Solve an issue</strong><span>Investigate a specific objective.</span></button></div></fieldset>
              <label htmlFor="objective">{mode === "SOLVE" ? "Issue or objective" : "Additional context"}<span className="optional">{mode === "SCAN" ? "Optional" : "Required for issue solving"}</span></label><textarea id="objective" rows={3} placeholder={mode === "SOLVE" ? "Paste a GitHub issue or describe the problem…" : "Anything the doctor should pay attention to…"} value={objective} onChange={event => setObjective(event.target.value)} />
              <div className="form-footer"><span><span className="lock">◇</span> Approval before edits</span><button className="primary-button" type="submit" disabled title="Repository intake is a later approved milestone">Analyze repository <span>↗</span></button></div>
            </form>
          </section><aside className="panel environment"><div className="panel-heading"><h2>Environment</h2><button className="icon-button" onClick={() => void refresh()} disabled={loading} aria-label="Refresh environment status">↻</button></div>
            <div className="environment-row"><div><strong>Local API</strong><span>Spring Boot · Java 21</span></div><span className={health ? "status good" : "status muted"}>{loading ? "Checking" : health ? "Connected" : "Unavailable"}</span></div>
            <div className="environment-row"><div><strong>Docker sandbox</strong><span>Repository execution boundary</span></div><span className="status caution">Disabled</span></div>
            <p className="diagnostic" aria-live="polite">{health?.sandbox.message || "Waiting for local Docker diagnostics."}</p>
            <div className="environment-row"><div><strong>Groq</strong><span>Reasoning provider</span></div><span className="status muted">{health ? health.groqConfigured ? "Configured" : "Not configured" : "Unknown"}</span></div>
            <p className="environment-note">No API key is needed for this milestone. No repository or model requests are sent.</p><div className="safety-card"><span>⌁</span><div><strong>Review comes first</strong><p>Plans, edits, and publishing each get a deliberate approval step.</p></div></div>
          </aside></div>
          <section className="panel pipeline"><div className="panel-heading"><h2>The path to a verified change</h2><span className="small-label">WORKFLOW PREVIEW</span></div><ol>{stages.map((stage, index) => <li key={stage}><span className="step-number">{String(index + 1).padStart(2, "0")}</span><strong>{stage}</strong><span className="pending">Not started</span></li>)}</ol></section>
          <div className="bottom-note"><span>◈</span> Real output. Explicit approvals. A documented result.<button className="text-button" onClick={() => setTab("Documentation")}>Explore the report structure <span>→</span></button></div>
        </>}
        {tab === "Documentation" && <section className="panel documentation"><div className="panel-heading"><h2>The Doctor Report</h2><span className="tag">PLANNED FEATURE</span></div><p className="intro">Every completed analysis will explain what the repository does and what the bot actually changed. Report generation is scheduled for a later approved milestone.</p><div className="report-sections">{[["01", "Repository overview", "Detected stack, modules, key files, and architecture relationships found in the code."], ["02", "The main change", "A plain-English explanation of the primary fix and why it matters."], ["03", "File-by-file explanation", "What changed in each file, why it changed, and the actual Git diff."], ["04", "Verification and limits", "Real baseline and final build/test results, unresolved concerns, and checks that did not run."]].map(([number, title, body]) => <article key={number}><span className="section-number">{number}</span><div><h3>{title}</h3><p>{body}</p></div></article>)}</div><div className="report-footer"><span>Downloadable Markdown · Evidence-backed findings</span><button className="primary-button" disabled>No report generated yet</button></div></section>}
        {tab === "Activity" && <section className="panel activity-empty"><div className="empty-symbol">⌘</div><h2>No repository operations yet</h2><p>The foundation only checks local service availability. Repository intake, builds, tools, and approvals will be recorded here after those milestones are enabled.</p><button className="secondary-button" onClick={() => setTab("Workspace")}>Back to workspace</button></section>}
        <footer className="page-footer"><span>CODEBASE DOCTOR <span className="footer-divider">/</span> LOCAL DEVELOPMENT</span><span>Milestone 01 · Review before continuing</span></footer>
      </main>
    </div>
  </div>;
}
