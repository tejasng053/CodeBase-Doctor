package dev.codebasedoctor.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codebasedoctor.model.Models;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Executes source only in a bounded, offline, rootless container. Never executes repository code on the host. */
@Service
public class DockerSandbox {
  static final int OUTPUT_LIMIT = 1_048_576;
  private final ObjectMapper json;
  private final String image;
  private final CommandExecutor commands;
  @FunctionalInterface interface CommandExecutor {
    Models.CommandResult execute(List<String> command, byte[] input, int seconds, Session session, Consumer<String> output);
  }
  private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
  private final ScheduledExecutorService reaper = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread thread = new Thread(r, "sandbox-cleanup"); thread.setDaemon(true); return thread;
  });
  public record VerificationRun(Models.Verification build, Models.Verification tests) {}

  @Autowired public DockerSandbox(ObjectMapper json, @Value("${doctor.sandbox.image:codebase-doctor-sandbox:local}") String image) {
    this(json, image, DockerSandbox::process);
  }

  DockerSandbox(ObjectMapper json, String image, CommandExecutor commands) {
    this.json = json;
    this.commands = Objects.requireNonNull(commands);
    if (image == null || !image.matches("[a-zA-Z0-9][a-zA-Z0-9._/:@-]{0,200}")) throw new IllegalArgumentException("Invalid trusted sandbox image name.");
    this.image = image;
    reaper.scheduleWithFixedDelay(this::reap, 15, 15, TimeUnit.SECONDS);
  }

  public Map<String, Object> health() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("available", false); result.put("ready", false); result.put("executionEnabled", false);
    result.put("image", image); result.put("policy", "Rootless Docker, cgroup v2/systemd, seccomp, no network/host mounts, 1 CPU, 1536 MiB memory, 128 PIDs; limits verified before every run.");
    result.put("cleanupPending", sessions.values().stream().filter(s -> s.cancelled.get()).count());
    try {
      String id = requireEngine(result);
      result.put("available", true); result.put("ready", true); result.put("executionEnabled", true); result.put("imageId", id);
      result.put("message", "Engine and trusted image checks passed. Actual isolation and resource enforcement are checked inside every new container before importing source.");
    } catch (RuntimeException e) { result.put("message", e.getMessage()); }
    return result;
  }

  private String requireEngine(Map<String, Object> diagnostics) {
    try {
      Models.CommandResult info = run(List.of("docker", "info", "--format", "{{json .}}"), null, 8, null);
      requireSuccess(info, "Docker daemon is unavailable. No repository code can execute.");
      JsonNode engine = json.readTree(info.stdout());
      boolean rootless = engine.path("SecurityOptions").toString().contains("rootless");
      boolean seccomp = engine.path("SecurityOptions").toString().contains("seccomp");
      String version = engine.path("CgroupVersion").asText(), driver = engine.path("CgroupDriver").asText();
      diagnostics.put("daemonAccessible", true); diagnostics.put("rootless", rootless); diagnostics.put("seccomp", seccomp);
      diagnostics.put("cgroupVersion", version); diagnostics.put("cgroupDriver", driver);
      if (!rootless || !seccomp || !"2".equals(version) || !"systemd".equals(driver)) throw new IllegalStateException("Sandbox blocked: rootless Docker with seccomp and cgroup v2/systemd is required.");
      Models.CommandResult inspect = run(List.of("docker", "image", "inspect", image, "--format", "{{json .}}"), null, 8, null);
      requireSuccess(inspect, "Trusted sandbox image is missing. Build docker/sandbox.Dockerfile using scripts/preflight.sh instructions.");
      JsonNode spec = json.readTree(inspect.stdout()); String id = spec.path("Id").asText();
      if (!id.matches("sha256:[a-f0-9]{64}") || spec.path("Config").path("Volumes").size() != 0
          || !"1".equals(spec.path("Config").path("Labels").path("dev.codebasedoctor.guard-version").asText())) {
        throw new IllegalStateException("Sandbox image must be the trusted guard-v1 image and must declare no volumes.");
      }
      return id;
    } catch (IOException e) { throw new IllegalStateException("Docker returned invalid diagnostics; execution is blocked.", e); }
  }

  public VerificationRun verify(String jobId, Map<String, byte[]> files, String buildTool, BiConsumer<String, String> output) {
    SandboxPolicy.jobId(jobId);
    Map<String, byte[]> snapshot = SandboxPolicy.snapshot(files);
    String tool = buildTool == null ? "" : buildTool.toLowerCase(Locale.ROOT);
    if (!Set.of("maven", "gradle").contains(tool)) return new VerificationRun(unavailable("UNSUPPORTED", "No supported Maven or Gradle build was detected."), unavailable("NOT_RUN", "Tests were not run."));
    Session session = new Session("doctor-" + jobId.toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID().toString().substring(0, 8));
    if (sessions.putIfAbsent(jobId, session) != null) throw new IllegalStateException("A sandbox is already active or awaiting cleanup for this job.");
    synchronized (session) {
      try {
        String imageId = requireEngine(new LinkedHashMap<>()); check(session);
        requireSuccess(run(createCommand(session.container, imageId), null, 30, session), "Could not create hardened sandbox.");
        check(session);
        requireSuccess(run(List.of("docker", "start", session.container), null, 15, session), "Could not start hardened sandbox.");
        Models.CommandResult limits = helper(session, "limits", null, 10);
        requireSuccess(limits, "Actual sandbox isolation/resource enforcement failed; repository was not imported.");
        emit(output, "sandbox", limits.stdout());
        Map<String, String> encoded = new TreeMap<>(); snapshot.forEach((path, bytes) -> encoded.put(path, Base64.getEncoder().encodeToString(bytes)));
        requireSuccess(helper(session, "import", json.writeValueAsBytes(encoded), 30), "Source snapshot was rejected by the sandbox guard.");
        Models.CommandResult build = runBuild(session, tool, false, output);
        Models.Verification buildResult = verification(build, false, null);
        if (build.exitCode() != 0 || build.timedOut()) return new VerificationRun(buildResult, unavailable("NOT_RUN", "Tests were not started because the build did not complete successfully."));
        requireSuccess(helper(session, "clean-reports", null, 15), "Could not safely clear stale test reports.");
        Models.CommandResult tests = runBuild(session, tool, true, output);
        JsonNode counts = null; String reportProblem = "";
        try {
          Models.CommandResult reports = helper(session, "reports", null, 15);
          requireSuccess(reports, "JUnit XML reports could not be read safely."); counts = json.readTree(reports.stdout());
        } catch (RuntimeException | IOException e) { reportProblem = e.getMessage(); }
        Models.Verification testResult = verification(tests, true, counts);
        if (!reportProblem.isEmpty()) testResult = new Models.Verification("UNVERIFIED", null, null, null, display(tests) + "\n" + reportProblem, tests.durationMs());
        return new VerificationRun(buildResult, testResult);
      } catch (IOException e) { throw new IllegalStateException("Could not encode source snapshot.", e); }
      finally {
        session.cancelled.set(true); session.finished = true;
        if (removeContainer(session)) sessions.remove(jobId, session);
        else emit(output, "sandbox", "Docker cleanup could not be confirmed; automatic cleanup retries remain active. The container has a fixed 480-second lifetime.");
      }
    }
  }

  static List<String> createCommand(String container, String imageId) {
    return List.of("docker", "create", "--name", container, "--pull", "never", "--label", "dev.codebasedoctor.sandbox=true",
        "--read-only", "--network", "none", "--ipc", "private", "--cgroupns", "private", "--cap-drop", "ALL",
        "--security-opt", "no-new-privileges:true", "--memory", "1536m", "--memory-swap", "1536m", "--cpus", "1", "--pids-limit", "128",
        "--ulimit", "nofile=1024:1024", "--ulimit", "core=0:0", "--log-driver", "none", "--restart", "no",
        "--user", "10000:10001", "--workdir", "/workspace",
        "--tmpfs", "/workspace:rw,nosuid,nodev,noexec,size=512m,uid=10000,gid=10001,mode=0770",
        "--tmpfs", "/cache:rw,nosuid,nodev,noexec,size=512m,uid=10000,gid=10001,mode=0770",
        "--tmpfs", "/state:rw,nosuid,nodev,noexec,size=16m,uid=10000,gid=10000,mode=0700",
        "--tmpfs", "/tmp:rw,nosuid,nodev,noexec,size=128m,mode=1777",
        "--entrypoint", "/usr/bin/sleep", imageId, "480");
  }

  private Models.CommandResult runBuild(Session session, String tool, boolean tests, BiConsumer<String, String> output) {
    List<String> command = exec(session, 10001);
    command.addAll(List.of("/usr/bin/python3", "-I", "/opt/doctor/guard.py", tool + (tests ? "-test" : "-build")));
    Models.CommandResult result;
    String phase = tests ? "tests" : "build";
    emit(output, phase, "\n[" + phase + "]\n");
    try { result = run(command, null, 190, session, line -> emit(output, phase, line)); }
    finally {
      if (!session.cancelled.get()) {
        List<String> stop = exec(session, 10001); stop.addAll(List.of("/usr/bin/python3", "-I", "/opt/doctor/guard.py", "quiesce"));
        try { requireSuccess(run(stop, null, 10, session), "Build processes did not quiesce; sandbox must be destroyed."); }
        catch (RuntimeException e) { session.cancelled.set(true); removeContainer(session); throw e; }
      }
    }
    if (result.truncated()) emit(output, phase, "\n[Output truncated at capture limit.]\n");
    return new Models.CommandResult(result.stdout(), result.stderr(), result.exitCode(), result.durationMs(), result.timedOut() || result.exitCode() == 124, result.truncated());
  }

  static Models.Verification verification(Models.CommandResult command, boolean tests, JsonNode counts) {
    String status = command.timedOut() ? "TIMEOUT" : command.exitCode() != 0 ? "FAILED" : "PASSED";
    Integer total = null, failures = null, skipped = null;
    if (tests && counts != null) {
      total = counts.path("tests").asInt(); failures = counts.path("failures").asInt(); skipped = counts.path("skipped").asInt();
      if ("PASSED".equals(status)) status = failures > 0 ? "FAILED" : total == 0 ? "NO_TESTS" : total.equals(skipped) ? "SKIPPED" : "PASSED";
    } else if (tests && "PASSED".equals(status)) status = "UNVERIFIED";
    return new Models.Verification(status, total, failures, skipped, display(command), command.durationMs());
  }
  private static Models.Verification unavailable(String status, String message) { return new Models.Verification(status, null, null, null, message, 0); }
  private static String display(Models.CommandResult r) { return r.stdout() + (r.stderr().isBlank() ? "" : "\n" + r.stderr()) + (r.truncated() ? "\n[Output truncated at capture limit.]" : ""); }
  private static void emit(BiConsumer<String, String> output, String stage, String text) { if (output != null) output.accept(stage, text); }

  public void cancel(String jobId) {
    SandboxPolicy.jobId(jobId); Session session = sessions.get(jobId); if (session == null) return;
    session.cancelled.set(true);
    // Let the bounded docker-create RPC settle before the verifier's final removal to close the create/cancel race.
    for (Process process : session.processes) if (!session.creating.contains(process)) process.destroyForcibly();
    removeContainer(session);
  }
  public void cleanup(String jobId) {
    cancel(jobId); Session session = sessions.get(jobId); if (session == null) return;
    synchronized (session) { session.finished = true; if (removeContainer(session)) sessions.remove(jobId, session); }
  }
  private void reap() {
    sessions.forEach((id, session) -> { if (session.cancelled.get() && session.finished && removeContainer(session)) sessions.remove(id, session); });
  }
  @PreDestroy public void shutdown() { reaper.shutdownNow(); sessions.keySet().forEach(this::cancel); }
  private boolean removeContainer(Session session) {
    // Cancellation interrupts the verifier, but cleanup still gets its own bounded attempt.
    boolean interrupted = Thread.interrupted();
    try {
      Models.CommandResult removed = run(List.of("docker", "rm", "--force", session.container), null, 8, null);
      boolean removedOrAbsent = !removed.timedOut() && (removed.exitCode() == 0 || removed.stderr().contains("No such container"));
      // A cancelled/timed-out create RPC may finish in the daemon after its CLI disappears.
      // Retain its unique name and retry even when an early removal reports it absent.
      return removedOrAbsent && System.nanoTime() >= session.cleanupNotBefore;
    } catch (RuntimeException e) { return false; }
    finally { if (interrupted) Thread.currentThread().interrupt(); }
  }
  private List<String> exec(Session session, int uid) { return new ArrayList<>(List.of("docker", "exec", "--interactive", "--user", uid + ":10001", "--workdir", "/workspace", session.container)); }
  private Models.CommandResult helper(Session session, String operation, byte[] input, int seconds) {
    List<String> command = exec(session, 10000); command.addAll(List.of("/usr/bin/python3", "-I", "/opt/doctor/guard.py", operation));
    return run(command, input, seconds, session);
  }
  private static void requireSuccess(Models.CommandResult result, String message) {
    if (result.exitCode() != 0 || result.timedOut() || result.truncated()) throw new IllegalStateException(message + (result.stderr().isBlank() ? "" : " " + result.stderr().substring(0, Math.min(1500, result.stderr().length()))));
  }
  private static void check(Session session) {
    if (Thread.currentThread().isInterrupted() || (session != null && session.cancelled.get())) throw new IllegalStateException("Sandbox operation cancelled.");
  }

  private Models.CommandResult run(List<String> command, byte[] input, int seconds, Session session) {
    return run(command, input, seconds, session, null);
  }
  private Models.CommandResult run(List<String> command, byte[] input, int seconds, Session session, Consumer<String> output) {
    check(session);
    Models.CommandResult result = commands.execute(command, input, seconds, session, output);
    check(session);
    return result;
  }

  static Models.CommandResult process(List<String> command, byte[] input, int seconds, Session session, Consumer<String> output) {
    check(session); long started = System.nanoTime(); Process process = null;
    boolean creating = command.size() > 1 && command.get(1).equals("create"), completed = false;
    ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();
    try {
      ProcessBuilder builder = new ProcessBuilder(command); Map<String, String> original = new HashMap<>(builder.environment()); builder.environment().clear();
      for (String key : List.of("PATH", "HOME", "XDG_RUNTIME_DIR", "DOCKER_HOST", "DOCKER_CONTEXT", "DOCKER_CONFIG")) if (original.containsKey(key)) builder.environment().put(key, original.get(key));
      builder.environment().put("LANG", "C.UTF-8"); process = builder.start();
      if (session != null) { if (creating) session.creating.add(process); session.processes.add(process); }
      check(session); Process active = process;
      Future<Capture> out = io.submit(() -> capture(active.getInputStream(), output));
      Future<Capture> err = io.submit(() -> capture(active.getErrorStream(), output));
      Future<?> writer = io.submit(() -> { try (OutputStream stream = active.getOutputStream()) { if (input != null) stream.write(input); } return null; });
      boolean timedOut = !process.waitFor(seconds, TimeUnit.SECONDS);
      completed = !timedOut;
      if (timedOut) { process.destroyForcibly(); process.waitFor(2, TimeUnit.SECONDS); }
      check(session);
      Capture stdout = out.get(3, TimeUnit.SECONDS), stderr = err.get(3, TimeUnit.SECONDS);
      if (!timedOut && process.exitValue() == 0) writer.get(3, TimeUnit.SECONDS);
      return new Models.CommandResult(stdout.text(), stderr.text(), process.isAlive() ? -1 : process.exitValue(), (System.nanoTime() - started) / 1_000_000, timedOut, stdout.truncated() || stderr.truncated());
    } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Sandbox command interrupted.", e); }
    catch (IOException | ExecutionException | TimeoutException e) { throw new IllegalStateException("Sandbox command is unavailable or exceeded its I/O deadline.", e); }
    finally {
      if (process != null) {
        if (creating && !completed && session != null) session.cleanupNotBefore = System.nanoTime() + TimeUnit.MINUTES.toNanos(2);
        if (process.isAlive()) process.destroyForcibly();
        try { process.getInputStream().close(); } catch (IOException ignored) { }
        try { process.getErrorStream().close(); } catch (IOException ignored) { }
        try { process.getOutputStream().close(); } catch (IOException ignored) { }
        if (session != null) { session.processes.remove(process); session.creating.remove(process); }
      }
      io.shutdownNow();
    }
  }
  static Capture capture(InputStream stream, Consumer<String> output) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(), line = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192]; boolean truncated = false; int count;
    while ((count = stream.read(buffer)) != -1) {
      int keep = Math.min(count, OUTPUT_LIMIT - bytes.size()); bytes.write(buffer, 0, keep); truncated |= keep < count;
      // Complete lines keep UTF-8 sequences and redaction tokens together across pipe read boundaries.
      // A single huge line is buffered up to the same capture cap, never accumulated without bound.
      if (output != null) for (int index = 0; index < keep; index++) {
        line.write(buffer[index]);
        if (buffer[index] == '\n') { output.accept(line.toString(StandardCharsets.UTF_8)); line.reset(); }
      }
    }
    if (output != null && line.size() > 0) output.accept(line.toString(StandardCharsets.UTF_8));
    return new Capture(bytes.toString(StandardCharsets.UTF_8), truncated);
  }
  record Capture(String text, boolean truncated) {}
  static final class Session {
    final String container; final AtomicBoolean cancelled = new AtomicBoolean(); volatile boolean finished; volatile long cleanupNotBefore;
    final Set<Process> processes = ConcurrentHashMap.newKeySet(), creating = ConcurrentHashMap.newKeySet();
    Session(String container) { this.container = container; }
  }
}
