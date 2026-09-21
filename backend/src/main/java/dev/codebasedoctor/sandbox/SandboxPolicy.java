package dev.codebasedoctor.sandbox;

import java.util.*;

/** Independently validate even snapshots that have already passed repository intake. */
final class SandboxPolicy {
  static final int MAX_FILES = 5_000, MAX_FILE_BYTES = 2 * 1024 * 1024, MAX_SNAPSHOT_BYTES = 24 * 1024 * 1024;
  private SandboxPolicy() {}
  static void jobId(String id) {
    if (id == null || !id.matches("[a-zA-Z0-9][a-zA-Z0-9-]{0,63}"))
      throw new IllegalArgumentException("Invalid sandbox job identifier.");
  }
  static void path(String path) {
    if (path == null || path.isBlank() || path.length() > 512 || path.startsWith("/") || path.contains("\\") || path.contains(":"))
      throw new IllegalArgumentException("Invalid source path.");
    for (int i = 0; i < path.length(); i++) if (Character.isISOControl(path.charAt(i))) throw new IllegalArgumentException("Control character in source path.");
    String[] parts = path.split("/", -1);
    if (parts.length > 32) throw new IllegalArgumentException("Source path exceeds depth limit.");
    for (String part : parts) if (part.isEmpty() || part.equals(".") || part.equals("..") || part.equalsIgnoreCase(".git"))
      throw new IllegalArgumentException("Unsafe source path.");
  }
  static Map<String, byte[]> snapshot(Map<String, byte[]> source) {
    if (source == null || source.isEmpty() || source.size() > MAX_FILES) throw new IllegalArgumentException("Snapshot must contain 1–5000 files.");
    Map<String, byte[]> copy = new TreeMap<>(); long total = 0;
    for (Map.Entry<String, byte[]> item : source.entrySet()) {
      path(item.getKey());
      if (item.getValue() == null || item.getValue().length > MAX_FILE_BYTES) throw new IllegalArgumentException("Source file exceeds 2 MiB.");
      total += item.getValue().length;
      if (total > MAX_SNAPSHOT_BYTES) throw new IllegalArgumentException("Source snapshot exceeds 24 MiB.");
      copy.put(item.getKey(), item.getValue().clone());
    }
    for (String path : copy.keySet()) {
      int slash = path.indexOf('/');
      while (slash >= 0) {
        if (copy.containsKey(path.substring(0, slash))) throw new IllegalArgumentException("Source file conflicts with directory.");
        slash = path.indexOf('/', slash + 1);
      }
    }
    return copy;
  }
}
