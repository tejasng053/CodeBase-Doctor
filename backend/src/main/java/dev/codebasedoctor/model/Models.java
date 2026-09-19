package dev.codebasedoctor.model;

import java.time.Instant;
import java.util.*;

public final class Models {
  private Models() {}
  public record Event(long sequence, String timestamp, String stage, String tool, String status, String message, long durationMs) {}
  public record Finding(String title, String type, String severity, String file, Integer line, String evidence, String explanation, String suggestion) {}
  public record Symbol(String name, String packageName, String kind, String layer, String file, int line, int lines, List<String> annotations, List<String> dependencies) {}
  public record Analysis(String language, String javaVersion, String buildTool, String springBootVersion, List<String> modules, List<String> importantFiles, List<String> files, List<Symbol> symbols, List<Finding> findings, List<String> observations) {}
  public record CommandResult(String stdout, String stderr, int exitCode, long durationMs, boolean timedOut, boolean truncated) {}
  public record Verification(String status, Integer tests, Integer failures, Integer skipped, String output, long durationMs) {}
  public record Plan(String summary, List<String> steps, List<String> files, List<String> risks) {}
  public record Change(String file, String summary, String reason, int additions, int deletions) {}
  public static class Job {
    public String id, repository, objective, mode, status="QUEUED", stage="Clone", createdAt=Instant.now().toString(), updatedAt=createdAt;
    public String branch, error, diff="", reportMarkdown="", changeSummary="No code changes have been made.", approvalDigest;
    public Analysis analysis;
    public Plan plan;
    public Verification baselineBuild, baselineTests, finalBuild, finalTests;
    public List<Event> events=new ArrayList<>();
    public List<Change> changes=new ArrayList<>();
    public List<String> inspectedFiles=new ArrayList<>();
    public List<String> concerns=new ArrayList<>();
    public boolean cancelled=false;
    public Job() {}
  }
}
