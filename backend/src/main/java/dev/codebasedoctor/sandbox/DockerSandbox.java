package dev.codebasedoctor.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Milestone 1 exposes read-only engine diagnostics. Repository execution is not implemented or enabled. */
@Service
public class DockerSandbox {
  private final ObjectMapper json;

  public DockerSandbox(ObjectMapper json) {
    this.json = json;
  }

  public Map<String, Object> health() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("available", false);
    result.put("ready", false);
    result.put("executionEnabled", false);
    result.put("dockerInstalled", false);
    result.put("daemonAccessible", false);
    result.put("rootless", false);
    result.put("cgroupVersion", "unknown");
    result.put("cgroupDriver", "unknown");
    result.put("policy", "Execution remains disabled in Milestone 1. Future execution requires rootless Docker, cgroup v2/systemd, and per-container resource enforcement.");
    try {
      Probe version = probe(List.of("docker", "--version"));
      if (version.exitCode() != 0) {
        result.put("message", "Docker CLI is unavailable. Repository execution is disabled.");
        return result;
      }
      result.put("dockerInstalled", true);
      result.put("dockerVersion", version.output().strip());
      Probe info = probe(List.of("docker", "info", "--format", "{{json .}}"));
      if (info.exitCode() != 0) {
        result.put("message", "Docker CLI is installed, but its daemon is inaccessible. Repository execution is disabled.");
        return result;
      }
      JsonNode engine = json.readTree(info.output());
      boolean rootless = engine.path("SecurityOptions").toString().contains("rootless");
      String cgroups = engine.path("CgroupVersion").asText("unknown");
      String driver = engine.path("CgroupDriver").asText("unknown");
      result.put("daemonAccessible", true);
      result.put("rootless", rootless);
      result.put("cgroupVersion", cgroups);
      result.put("cgroupDriver", driver);
      result.put("enginePolicySupported", rootless && "2".equals(cgroups) && "systemd".equals(driver));
      result.put("message", "Docker diagnostics were read successfully. Repository execution is disabled until the sandbox milestone is implemented and verified.");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      result.put("message", "Docker diagnostics were interrupted. Repository execution is disabled.");
    } catch (IOException | RuntimeException e) {
      result.put("message", "Docker diagnostics are unavailable. Repository execution is disabled.");
    }
    return result;
  }

  private static Probe probe(List<String> command) throws IOException, InterruptedException {
    ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
    Map<String, String> environment = new LinkedHashMap<>(builder.environment());
    builder.environment().clear();
    // Only the CLI's connection configuration is inherited. API provider credentials never reach child processes.
    for (String key : List.of("PATH", "HOME", "XDG_RUNTIME_DIR", "DOCKER_HOST", "DOCKER_CONTEXT", "DOCKER_CONFIG")) {
      if (environment.containsKey(key)) builder.environment().put(key, environment.get(key));
    }
    Process process = builder.start();
    try {
      // A large output will fill the pipe and hit this deadline rather than allocate unbounded host memory.
      if (!process.waitFor(4, TimeUnit.SECONDS)) return new Probe(-1, "");
      return new Probe(process.exitValue(), new String(process.getInputStream().readNBytes(32_768), StandardCharsets.UTF_8));
    } finally {
      if (process.isAlive()) process.destroyForcibly();
      process.getInputStream().close();
      process.getOutputStream().close();
      process.getErrorStream().close();
    }
  }

  private record Probe(int exitCode, String output) {}
}
