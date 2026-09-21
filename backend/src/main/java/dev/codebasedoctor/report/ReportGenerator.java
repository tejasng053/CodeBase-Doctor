package dev.codebasedoctor.report;

import dev.codebasedoctor.model.Models.*;
import java.util.*;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Deterministic documentation from a job snapshot. Never executes repository content or asks an LLM. */
@Service
public class ReportGenerator {
  private static final int MAX_SYMBOLS = 250, MAX_FINDINGS = 250, MAX_OUTPUT_CHARACTERS = 32_768;
  private static final Pattern TOKEN = Pattern.compile("(?i)\\b(?:gsk_[A-Za-z0-9_-]{12,}|gh[pousr]_[A-Za-z0-9_]{16,}|github_pat_[A-Za-z0-9_]{16,}|sk-[A-Za-z0-9_-]{16,})\\b");
  private static final Pattern NAMED_SECRET = Pattern.compile("(?im)((?:[A-Za-z_]*?(?:API[_-]?KEY|PASSWORD|SECRET|ACCESS[_-]?TOKEN|AUTH[_-]?TOKEN|DOCTOR[_-]?API[_-]?TOKEN)[A-Za-z_]*|GITHUB_TOKEN|GROQ_API_KEY)\\s*[=:]\\s*[\\\"']?)([^\\s\\\"';,}]{4,})");

