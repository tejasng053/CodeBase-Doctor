package dev.codebasedoctor.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codebasedoctor.model.Models;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** All Docker operations are simulated; these tests never start Docker or run repository programs. */
class DockerSandboxTest {
  static final ObjectMapper JSON = new ObjectMapper();
  static final String IMAGE = "sha256:" + "a".repeat(64);
  static final Map<String, byte[]> SOURCE = Map.of("pom.xml", "<project/>".getBytes(StandardCharsets.UTF_8));
  static Models.CommandResult result(String output, int code) { return new Models.CommandResult(output, "", code, 5, false, false); }

  static class FakeDocker implements DockerSandbox.CommandExecutor {
    final List<List<String>> calls = new CopyOnWriteArrayList<>();
    final Map<String, Models.CommandResult> overrides = new HashMap<>();
    Consumer<List<String>> hook = command -> {};
    byte[] imported;
    @Override public Models.CommandResult execute(List<String> command, byte[] input, int seconds, DockerSandbox.Session session, Consumer<String> output) {
      calls.add(List.copyOf(command)); hook.accept(command);
      String operation = command.getLast();
      if (command.get(1).equals("info")) return overrides.getOrDefault("info", result("{\"SecurityOptions\":[\"name=rootless\",\"name=seccomp\"],\"CgroupVersion\":\"2\",\"CgroupDriver\":\"systemd\"}", 0));
      if (command.get(1).equals("image")) return result("{\"Id\":\"" + IMAGE + "\",\"Config\":{\"Labels\":{\"dev.codebasedoctor.guard-version\":\"1\"}}}", 0);
      if (operation.equals("import")) imported = input;
      if (operation.equals("reports")) return overrides.getOrDefault("reports", result("{\"tests\":3,\"failures\":0,\"skipped\":1}", 0));
      if (output != null) { output.accept("real fixture output one\n"); output.accept("real fixture output two\n"); }
      return overrides.getOrDefault(operation, result("", 0));
    }
    long operationCount(String operation) { return calls.stream().filter(c -> c.getLast().equals(operation)).count(); }
    long removals() { return calls.stream().filter(c -> c.get(1).equals("rm")).count(); }
  }

  @Test void containerCommandHasRequiredIsolationAndNoHostMountsOrSecrets() {
    List<String> command = DockerSandbox.createCommand("doctor-test", IMAGE);
    for (String flag : List.of("--read-only", "--cap-drop", "--security-opt", "--memory", "--memory-swap", "--cpus", "--pids-limit", "--log-driver")) assertTrue(command.contains(flag), flag);
    assertEquals("none", command.get(command.indexOf("--network") + 1));
    assertEquals("ALL", command.get(command.indexOf("--cap-drop") + 1));
    assertEquals("no-new-privileges:true", command.get(command.indexOf("--security-opt") + 1));
    assertEquals("1536m", command.get(command.indexOf("--memory") + 1));
    assertEquals("1536m", command.get(command.indexOf("--memory-swap") + 1));
    assertEquals("128", command.get(command.indexOf("--pids-limit") + 1));
    assertEquals("1", command.get(command.indexOf("--cpus") + 1));
    assertEquals("never", command.get(command.indexOf("--pull") + 1));
    assertEquals(List.of("/usr/bin/sleep", IMAGE, "480"), command.subList(command.size() - 3, command.size()));
    assertFalse(command.stream().anyMatch(c -> Set.of("--privileged", "--mount", "--volume", "-v", "--env", "-e").contains(c) || c.contains("docker.sock")));
    assertEquals(4, command.stream().filter("--tmpfs"::equals).count());
    command.stream().filter(c -> c.startsWith("/") && c.contains(":rw")).forEach(c -> assertTrue(c.contains("nosuid,nodev,noexec,size=")));
  }

  @Test void successfulVerificationChecksLimitsBeforeImportAndCollectsRealEvidence() throws Exception {
    FakeDocker docker = new FakeDocker(); DockerSandbox sandbox = new DockerSandbox(JSON, "trusted:local", docker);
    try {
      List<String> output = new ArrayList<>();
      var run = sandbox.verify("success", SOURCE, "Maven", (stage, chunk) -> output.add(chunk));
      assertEquals("PASSED", run.build().status()); assertEquals("PASSED", run.tests().status());
      assertEquals(3, run.tests().tests()); assertEquals(1, run.tests().skipped());
      List<String> operations = docker.calls.stream().map(List::getLast).toList();
      assertTrue(operations.indexOf("limits") < operations.indexOf("import"));
      assertTrue(operations.indexOf("import") < operations.indexOf("maven-build"));
      assertTrue(operations.indexOf("quiesce") < operations.indexOf("clean-reports"));
      assertEquals(2, docker.operationCount("quiesce")); assertEquals(1, docker.removals());
      assertEquals("<project/>", new String(Base64.getDecoder().decode(JSON.readTree(docker.imported).get("pom.xml").asText()), StandardCharsets.UTF_8));
      assertEquals(2, output.stream().filter("real fixture output one\n"::equals).count());
      for (List<String> command : docker.calls) {
        assertEquals("docker", command.getFirst());
        if (command.get(1).equals("exec")) assertTrue(command.contains("-I"));
      }
      // A confirmed cleanup releases the unique session for a later verification of the same job.
      sandbox.verify("success", SOURCE, "Maven", null);
      assertEquals(2, docker.removals());
    } finally { sandbox.shutdown(); }
  }

