package dev.codebasedoctor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codebasedoctor.agent.LlmProvider;
import dev.codebasedoctor.analysis.JavaSpringAnalyzer;
import dev.codebasedoctor.config.Redactor;
import dev.codebasedoctor.model.Models.*;
import dev.codebasedoctor.repository.RepositoryPolicy;
import dev.codebasedoctor.repository.RepositorySource;
import dev.codebasedoctor.report.ReportGenerator;
import dev.codebasedoctor.sandbox.DockerSandbox;
import dev.codebasedoctor.store.JobStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Offline integration: external source, model and execution boundaries are explicit mocks. */
class DoctorServiceTest {
  @TempDir Path directory;
  private final ObjectMapper json=new ObjectMapper();
  private final RepositorySource source=mock(RepositorySource.class);
  private final DockerSandbox sandbox=mock(DockerSandbox.class);
  private final LlmProvider llm=mock(LlmProvider.class);
  private final Redactor redactor=new Redactor("local-test-token", "", "");
  private static final String SOURCE="src/main/java/Main.java", BEFORE="class Main { int value() { return 1; } }\n";
  private static final String POM="<project><modelVersion>4.0.0</modelVersion><groupId>test</groupId><artifactId>demo</artifactId><version>1</version><properties><maven.compiler.release>21</maven.compiler.release></properties></project>";
  private JobStore store;
  private DoctorService service;

  @BeforeEach void setup()throws Exception {
    store=new JobStore(json,directory.toString());
    service=new DoctorService(store,source,new JavaSpringAnalyzer(),sandbox,llm,new ReportGenerator(),json,redactor);
    when(source.fetch("https://github.com/test/demo")).thenReturn(new RepositorySource.Snapshot("test/demo","a".repeat(40),"main",Map.of(SOURCE,bytes(BEFORE),"pom.xml",bytes(POM)),Map.of()));
    when(source.issue(anyString(),eq("test/demo"))).thenAnswer(i->i.getArgument(0));
    when(sandbox.health()).thenReturn(Map.of("available",true,"message","Mock execution boundary"));
    when(sandbox.verify(anyString(),anyMap(),eq("Maven"),any())).thenReturn(verification("PASSED"));
  }
  @AfterEach void cleanup(){service.stop();}

  @Test void exactPlanApprovalGuardsRepairAndRecordsActualChanges()throws Exception {
    when(llm.configured()).thenReturn(true);
    when(llm.complete(anyList(),anyList())).thenReturn(plan(),tool("read_file",Map.of("path",SOURCE)),
      tools(call("apply_patch",patch("pom.xml",RepositoryPolicy.digest(bytes(POM)),"demo","unsafe")),call("apply_patch",patch(SOURCE,RepositoryPolicy.digest(bytes(BEFORE)),"return 1","return 2"))),
      tool("finish",Map.of("summary","Corrected the return value.","concerns",List.of())));
    when(sandbox.verify(anyString(),anyMap(),eq("Maven"),any())).thenReturn(verification("FAILED"),verification("PASSED"));
    String id=service.create("https://github.com/test/demo","Correct the return value","SOLVE").id;
    await(()->"AWAITING_APPROVAL".equals(service.get(id).status)&&!service.get(id).reportMarkdown.isBlank());
    Job planned=service.get(id);
    assertTrue(planned.changes.isEmpty());
    assertEquals(BEFORE,new String(store.workspace(id).files().get(SOURCE),StandardCharsets.UTF_8));
    assertThrows(IllegalStateException.class,()->service.approve(id,"stale"));
    service.approve(id,planned.approvalDigest);
    await(()->"COMPLETED".equals(service.get(id).status)&&service.get(id).reportMarkdown.contains("Corrected the return value."));
    Job repaired=service.get(id);
    assertEquals("FAILED",repaired.baselineTests.status());
    assertEquals("PASSED",repaired.finalTests.status());
    assertEquals(List.of(SOURCE),repaired.changes.stream().map(Change::file).toList());
    assertEquals(POM,new String(store.workspace(id).files().get("pom.xml"),StandardCharsets.UTF_8));
    assertEquals(BEFORE,new String(store.workspace(id).original.get(SOURCE),StandardCharsets.UTF_8));
    assertTrue(repaired.diff.contains("+class Main { int value() { return 2; } }"));
    assertTrue(repaired.events.stream().anyMatch(e->e.tool().equals("apply_patch")&&e.status().equals("FAILED")));
    assertEquals(DoctorService.publicationDigest(repaired),repaired.publicationDigest);
    assertTrue(repaired.reportMarkdown.contains("Mock tests: PASSED"));
    verify(sandbox,times(2)).verify(eq(id),anyMap(),eq("Maven"),any());
    assertThrows(IllegalStateException.class,()->service.approve(id,planned.approvalDigest));
  }

  @Test void missingSandboxBlocksApprovedRepairsWithoutChangingThePlan()throws Exception {
    when(llm.configured()).thenReturn(true);
    when(llm.complete(anyList(),anyList())).thenReturn(plan());
    when(sandbox.health()).thenReturn(Map.of("available",false,"message","Docker permission unavailable"));
    String id=service.create("https://github.com/test/demo","Correct value","SOLVE").id;
    await(()->"AWAITING_APPROVAL".equals(service.get(id).status));
    String digest=service.get(id).approvalDigest;
    assertThrows(IllegalStateException.class,()->service.approve(id,digest));
    assertEquals("AWAITING_APPROVAL",service.get(id).status);
    assertEquals(digest,service.get(id).approvalDigest);
    assertEquals("NOT_RUN",service.get(id).baselineTests.status());
    verify(sandbox,never()).verify(anyString(),anyMap(),anyString(),any());
  }