  public String generate(Job job) {
    Objects.requireNonNull(job, "job");
    StringBuilder report = new StringBuilder("# Codebase Doctor report\n\n");
    report.append("This document describes the recorded run. Agent explanations are labeled separately from static analysis, patch data, and command results. Missing evidence is not a successful check.\n\n");
    report.append("## Run context\n\n");
    field(report, "Run", job.id);
    field(report, "Repository", job.repository);
    field(report, "Mode", job.mode);
    field(report, "State", job.cancelled ? "CANCELLED" : job.status);
    field(report, "Stage", job.stage);
    field(report, "Created", job.createdAt);
    field(report, "Snapshot updated", job.updatedAt);
    field(report, "Reserved review branch", job.branch);
    field(report, "Pinned source revision", job.sourceRevision);
    field(report, "Source default branch", job.sourceDefaultBranch);
    if (!blank(job.publishedCommit)) field(report, "Published commit", job.publishedCommit);
    if (!blank(job.publishedBranchUrl)) field(report, "Published branch URL", job.publishedBranchUrl);
    if (!blank(job.pullRequestUrl)) field(report, "Pull request URL", job.pullRequestUrl);
    report.append("\n**Requested objective:** ").append(markdown(value(job.objective, "Not supplied"))).append("\n\n");

    LinkedHashMap<String, PatchStats> patch = patchStats(job.diff);
    report.append("## Main changes\n\n");
    if (blank(job.diff)) {
      report.append(list(job.changes).isEmpty() ? "No code changes are recorded in this run.\n\n" : "Structured changes were recorded, but no patch is available to verify their scope.\n\n");
    } else if (patch.isEmpty()) {
      report.append("A patch was recorded, but its file statistics could not be derived from supported unified-diff headers. Review the patch below.\n\n");
    } else {
      int additions = patch.values().stream().mapToInt(p -> p.additions).sum();
      int deletions = patch.values().stream().mapToInt(p -> p.deletions).sum();
      report.append("**Recorded patch scope:** ").append(patch.size()).append(" file(s) with supported diff headers, ").append(additions).append(" added text line(s), ").append(deletions).append(" removed text line(s). These statistics describe the recorded patch, which may replace whole files; they are not minimal edit counts or proof that the intended behavior works.\n\n");
    }
    if (!blank(job.changeSummary) && (!blank(job.diff) || !list(job.changes).isEmpty())) {
      report.append("**Agent explanation — not independent verification:** ").append(markdown(job.changeSummary)).append("\n\n");
    }
    if (job.plan != null) {
      report.append("### Proposed plan context\n\n");
      report.append("The plan expresses intended work; its presence does not prove that it was approved or completed.\n\n");
      report.append(markdown(value(job.plan.summary(), "No plan summary recorded"))).append("\n\n");
      bullets(report, job.plan.steps());
      if (!list(job.plan.risks()).isEmpty()) { report.append("\nPlan risks:\n\n"); bullets(report, job.plan.risks()); }
      report.append('\n');
    }

    overview(report, job.analysis);
    report.append("## File-by-file changes\n\n");
    var changes = new LinkedHashMap<String, Change>();
    for (Change change : list(job.changes)) if (change != null) changes.putIfAbsent(change.file(), change);
    var paths = new LinkedHashSet<String>(patch.keySet());
    paths.addAll(changes.keySet());
    if (paths.isEmpty()) report.append("No changed files were recorded.\n\n");
    for (String path : paths) {
      Change change = changes.get(path);
      PatchStats stats = patch.get(path);
      report.append("### ").append(markdown(value(path, "Unknown path"))).append("\n\n");
      if (stats == null) report.append("**Patch evidence:** No matching supported diff entry; change statistics are not verified.\n\n");
      else if (stats.binary) report.append("**Patch evidence:** Binary change recorded; text line counts do not describe binary content.\n\n");
      else report.append("**Patch evidence:** ").append(stats.additions).append(" added / ").append(stats.deletions).append(" removed text line(s).\n\n");
      report.append("**What changed (agent explanation):** ").append(markdown(change == null ? "No per-file explanation recorded; inspect the diff." : value(change.summary(), "Not recorded"))).append("\n\n");
      report.append("**Why (agent explanation):** ").append(markdown(change == null ? "Not recorded" : value(change.reason(), "Not recorded"))).append("\n\n");
    }

    report.append("## Before and after verification\n\n");
    report.append("Results below reproduce recorded verification fields. A successful build does not establish functional correctness, and a missing test count does not mean zero tests ran.\n\n");
    report.append("| Check | Recorded status | Tests | Failures | Skipped | Duration (ms) |\n| --- | --- | --- | --- | --- | --- |\n");
    verificationRow(report, "Baseline build", job.baselineBuild);
    verificationRow(report, "Baseline tests", job.baselineTests);
    verificationRow(report, "Final build", job.finalBuild);
    verificationRow(report, "Final tests", job.finalTests);
    report.append('\n');
    verificationOutput(report, "Baseline build", job.baselineBuild);
    verificationOutput(report, "Baseline tests", job.baselineTests);
    verificationOutput(report, "Final build", job.finalBuild);
    verificationOutput(report, "Final tests", job.finalTests);

    report.append("## Remaining concerns and limitations\n\n");
    if (!blank(job.error)) report.append("- Recorded error: ").append(markdown(job.error)).append('\n');
    if (job.cancelled || "CANCELLED".equals(job.status)) report.append("- The run was cancelled. Its evidence may be incomplete.\n");
    if (job.analysis == null) report.append("- Repository analysis was not recorded. No architecture conclusions can be drawn from this report.\n");
    if (job.baselineBuild == null) report.append("- Baseline build was not run or no result was recorded.\n");
    if (job.baselineTests == null) report.append("- Baseline tests were not run or no result was recorded.\n");
    if (job.finalBuild == null) report.append("- Final build was not run or no result was recorded.\n");
    if (job.finalTests == null) report.append("- Final tests were not run or no result was recorded.\n");
    bullets(report, job.concerns);
    report.append("- Static analysis covers only the supplied source snapshot. It does not prove runtime behavior or complete repository coverage.\n");
    report.append("- Obvious token and credential patterns are redacted in this document. Redaction cannot detect every possible secret; review documentation before sharing it.\n\n");

    report.append("## Files inspected during this run\n\n");
    if (list(job.inspectedFiles).isEmpty()) report.append("No explicit file-inspection record is available. Snapshot files listed above are not evidence that the reasoning agent read each file.\n\n");
    else { for (String file : job.inspectedFiles.stream().filter(Objects::nonNull).distinct().sorted().toList()) report.append("- ").append(markdown(file)).append('\n'); report.append('\n'); }

    report.append("## Reviewable patch\n\n");
    report.append("Reserved review branch: ").append(markdown(value(job.branch, "Not recorded"))).append(". Pinned source revision: ").append(markdown(value(job.sourceRevision, "Not recorded"))).append(". Source revision is not inferred from repository text.\n\n");
    if (!blank(job.id) && job.id.matches("[A-Za-z0-9_-]{1,80}")) report.append("[Download the run's patch](/api/jobs/").append(job.id).append("/diff). This link is relative to the local Codebase Doctor server.\n\n");
    if (blank(job.diff)) report.append("No patch recorded.\n");
    else {
      report.append("Recorded unified diff (obvious secrets redacted; original line order retained):\n\n");
      codeBlock(report, job.diff, "diff", false);
    }
    return report.toString();
  }

