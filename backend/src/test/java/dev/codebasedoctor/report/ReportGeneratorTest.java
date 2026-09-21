package dev.codebasedoctor.report;

import dev.codebasedoctor.model.Models.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReportGeneratorTest {
  private final ReportGenerator generator = new ReportGenerator();

  private Job job() {
    Job job = new Job();
    job.id = "run-123"; job.repository = "https://github.com/example/tokens";
    job.mode = "SOLVE"; job.objective = "Fix expiry at the deadline";
    job.branch = "doctor/expiry"; job.sourceRevision = "abc123";
    return job;
  }

  @Test void absentAnalysisAndChecksAreNotReportedAsSuccessOrZeroTests() {
    String report = generator.generate(job());
    assertTrue(report.contains("No code changes are recorded"));
    assertTrue(report.contains("Not analyzed"));
    assertTrue(report.contains("| Baseline tests | Not run | Not reported | Not reported | Not reported | Not reported |"));
    assertTrue(report.contains("| Final tests | Not run | Not reported | Not reported | Not reported | Not reported |"));
    assertFalse(report.contains("0 tests"));
    assertFalse(report.contains("PASSED"));
  }

  @Test void preservesRecordedBeforeAfterResultsAndUnknownCounts() {
    Job job = job();
    job.baselineBuild = new Verification("PASSED", null, null, null, "Build completed", 200);
    job.baselineTests = new Verification("FAILED", 4, 1, 0, "ExpiryTest failed at the exact deadline", 330);
    job.finalTests = new Verification("PASSED", 4, 0, 0, "Tests run: 4, Failures: 0, Errors: 0, Skipped: 0", 350);
    String report = generator.generate(job);
    assertTrue(report.contains("| Baseline build | PASSED | Not reported | Not reported | Not reported | 200 |"));
    assertTrue(report.contains("| Baseline tests | FAILED | 4 | 1 | 0 | 330 |"));
    assertTrue(report.contains("| Final tests | PASSED | 4 | 0 | 0 | 350 |"));
    assertTrue(report.contains("ExpiryTest failed at the exact deadline"));
    assertTrue(report.contains("Final build was not run"));
  }

  @Test void derivesLineCountsFromPatchAndLabelsModelExplanation() {
    Job job = job();
    job.changeSummary = "Expiry includes the exact deadline.";
    job.diff = """
      diff --git a/src/Token.java b/src/Token.java
      index 123..456 100644
      --- a/src/Token.java
      +++ b/src/Token.java
      @@ -1 +1 @@
      -return now.isAfter(expiresAt);
      +return !now.isBefore(expiresAt);
      """;
    job.changes.add(new Change("src/Token.java", "Changed the comparison", "An equal instant must expire", 999, 888));
    String report = generator.generate(job);
    assertTrue(report.contains("1 added / 1 removed text line(s)"));
    assertFalse(report.contains("999")); assertFalse(report.contains("888"));
    assertTrue(report.contains("Agent explanation — not independent verification"));
    assertTrue(report.contains("Why (agent explanation)"));
    assertTrue(report.contains("/api/jobs/run-123/diff"));
    assertTrue(report.contains("Pinned source revision:** abc123"));
  }

  @Test void structuredChangesWithoutPatchDoNotClaimVerifiedStatistics() {
    Job job = job(); job.changes.add(new Change("A.java", "Change", "Reason", 4, 2));
    String report = generator.generate(job);
    assertTrue(report.contains("no patch is available to verify their scope"));
    assertTrue(report.contains("change statistics are not verified"));
    assertFalse(report.contains("4 added"));
  }

  @Test void deletionAndCreationStatisticsUseTheRightFile() {
    Job job = job();
    job.diff = """
      diff --git a/Old.java b/Old.java
      deleted file mode 100644
      --- a/Old.java
      +++ /dev/null
      @@ -1 +0,0 @@
      -class Old {}
      diff --git a/New.java b/New.java
      new file mode 100644
      --- /dev/null
      +++ b/New.java
      @@ -0,0 +1 @@
      +class New {}
      """;
    String report = generator.generate(job);
    assertTrue(report.contains("### Old.java")); assertTrue(report.contains("### New.java"));
    assertTrue(report.contains("0 added / 1 removed")); assertTrue(report.contains("1 added / 0 removed"));
    assertFalse(report.contains("### /dev/null"));
  }

  @Test void quotedPathsMatchActualSourceToolsDiffWithoutDuplicateChangeEntries() {
    var workspace = new dev.codebasedoctor.store.JobStore.Workspace();
    String path = "src/Quoted \"type\".java";
    workspace.original.put(path, "old\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    workspace.edits.put(path, "new\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    Job job = job();
    job.diff = dev.codebasedoctor.agent.SourceTools.diff(workspace);
    job.changes = dev.codebasedoctor.agent.SourceTools.changes(workspace, Map.of());
    String report = generator.generate(job);
    assertTrue(report.contains("1 file(s) with supported diff headers, 1 added text line(s), 1 removed text line(s)"));
    assertTrue(report.contains("### src/Quoted &quot;type&quot;.java"));
    assertFalse(report.contains("No matching supported diff entry"));
    assertEquals(1, report.split("1 added / 1 removed text line", -1).length - 1);
  }

  @Test void octalQuotedGitPathsAreDecodedAsUtf8() {
    Job job = job();
    job.diff = "diff --git \"a/caf\\303\\251.java\" \"b/caf\\303\\251.java\"\n"
      + "--- \"a/caf\\303\\251.java\"\n+++ \"b/caf\\303\\251.java\"\n@@ -1 +1 @@\n-old\n+new\n";
    assertTrue(generator.generate(job).contains("### café.java"));
  }

  @Test void malformedQuotedPathsDoNotInventStatistics() {
    Job job = job();
    job.diff = "diff --git \"a/bad\\q.java\" \"b/bad\\q.java\"\n"
      + "--- \"a/bad\\q.java\"\n+++ \"b/bad\\q.java\"\n@@ -1 +1 @@\n-old\n+new\n";
    assertTrue(generator.generate(job).contains("its file statistics could not be derived"));
  }

  @Test void entityEncodingDoesNotBreakApostrophesOrPermitStandaloneLists() {
    assertEquals("Doctor&#39;s &#124; repo &amp; findings", ReportGenerator.markdown("Doctor's | repo & findings"));
    assertEquals("\\---", ReportGenerator.markdown("---"));
    assertEquals("1\\. invented list", ReportGenerator.markdown("1. invented list"));
  }

  @Test void binaryDiffIsIncludedWithoutPretendingTextCountsDescribeItsContents() {
    Job job = job(); job.diff = "diff --git a/icon.png b/icon.png\nindex 123..456 100644\nBinary files a/icon.png and b/icon.png differ\n";
    assertTrue(generator.generate(job).contains("Binary change recorded; text line counts do not describe binary content"));
  }

  @Test void untrustedTextCannotInjectHtmlLinksHeadingsOrTables() {
    Job job = job();
    job.objective = "<script>alert(1)</script>\n# fake heading\n![tracking](https://attacker.invalid/x) | column";
    job.id = "x](https://attacker.invalid)";
    String report = generator.generate(job);
    assertFalse(report.contains("<script>"));
    assertFalse(report.contains("\n# fake heading"));
    assertFalse(report.contains("![tracking]"));
    assertFalse(report.contains("/api/jobs/x]"));
    assertTrue(report.contains("&lt;script&gt;"));
    assertTrue(report.contains("&#124; column"));
    assertFalse(report.contains("&\\#"));
    String html = generator.generateHtml(job);
    assertFalse(html.contains("<script>"));
    assertFalse(html.contains("<img"));
    assertTrue(html.contains("default-src 'none'"));
  }

  @Test void embeddedCodeFencesCannotBreakOutOfOutputBlock() {
    Job job = job();
    job.finalTests = new Verification("FAILED", null, null, null, "````\n<script>evil()</script>\n````", 2);
    String report = generator.generate(job);
    assertTrue(report.contains("`````text\n````\n<script>evil()</script>\n````\n`````"));
    assertFalse(generator.generateHtml(job).contains("<script>evil()"));
  }

  @Test void reportRedactsRecognizableTokensAndCredentialAssignments() {
    Job job = job();
    job.finalTests = new Verification("FAILED", null, null, null,
      "GROQ_API_KEY=gsk_thisisnotarealkey123456789\nAuthorization: Bearer abcdef0123456789\npassword=very-secret-value", 1);
    String report = generator.generate(job);
    assertFalse(report.contains("gsk_thisisnotarealkey123456789"));
    assertFalse(report.contains("abcdef0123456789"));
    assertFalse(report.contains("very-secret-value"));
    assertTrue(report.contains("[REDACTED]"));
  }

  @Test void architectureShowsOnlyGivenSymbolsAndDistinguishesInspectionFromSnapshot() {
    Job job = job();
    job.analysis = new Analysis("Java", "21", "Maven", "3.5.16", List.of("."), List.of("pom.xml"), List.of("pom.xml", "src/Token.java"),
      List.of(new Symbol("Token", "example", "record", "unclassified", "src/Token.java", 3, 4, List.of(), List.of())),
      List.of(new Finding("Potential issue", "suspected", "low", "src/Token.java", 4, "A source pattern", "Needs review", "Inspect the contract")), List.of("Static snapshot only"));
    String report = generator.generate(job);
    assertTrue(report.contains("example.Token")); assertTrue(report.contains("src/Token.java:3"));
    assertTrue(report.contains("Classification:** suspected"));
    assertTrue(report.contains("Snapshot files:** 2"));
    assertTrue(report.contains("No explicit file-inspection record is available"));
    assertFalse(report.contains("controller →"));
  }

  @Test void cancelledAndTruncatedRunsExposeTheirLimits() {
    Job job = job(); job.cancelled = true; job.error = "Stopped by user";
    job.finalTests = new Verification("TIMED_OUT", null, null, null, "x".repeat(33_000), 10_000);
    String report = generator.generate(job);
    assertTrue(report.contains("State:** CANCELLED"));
    assertTrue(report.contains("Output excerpt truncated after 32768 characters"));
    assertTrue(report.contains("The run was cancelled"));
    assertTrue(report.contains("TIMED\\_OUT"));
  }

  @Test void planPresenceDoesNotClaimApprovalOrCompletion() {
    Job job = job(); job.plan = new Plan("Fix expiry", List.of("Inspect contract", "Change comparison"), List.of("Token.java"), List.of("Expiry boundary may change behavior"));
    String report = generator.generate(job);
    assertTrue(report.contains("does not prove that it was approved or completed"));
  }
}