  @Test void cancelledRepairCannotApplyALateModelResponse()throws Exception {
    when(llm.configured()).thenReturn(true);
    CountDownLatch repairing=new CountDownLatch(1),release=new CountDownLatch(1);
    when(llm.complete(anyList(),anyList())).thenReturn(plan()).thenAnswer(i->{
      repairing.countDown();awaitUninterruptibly(release);
      return tool("apply_patch",patch(SOURCE,RepositoryPolicy.digest(bytes(BEFORE)),"return 1","return 2"));
    });
    String id=service.create("https://github.com/test/demo","Correct value","SOLVE").id;
    await(()->"AWAITING_APPROVAL".equals(service.get(id).status));
    service.approve(id,service.get(id).approvalDigest);
    assertTrue(repairing.await(5,TimeUnit.SECONDS));
    try {assertEquals("CANCELLED",service.cancel(id).status);}finally{release.countDown();}
    verify(sandbox,timeout(5000).times(2)).cleanup(id);
    Job cancelled=service.get(id);
    assertEquals("CANCELLED",cancelled.status);
    assertNull(cancelled.publicationDigest);
    assertNull(cancelled.approvalDigest);
    assertTrue(cancelled.changes.isEmpty());
    assertEquals(BEFORE,new String(store.workspace(id).files().get(SOURCE),StandardCharsets.UTF_8));
    verify(sandbox).cancel(id);
    verify(sandbox,times(1)).verify(eq(id),anyMap(),eq("Maven"),any());
  }

  @Test void finalVerificationReturningAfterCancellationCannotMarkJobCompleted()throws Exception {
    when(llm.configured()).thenReturn(true);
    when(llm.complete(anyList(),anyList())).thenReturn(plan(),tool("finish",Map.of("summary","No edits needed.","concerns",List.of())));
    CountDownLatch verifying=new CountDownLatch(1),release=new CountDownLatch(1);
    when(sandbox.verify(anyString(),anyMap(),eq("Maven"),any())).thenReturn(verification("PASSED")).thenAnswer(i->{verifying.countDown();awaitUninterruptibly(release);return verification("PASSED");});
    String id=service.create("https://github.com/test/demo","Correct value","SOLVE").id;
    await(()->"AWAITING_APPROVAL".equals(service.get(id).status));
    service.approve(id,service.get(id).approvalDigest);
    assertTrue(verifying.await(5,TimeUnit.SECONDS));
    try{service.cancel(id);}finally{release.countDown();}
    verify(sandbox,timeout(5000).times(2)).cleanup(id);
    assertEquals("CANCELLED",service.get(id).status);
    assertNull(service.get(id).publicationDigest);
    assertNull(service.get(id).finalTests);
  }

  @Test void reportFailureStillCleansTheSandboxAndInvalidatesPublication()throws Exception {
    service.stop();
    ReportGenerator reports=mock(ReportGenerator.class);
    when(reports.generate(any())).thenThrow(new IllegalStateException("disk report rendering failure"));
    service=new DoctorService(store,source,new JavaSpringAnalyzer(),sandbox,llm,reports,json,redactor);
    String id=service.create("https://github.com/test/demo","","SCAN").id;
    verify(sandbox,timeout(5000)).cleanup(id);
    assertNull(service.get(id).publicationDigest);
    assertTrue(service.get(id).concerns.stream().anyMatch(c->c.contains("persist the final report")));
  }

  private JsonNode plan(){return tool("submit_plan",Map.of("summary","Correct return value","steps",List.of("Inspect Main","Apply one correction","Run tests"),"files",List.of(SOURCE),"risks",List.of("Check callers")));}
  private Map<String,Object> patch(String path,String digest,String before,String after){return Map.of("path",path,"expectedDigest",digest,"operation","replace","oldText",before,"newText",after,"reason","Correct the diagnosed return value.");}
  private JsonNode tool(String name,Object args){return tools(call(name,args));}
  private Map<String,Object> call(String name,Object args){try{return Map.of("id",UUID.randomUUID().toString(),"type","function","function",Map.of("name",name,"arguments",json.writeValueAsString(args)));}catch(Exception e){throw new AssertionError(e);}}
  @SafeVarargs private JsonNode tools(Map<String,Object>... calls){return json.valueToTree(Map.of("role","assistant","tool_calls",Arrays.asList(calls)));}
  private static DockerSandbox.VerificationRun verification(String tests){return new DockerSandbox.VerificationRun(new Verification("PASSED",null,null,null,"Mock build passed",10),new Verification(tests,1,tests.equals("FAILED")?1:0,0,"Mock tests: "+tests,20));}
  private static byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
  private static void await(BooleanSupplier condition){assertTimeoutPreemptively(Duration.ofSeconds(5),()->{while(!condition.getAsBoolean())Thread.sleep(10);});}
  private static void awaitUninterruptibly(CountDownLatch latch){boolean interrupted=false;try{while(true){try{if(!latch.await(5,TimeUnit.SECONDS))throw new AssertionError("Test latch timed out");return;}catch(InterruptedException e){interrupted=true;}}}finally{if(interrupted)Thread.currentThread().interrupt();}}
}