  /** A self-contained, script-free HTML document preserving the exact safe Markdown report as text. */
  public String generateHtml(Job job) {
    return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
      + "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'\">"
      + "<title>Codebase Doctor report</title><style>body{margin:0;background:#f4f6fa;color:#15263b;font:15px/1.65 system-ui,sans-serif}main{max-width:1000px;margin:36px auto;padding:36px;background:#fff;border:1px solid #d8e0ea;border-radius:16px}h1{font-size:30px;margin:0 0 8px}p{color:#526279}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:14px/1.7 ui-monospace,monospace}@media print{body{background:#fff}main{margin:0;border:0;padding:0}}</style></head><body><main><h1>Codebase Doctor</h1>"
      + "<p>Repository and change documentation · recorded run evidence</p><pre>" + html(generate(job)) + "</pre></main></body></html>";
  }

  private static void overview(StringBuilder report, Analysis analysis) {
    report.append("## Repository overview\n\n");
    if (analysis == null) { report.append("Not analyzed, or no analysis snapshot was recorded.\n\n"); return; }
    report.append("The following metadata was derived from the source snapshot captured for analysis; versions are declarations, not measured runtime versions. Source references describe that snapshot and may precede later repairs.\n\n");
    field(report, "Language", analysis.language()); field(report, "Declared Java version", analysis.javaVersion());
    field(report, "Build tool", analysis.buildTool()); field(report, "Declared Spring Boot version", analysis.springBootVersion());
    report.append("- **Snapshot files:** ").append(list(analysis.files()).size()).append("\n- **Parsed declared types:** ").append(list(analysis.symbols()).size()).append("\n\n");
    report.append("### Declared modules\n\n"); bulletsOrMissing(report, analysis.modules());
    report.append("\n### Important files in the snapshot\n\n"); bulletsOrMissing(report, analysis.importantFiles());
    report.append("\n### Architecture and code references\n\n");
    report.append("Layer labels come from source annotations or test paths. Relationships are repository type references resolved structurally from imports/packages; unresolved external types and runtime calls are not asserted.\n\n");
    if (list(analysis.symbols()).isEmpty()) report.append("No parsed types recorded.\n\n");
    else {
      report.append("| Type | Kind / layer | Source | Resolved repository type references |\n| --- | --- | --- | --- |\n");
      for (Symbol symbol : list(analysis.symbols()).stream().limit(MAX_SYMBOLS).toList()) {
        String name = blank(symbol.packageName()) ? symbol.name() : symbol.packageName() + "." + symbol.name();
        report.append("| ").append(markdown(name)).append(" | ").append(markdown(symbol.kind())).append(" / ").append(markdown(symbol.layer())).append(" | ")
          .append(markdown(symbol.file())).append(':').append(symbol.line()).append(" | ").append(markdown(list(symbol.dependencies()).isEmpty() ? "None resolved in snapshot" : String.join(", ", symbol.dependencies()))).append(" |\n");
      }
      if (list(analysis.symbols()).size() > MAX_SYMBOLS) report.append("\n").append(analysis.symbols().size() - MAX_SYMBOLS).append(" additional parsed types omitted from this report table.\n");
      report.append('\n');
    }
    report.append("### Static findings\n\n");
    if (list(analysis.findings()).isEmpty()) report.append("No findings were recorded by the available static rules. This is not a clean bill of health.\n\n");
    for (Finding finding : list(analysis.findings()).stream().limit(MAX_FINDINGS).toList()) {
      report.append("#### ").append(markdown(finding.title())).append("\n\n");
      field(report, "Classification", finding.type()); field(report, "Severity", finding.severity());
      field(report, "Source", finding.file() + (finding.line() == null ? "" : ":" + finding.line()));
      report.append("\n**Evidence:** ").append(markdown(finding.evidence())).append("\n\n**Meaning:** ").append(markdown(finding.explanation())).append("\n\n**Suggested action:** ").append(markdown(finding.suggestion())).append("\n\n");
    }
    if (list(analysis.findings()).size() > MAX_FINDINGS) report.append(analysis.findings().size() - MAX_FINDINGS).append(" additional findings omitted from the report.\n\n");
    report.append("### Analyzer observations\n\n"); bulletsOrMissing(report, analysis.observations()); report.append('\n');
  }

