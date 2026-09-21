package dev.codebasedoctor.sandbox;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SandboxPolicyTest {
  @Test void rejectsPathsThatCouldEscapeOrReplaceGitMetadata() {
    for (String path : List.of("../escape", "/etc/passwd", "a/../b", "a\\b", "C:drive", ".git/config", "a/.GIT/HEAD", "a//b", "a/", "a\0b", "a/./b", "x/".repeat(33) + "a"))
      assertThrows(IllegalArgumentException.class, () -> SandboxPolicy.snapshot(Map.of(path, new byte[1])), path);
    assertThrows(IllegalArgumentException.class, () -> SandboxPolicy.jobId("name;echo unsafe"));
  }
  @Test void rejectsCollisionsAndOversizedSnapshotsBeforeDockerRuns() {
    assertThrows(IllegalArgumentException.class, () -> SandboxPolicy.snapshot(Map.of("a", new byte[1], "a/b", new byte[1])));
    assertThrows(IllegalArgumentException.class, () -> SandboxPolicy.snapshot(Map.of("huge", new byte[SandboxPolicy.MAX_FILE_BYTES + 1])));
    Map<String, byte[]> tooMany = new HashMap<>(); for (int i = 0; i <= SandboxPolicy.MAX_FILES; i++) tooMany.put("file" + i, new byte[0]);
    assertThrows(IllegalArgumentException.class, () -> SandboxPolicy.snapshot(tooMany));
    Map<String, byte[]> oversized = new HashMap<>(); for (int i = 0; i < 13; i++) oversized.put("file" + i, new byte[SandboxPolicy.MAX_FILE_BYTES]);
    assertThrows(IllegalArgumentException.class, () -> SandboxPolicy.snapshot(oversized));
  }
  @Test void snapshotsOwnCopiesSoCallerMutationCannotChangeImportedBytes() {
    byte[] source = new byte[]{1, 2, 3}; var copy = SandboxPolicy.snapshot(Map.of("src/App.java", source));
    source[0] = 9; assertArrayEquals(new byte[]{1, 2, 3}, copy.get("src/App.java"));
  }
}
