package dev.codebasedoctor.github;

import com.fasterxml.jackson.databind.*;
import dev.codebasedoctor.agent.SourceTools;
import dev.codebasedoctor.config.Redactor;
import dev.codebasedoctor.model.Models.*;
import dev.codebasedoctor.report.ReportGenerator;
import dev.codebasedoctor.repository.*;
import dev.codebasedoctor.service.DoctorService;
import dev.codebasedoctor.store.JobStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.net.http.HttpRequest;
import java.util.*;
import java.util.concurrent.*;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** External GitHub mutations are mocked; this test never connects to GitHub. */
class GitHubPublisherTest {
  @TempDir Path data;
  ObjectMapper json=new ObjectMapper();BoundedHttp http=mock(BoundedHttp.class);
  JobStore store;Job job;GitHubPublisher publisher;List<HttpRequest> requests=new ArrayList<>();
  String source="a".repeat(40),commit="d".repeat(40);String current=source;boolean collision=false,refCreated=false;
  @BeforeEach void setup()throws Exception {
    store=new JobStore(json,data.toString());job=new Job();job.id=UUID.randomUUID().toString();job.repository="https://github.com/owner/demo";job.sourceRevision=source;job.sourceDefaultBranch="main";job.status="COMPLETED";job.branch="codebase-doctor/"+job.id;
    var w=new JobStore.Workspace(new RepositorySource.Snapshot("owner/demo",source,"main",Map.of("A.java","class A {}\n".getBytes()),Map.of("A.java","100644")));
    SourceTools.patch(w,Set.of("A.java"),"A.java",RepositoryPolicy.digest(w.original.get("A.java")),"class A {}","class A { int n; }","replace");
    job.changes=SourceTools.changes(w,Map.of("A.java","Add field"));job.diff=SourceTools.diff(w);job.finalBuild=new Verification("PASSED",null,null,null,"Test fixture only",1);job.finalTests=new Verification("PASSED",1,0,0,"Test fixture only",1);job.publicationDigest=DoctorService.publicationDigest(job);store.add(job);store.saveWorkspace(job.id,w);
    publisher=new GitHubPublisher(http,json,store,new Redactor("","","test-token"),new ReportGenerator(),"test-token");
    when(http.send(any(),anyInt())).thenAnswer(inv->{HttpRequest request=inv.getArgument(0);requests.add(request);String path=request.uri().getPath();Object body;int status=200;
      if(path.contains("/git/ref/heads/")){if(!refCreated&&!collision)return new BoundedHttp.Result(404,"{}".getBytes());body=Map.of("object",Map.of("sha",collision?"e".repeat(40):commit));}
      else if(path.endsWith("/commits/main"))body=Map.of("sha",current);
      else if(path.endsWith("/git/commits/"+source))body=Map.of("tree",Map.of("sha","b".repeat(40)));
      else if(path.endsWith("/git/blobs")||path.endsWith("/git/trees")){status=201;body=Map.of("sha","c".repeat(40));}
      else if(path.endsWith("/git/commits")){status=201;body=Map.of("sha",commit);}
      else if(path.endsWith("/git/refs")){status=201;refCreated=true;body=Map.of("ref","refs/heads/"+job.branch);}
      else if(path.endsWith("/pulls")&&request.method().equals("GET"))body=List.of();
      else if(path.endsWith("/pulls")){status=201;body=Map.of("html_url","https://github.com/owner/demo/pull/1");}
      else throw new AssertionError("Unexpected request "+request.uri());
      return new BoundedHttp.Result(status,json.writeValueAsBytes(body));});
  }
  @Test void publishesOnlyNewDoctorBranchAndDraftThenRetriesWithoutDuplicateWrites()throws Exception {
    Job result=publisher.publish(job.id,job.publicationDigest,true);
    assertEquals("https://github.com/owner/demo/pull/1",result.pullRequestUrl);
    assertTrue(result.reportMarkdown.contains(result.pullRequestUrl));
    for(HttpRequest request:requests){assertEquals("api.github.com",request.uri().getHost());assertNotEquals("PATCH",request.method());
      if(request.method().equals("POST")){
        JsonNode body=body(request);
        if(request.uri().getPath().endsWith("/git/refs"))assertEquals("refs/heads/"+job.branch,body.path("ref").asText());
        if(request.uri().getPath().endsWith("/pulls")){assertTrue(body.path("draft").asBoolean());assertEquals("main",body.path("base").asText());}
        if(request.uri().getPath().endsWith("/git/commits"))assertEquals(source,body.path("parents").get(0).asText());
      }
    }
    int count=requests.size();publisher.publish(job.id,job.publicationDigest,true);assertEquals(count,requests.size());
  }
  @Test void staleApprovalAndModifiedSavedSnapshotCannotPublish(){
    assertThrows(IllegalStateException.class,()->publisher.publish(job.id,"stale",true));assertTrue(requests.isEmpty());
    store.workspace(job.id).edits.put("A.java","class A { int tampered; }".getBytes());
    assertThrows(IllegalStateException.class,()->publisher.publish(job.id,job.publicationDigest,true));assertTrue(requests.isEmpty());
  }
  @Test void movedDefaultBranchCannotCreateAnyObjects(){current="f".repeat(40);assertThrows(IllegalStateException.class,()->publisher.publish(job.id,job.publicationDigest,true));assertTrue(requests.stream().allMatch(r->r.method().equals("GET")));}
  @Test void existingDifferentBranchIsNeverOverwritten(){collision=true;assertThrows(IllegalStateException.class,()->publisher.publish(job.id,job.publicationDigest,true));assertEquals(1,requests.size());assertEquals("GET",requests.getFirst().method());}
  private JsonNode body(HttpRequest request)throws Exception {
    ByteArrayOutputStream bytes=new ByteArrayOutputStream();CompletableFuture<Void> done=new CompletableFuture<>();
    request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>(){
      public void onSubscribe(Flow.Subscription subscription){subscription.request(Long.MAX_VALUE);}
      public void onNext(ByteBuffer value){byte[] chunk=new byte[value.remaining()];value.get(chunk);bytes.writeBytes(chunk);}
      public void onError(Throwable e){done.completeExceptionally(e);}public void onComplete(){done.complete(null);}
    });done.get(1,TimeUnit.SECONDS);return json.readTree(bytes.toByteArray());
  }
}