  @Test void missingIsolationBlocksSourceImportAndAlwaysRemovesContainer() {
    FakeDocker docker = new FakeDocker(); docker.overrides.put("limits", result("unsafe limits", 1));
    DockerSandbox sandbox = new DockerSandbox(JSON, "trusted:local", docker);
    try {
      assertThrows(IllegalStateException.class, () -> sandbox.verify("limits", SOURCE, "Maven", null));
      assertNull(docker.imported); assertEquals(0, docker.operationCount("maven-build")); assertEquals(1, docker.removals());
    } finally { sandbox.shutdown(); }
  }

  @Test void rootfulEngineIsBlockedBeforeAnyContainerIsCreated() {
    FakeDocker docker = new FakeDocker();
    docker.overrides.put("info", result("{\"SecurityOptions\":[\"name=seccomp\"],\"CgroupVersion\":\"2\",\"CgroupDriver\":\"systemd\"}", 0));
    DockerSandbox sandbox = new DockerSandbox(JSON, "trusted:local", docker);
    try {
      assertThrows(IllegalStateException.class, () -> sandbox.verify("rootful", SOURCE, "Maven", null));
      assertFalse(docker.calls.stream().anyMatch(c -> c.get(1).equals("create"))); assertNull(docker.imported);
    } finally { sandbox.shutdown(); }
  }

  @Test void failedBuildSkipsTestsAndStillQuiescesAndCleansUp() {
    FakeDocker docker = new FakeDocker(); docker.overrides.put("gradle-build", result("offline dependency unavailable", 1));
    DockerSandbox sandbox = new DockerSandbox(JSON, "trusted:local", docker);
    try {
      var run = sandbox.verify("failed", SOURCE, "Gradle", null);
      assertEquals("FAILED", run.build().status()); assertEquals("NOT_RUN", run.tests().status());
      assertEquals(0, docker.operationCount("gradle-test")); assertEquals(1, docker.operationCount("quiesce")); assertEquals(1, docker.removals());
    } finally { sandbox.shutdown(); }
  }

  @Test void cancellationBeforeBuildPreventsAnyRepositoryCommand() {
    FakeDocker docker = new FakeDocker(); AtomicReference<DockerSandbox> reference = new AtomicReference<>();
    docker.hook = command -> { if (command.getLast().equals("import")) reference.get().cancel("cancelled"); };
    DockerSandbox sandbox = new DockerSandbox(JSON, "trusted:local", docker); reference.set(sandbox);
    try {
      assertThrows(IllegalStateException.class, () -> sandbox.verify("cancelled", SOURCE, "Maven", null));
      assertEquals(0, docker.operationCount("maven-build")); assertTrue(docker.removals() >= 1);
    } finally { sandbox.shutdown(); }
  }

  @Test void interruptedVerifierStillGetsBoundedCleanupAndKeepsInterruptFlag() {
    FakeDocker docker = new FakeDocker();
    docker.hook = command -> { if (command.getLast().equals("maven-build")) Thread.currentThread().interrupt(); };
    DockerSandbox sandbox = new DockerSandbox(JSON, "trusted:local", docker);
    try {
      assertThrows(IllegalStateException.class, () -> sandbox.verify("interrupted", SOURCE, "Maven", null));
      assertTrue(Thread.currentThread().isInterrupted()); assertTrue(docker.removals() >= 1);
    } finally { Thread.interrupted(); sandbox.shutdown(); }
  }

  @Test void testCountsDoNotTurnNoTestsSkippedOrUnverifiedIntoSuccess() throws Exception {
    assertEquals("NO_TESTS", DockerSandbox.verification(result("", 0), true, JSON.readTree("{\"tests\":0,\"failures\":0,\"skipped\":0}")).status());
    assertEquals("SKIPPED", DockerSandbox.verification(result("", 0), true, JSON.readTree("{\"tests\":2,\"failures\":0,\"skipped\":2}")).status());
    assertEquals("FAILED", DockerSandbox.verification(result("", 0), true, JSON.readTree("{\"tests\":2,\"failures\":1,\"skipped\":0}")).status());
    assertEquals("UNVERIFIED", DockerSandbox.verification(result("", 0), true, null).status());
    assertEquals("FAILED", DockerSandbox.verification(result("killed", 137), false, null).status());
  }

  @Test void outputStreamingPreservesLinesAcrossByteReadsAndRemainsBounded() throws Exception {
    byte[] content = "utf8 café\nsecond line\nlast".getBytes(StandardCharsets.UTF_8);
    List<String> lines = new ArrayList<>();
    ByteArrayInputStream shortReads = new ByteArrayInputStream(content) {
      @Override public synchronized int read(byte[] bytes, int offset, int length) { return super.read(bytes, offset, Math.min(length, 3)); }
    };
    var capture = DockerSandbox.capture(shortReads, lines::add);
    assertEquals(List.of("utf8 café\n", "second line\n", "last"), lines);
    assertEquals(new String(content, StandardCharsets.UTF_8), capture.text()); assertFalse(capture.truncated());
    List<String> capped = new ArrayList<>();
    var huge = DockerSandbox.capture(new ByteArrayInputStream("x".repeat(DockerSandbox.OUTPUT_LIMIT + 50).getBytes(StandardCharsets.UTF_8)), capped::add);
    assertTrue(huge.truncated()); assertEquals(DockerSandbox.OUTPUT_LIMIT, huge.text().length());
    assertEquals(DockerSandbox.OUTPUT_LIMIT, capped.stream().mapToInt(String::length).sum());
  }
}
