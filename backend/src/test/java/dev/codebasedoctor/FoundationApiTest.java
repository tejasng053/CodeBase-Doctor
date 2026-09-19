package dev.codebasedoctor;

import dev.codebasedoctor.config.LocalApiGuard;
import dev.codebasedoctor.sandbox.DockerSandbox;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="doctor.api-token=test-only-0123456789abcdef0123456789abcdef")
@AutoConfigureMockMvc
class FoundationApiTest {
  static final String TOKEN="test-only-0123456789abcdef0123456789abcdef";
  @Autowired MockMvc api;
  @MockitoBean DockerSandbox sandbox;

  @Test void unauthenticatedClientCannotReadLocalState() throws Exception {
    api.perform(get("/api/health")).andExpect(status().isUnauthorized());
    api.perform(get("/api/jobs").header("X-Doctor-Token","wrong")).andExpect(status().isUnauthorized());
    verifyNoInteractions(sandbox);
  }
  @Test void authenticatedHealthReportsOnlyRealConfigurationAndNoExecution() throws Exception {
    when(sandbox.health()).thenReturn(Map.of("available",false,"message","Docker unavailable"));
    api.perform(get("/api/health").header("X-Doctor-Token",TOKEN)).andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("UP")).andExpect(jsonPath("$.milestone").value(1))
      .andExpect(jsonPath("$.executionEnabled").value(false)).andExpect(jsonPath("$.sandbox.available").value(false))
      .andExpect(header().string("Cache-Control","no-store"));
  }
  @Test void jobsAreEmptyAndCannotStartBeforeApprovedMilestone() throws Exception {
    api.perform(get("/api/jobs").header("X-Doctor-Token",TOKEN)).andExpect(status().isOk()).andExpect(content().json("[]"));
    api.perform(post("/api/jobs").header("X-Doctor-Token",TOKEN).contentType("application/json").content("{\"repository\":\"https://github.com/example/repository\"}"))
      .andExpect(status().isConflict()).andExpect(jsonPath("$.error").exists());
    verifyNoInteractions(sandbox);
  }
  @Test void refusesBlankOrWeakTokenConfiguration() {
    assertThrows(IllegalStateException.class,()->new LocalApiGuard(""));
    assertThrows(IllegalStateException.class,()->new LocalApiGuard("short"));
  }
  @Test void oversizedRequestsAreRejectedBeforeController() throws Exception {
    api.perform(post("/api/jobs").header("X-Doctor-Token",TOKEN).contentType("application/json").content("x".repeat(32769))).andExpect(status().isPayloadTooLarge());
  }
}