  private static void verificationRow(StringBuilder report, String name, Verification result) {
    report.append("| ").append(name).append(" | ").append(result == null ? "Not run" : markdown(value(result.status(), "Not reported"))).append(" | ")
      .append(count(result == null ? null : result.tests())).append(" | ").append(count(result == null ? null : result.failures())).append(" | ")
      .append(count(result == null ? null : result.skipped())).append(" | ").append(result == null || result.durationMs() < 0 ? "Not reported" : result.durationMs()).append(" |\n");
  }
  private static void verificationOutput(StringBuilder report, String name, Verification result) {
    if (result == null) return;
    report.append("### ").append(name).append(" output\n\n");
    if (blank(result.output())) report.append("No output was recorded.\n\n");
    else codeBlock(report, result.output(), "text", true);
  }
  private static String count(Integer count) { return count == null || count < 0 ? "Not reported" : count.toString(); }
  private static void field(StringBuilder report, String name, String value) { report.append("- **").append(name).append(":** ").append(markdown(value(value, "Not recorded"))).append('\n'); }
  private static void bullets(StringBuilder report, List<String> items) { for (String item : list(items)) report.append("- ").append(markdown(item)).append('\n'); }
  private static void bulletsOrMissing(StringBuilder report, List<String> items) { if (list(items).isEmpty()) report.append("Not recorded.\n"); else bullets(report, items); }
  private static <T> List<T> list(List<T> items) { return items == null ? List.of() : items; }
  private static boolean blank(String value) { return value == null || value.isBlank(); }
  private static String value(String text, String fallback) { return blank(text) ? fallback : text; }

