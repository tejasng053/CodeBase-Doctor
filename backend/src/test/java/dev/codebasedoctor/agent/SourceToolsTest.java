package dev.codebasedoctor.agent;
import dev.codebasedoctor.repository.RepositoryPolicy;
import dev.codebasedoctor.store.JobStore.Workspace;
import dev.codebasedoctor.model.Models.*;
import dev.codebasedoctor.service.DoctorService;
import dev.codebasedoctor.github.GitHubPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SourceToolsTest {
 private Workspace workspace(){var w=new Workspace();w.original.put("src/Main.java","class Main { int value=1; }\n".getBytes());return w;}
 @Test void approvedPatchRequiresReadDigestAndUniqueMatch(){var w=workspace();String sha=RepositoryPolicy.digest(w.original.get("src/Main.java"));assertThrows(IllegalArgumentException.class,()->SourceTools.patch(w,Set.of(),"src/Main.java",sha,"value=1","value=2","replace"));assertThrows(IllegalArgumentException.class,()->SourceTools.patch(w,Set.of("src/Main.java"),"src/Main.java","stale","value=1","value=2","replace"));SourceTools.patch(w,Set.of("src/Main.java"),"src/Main.java",sha,"value=1","value=2","replace");assertTrue(SourceTools.read(w,"src/Main.java").contains("value=2"));String diff=SourceTools.diff(w);assertTrue(diff.contains("-class Main { int value=1; }"));assertTrue(diff.contains("+class Main { int value=2; }"));assertTrue(new String(w.original.get("src/Main.java")).contains("value=1"));}
 @Test void createAndDeleteAreVisibleInDiff(){var w=workspace();SourceTools.patch(w,Set.of("src/New.java"),"src/New.java","missing","","class New {}","create");assertTrue(SourceTools.diff(w).contains("new file mode"));SourceTools.patch(w,Set.of("src/Main.java"),"src/Main.java",RepositoryPolicy.digest(w.original.get("src/Main.java")),"","","delete");assertTrue(SourceTools.diff(w).contains("deleted file mode"));}
 @Test void modifiedApprovalDigestCannotPublish(){Job j=new Job();j.sourceRevision="a".repeat(40);j.status="COMPLETED";j.diff="original";j.changes.add(new Change("Main.java","change","why",1,1));j.publicationDigest=DoctorService.publicationDigest(j);assertDoesNotThrow(()->GitHubPublisher.validateApproval(j,j.publicationDigest));j.diff="different";assertThrows(IllegalStateException.class,()->GitHubPublisher.validateApproval(j,j.publicationDigest));}
 @Test void groqResponseMustUseAssistantRole()throws Exception{ObjectMapper json=new ObjectMapper();assertThrows(IllegalStateException.class,()->GroqLlmProvider.parseResponse(json.readTree("{\"choices\":[{\"message\":{\"role\":\"system\"}}]}")));assertTrue(GroqLlmProvider.parseResponse(json.readTree("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"x\"}}]}")).isObject());}
}