  static String markdown(String text) {
    String input = redact(value(text, "Not recorded"));
    StringBuilder safe = new StringBuilder(input.length());
    // Encode each source character once: later escaping must not alter generated HTML entities.
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      switch (c) {
        case '&' -> safe.append("&amp;");
        case '<' -> safe.append("&lt;");
        case '>' -> safe.append("&gt;");
        case '"' -> safe.append("&quot;");
        case '\'' -> safe.append("&#39;");
        case '|' -> safe.append("&#124;");
        case '\n', '\u2028', '\u2029' -> safe.append(" · ");
        case '\\', '`', '*', '_', '[', ']', '(', ')', '!', '#', '~' -> safe.append('\\').append(c);
        default -> safe.append(Character.isISOControl(c) ? ' ' : c);
      }
    }
    // Inline fields are also reused as standalone paragraphs; avoid list/setext syntax there.
    return safe.toString().replaceFirst("^(\\s*)([-+=])", "$1\\\\$2")
      .replaceFirst("^(\\s*[0-9]+)[.](\\s)", "$1\\\\.$2");
  }
  private static String html(String text) { return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
  static String redact(String text) {
    String safe = TOKEN.matcher(text).replaceAll("[REDACTED_TOKEN]");
    safe = NAMED_SECRET.matcher(safe).replaceAll("$1[REDACTED]");
    return safe.replaceAll("(?i)(Authorization\\s*[:=]\\s*(?:Bearer|Basic)\\s+)[^\\s\\\"']+", "$1[REDACTED]");
  }
  private static void codeBlock(StringBuilder report, String content, String language, boolean truncate) {
    String safe = redact(content.replace("\u0000", ""));
    boolean shortened = truncate && safe.length() > MAX_OUTPUT_CHARACTERS;
    if (shortened) safe = safe.substring(0, MAX_OUTPUT_CHARACTERS);
    int longest = 0, current = 0;
    for (int i = 0; i < safe.length(); i++) { if (safe.charAt(i) == '`') { current++; longest = Math.max(longest, current); } else current = 0; }
    String fence = "`".repeat(Math.max(3, longest + 1));
    report.append(fence).append(language).append('\n').append(safe);
    if (!safe.endsWith("\n")) report.append('\n');
    report.append(fence).append("\n\n");
    if (shortened) report.append("Output excerpt truncated after ").append(MAX_OUTPUT_CHARACTERS).append(" characters. Consult the run's bounded command log for available additional output.\n\n");
  }

  private static class PatchStats { String oldPath, path; int additions, deletions; boolean binary; }
  private static LinkedHashMap<String, PatchStats> patchStats(String diff) {
    var result = new LinkedHashMap<String, PatchStats>();
    if (blank(diff)) return result;
    PatchStats current = null;
    boolean hunk = false;
    for (String line : diff.split("\n", -1)) {
      if (line.startsWith("diff --git ")) {
        finishPatch(result, current); current = new PatchStats(); hunk = false;
        String header = line.substring("diff --git ".length());
        PathToken oldToken = pathToken(header, 0);
        PathToken newToken = oldToken == null ? null : pathToken(header, oldToken.end());
        if (newToken != null && header.substring(newToken.end()).isBlank()) {
          current.oldPath = stripPrefix(oldToken.path(), "a/");
          current.path = stripPrefix(newToken.path(), "b/");
        }
      } else if (!hunk && line.startsWith("--- ")) {
        if (current == null) current = new PatchStats();
        current.oldPath = diffPath(line.substring(4), "a/");
      } else if (!hunk && line.startsWith("+++ ")) {
        if (current == null) current = new PatchStats();
        current.path = diffPath(line.substring(4), "b/");
      } else if (line.startsWith("@@ ") && current != null) { hunk = true;
      } else if (!hunk && current != null && line.startsWith("rename from ")) { current.oldPath = diffPath(line.substring(12), "a/");
      } else if (!hunk && current != null && line.startsWith("rename to ")) { current.path = diffPath(line.substring(10), "b/");
      } else if (current != null && line.startsWith("Binary files ")) { current.binary = true;
      } else if (hunk && current != null && line.startsWith("+")) current.additions++;
      else if (hunk && current != null && line.startsWith("-")) current.deletions++;
    }
    finishPatch(result, current);
    return result;
  }
  private record PathToken(String path, int end) {}

  /** Parses Git's quoted path syntax, including C escapes and octal UTF-8 bytes. */
  private static PathToken pathToken(String text, int from) {
    int i = from;
    while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
    if (i == text.length()) return null;
    if (text.charAt(i) != '"') {
      int start = i;
      while (i < text.length() && !Character.isWhitespace(text.charAt(i))) i++;
      return new PathToken(text.substring(start, i), i);
    }
    var bytes = new ByteArrayOutputStream();
    i++;
    while (i < text.length()) {
      char c = text.charAt(i++);
      if (c == '"') {
        try {
          String decoded = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
          return new PathToken(decoded, i);
        } catch (CharacterCodingException invalid) { return null; }
      }
      if (c == '\\') {
        if (i == text.length()) return null;
        char escape = text.charAt(i++);
        if (escape >= '0' && escape <= '7') {
          int value = escape - '0', count = 1;
          while (count < 3 && i < text.length() && text.charAt(i) >= '0' && text.charAt(i) <= '7') {
            value = value * 8 + text.charAt(i++) - '0'; count++;
          }
          if (value > 255) return null;
          bytes.write(value);
        } else {
          int value = switch (escape) {
            case '\\' -> '\\'; case '"' -> '"'; case 'a' -> 7; case 'b' -> 8;
            case 'f' -> 12; case 'n' -> 10; case 'r' -> 13; case 't' -> 9; case 'v' -> 11;
            default -> -1;
          };
          if (value < 0) return null;
          bytes.write(value);
        }
      } else {
        String literal = Character.isHighSurrogate(c) && i < text.length() && Character.isLowSurrogate(text.charAt(i))
          ? new String(new char[] { c, text.charAt(i++) }) : Character.toString(c);
        bytes.writeBytes(literal.getBytes(StandardCharsets.UTF_8));
      }
    }
    return null;
  }
  private static String diffPath(String path, String prefix) {
    if (path.startsWith("\"")) {
      PathToken token = pathToken(path, 0);
      if (token == null || (!path.substring(token.end()).isEmpty() && path.charAt(token.end()) != '\t')) return null;
      return stripPrefix(token.path(), prefix);
    }
    int tab = path.indexOf('\t'); if (tab >= 0) path = path.substring(0, tab);
    return stripPrefix(path, prefix);
  }
  private static String stripPrefix(String path, String prefix) {
    if (path.equals("/dev/null")) return null;
    return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
  }
  private static void finishPatch(Map<String, PatchStats> results, PatchStats entry) {
    if (entry == null) return;
    String path = entry.path == null ? entry.oldPath : entry.path;
    if (!blank(path)) results.put(path, entry);
  }
}
