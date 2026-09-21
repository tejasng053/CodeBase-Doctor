package dev.codebasedoctor.github;

import com.fasterxml.jackson.databind.*;
import dev.codebasedoctor.model.Models.Job;
import dev.codebasedoctor.repository.*;
import dev.codebasedoctor.service.DoctorService;
import dev.codebasedoctor.store.JobStore;
import dev.codebasedoctor.config.Redactor;
import dev.codebasedoctor.agent.SourceTools;
import dev.codebasedoctor.report.ReportGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class GitHubPublisher {
  private final BoundedHttp http;private final ObjectMapper json;private final JobStore store;private final String token;private final Redactor redactor;private final ReportGenerator reports;
  public GitHubPublisher(BoundedHttp http,ObjectMapper json,JobStore store,Redactor redactor,ReportGenerator reports,@Value("${doctor.github-token}")String token){this.http=http;this.json=json;this.store=store;this.token=token;this.redactor=redactor;this.reports=reports;}
  public boolean configured(){return !token.isBlank();}
  public Job publish(String id,String digest,boolean createDraftPr){
    Job job=store.get(id);synchronized(job){
      validateApproval(job,digest);
      if(!configured())throw new IllegalStateException("Set a fine-grained GITHUB_TOKEN with access to this repository to publish.");
      String slug=RepositoryPolicy.slug(job.repository),branch="codebase-doctor/"+job.id;
      if(!branch.matches("codebase-doctor/[a-f0-9-]{36}"))throw new IllegalStateException("Unsafe branch name.");
      var workspace=store.workspace(id);
      requireSha(job.sourceRevision);
      if(!Objects.equals(workspace.revision,job.sourceRevision)||!Objects.equals(workspace.defaultBranch,job.sourceDefaultBranch)||!Objects.equals(workspace.slug,slug)
          ||!SourceTools.diff(workspace).equals(job.diff)
          ||!SourceTools.changes(workspace,Map.of()).stream().map(c->c.file()).toList().equals(job.changes.stream().map(c->c.file()).toList()))
        throw new IllegalStateException("Saved source or changes differ from the approved review; start a new analysis.");
      for(var change:job.changes)if(!Set.of("100644","100755").contains(workspace.modes.getOrDefault(change.file(),"100644")))throw new IllegalStateException("Unsafe source file mode.");
      if(job.publishedBranchUrl==null){
        JsonNode existing=api("GET","/repos/"+slug+"/git/ref/heads/"+branch,null,200,404);
        if(!existing.isMissingNode()){
          if(job.publishedCommit==null||!job.publishedCommit.equals(existing.path("object").path("sha").asText()))throw new IllegalStateException("The proposed branch already exists with different content and will not be overwritten.");
        }else{
          JsonNode current=api("GET","/repos/"+slug+"/commits/"+encode(job.sourceDefaultBranch),null,200);
          if(!job.sourceRevision.equals(current.path("sha").asText()))throw new IllegalStateException("The default branch changed after analysis. Start a new job before publishing.");
          if(job.publishedCommit==null){
            JsonNode base=api("GET","/repos/"+slug+"/git/commits/"+job.sourceRevision,null,200);
            String tree=base.path("tree").path("sha").asText();requireSha(tree);
            List<Map<String,Object>> entries=new ArrayList<>();var files=workspace.files();
            for(var change:job.changes){String path=RepositoryPolicy.path(change.file());RepositoryPolicy.editable(path);Map<String,Object> entry=new LinkedHashMap<>();entry.put("path",path);entry.put("mode",workspace.modes.getOrDefault(path,"100644"));entry.put("type","blob");byte[] content=files.get(path);if(content==null)entry.put("sha",null);else{var blob=api("POST","/repos/"+slug+"/git/blobs",Map.of("encoding","base64","content",Base64.getEncoder().encodeToString(content)),201);String sha=blob.path("sha").asText();requireSha(sha);entry.put("sha",sha);}entries.add(entry);}
            String newTree=api("POST","/repos/"+slug+"/git/trees",Map.of("base_tree",tree,"tree",entries),201).path("sha").asText();requireSha(newTree);
            String commit=api("POST","/repos/"+slug+"/git/commits",Map.of("message","Codebase Doctor: approved repair "+job.id,"tree",newTree,"parents",List.of(job.sourceRevision)),201).path("sha").asText();requireSha(commit);job.publishedCommit=commit;store.save(job);
          }
          api("POST","/repos/"+slug+"/git/refs",Map.of("ref","refs/heads/"+branch,"sha",job.publishedCommit),201);
        }
        job.publishedBranchUrl="https://github.com/"+slug+"/tree/"+branch;refreshReport(job);store.save(job);
      }
      if(createDraftPr&&job.pullRequestUrl==null){
        String owner=slug.substring(0,slug.indexOf('/'));
        JsonNode existing=api("GET","/repos/"+slug+"/pulls?state=all&head="+encode(owner+":"+branch),null,200);
        if(existing.isArray()&&!existing.isEmpty()){
          JsonNode pr=existing.get(0);
          if(!branch.equals(pr.path("head").path("ref").asText())||!job.publishedCommit.equals(pr.path("head").path("sha").asText())||!job.sourceDefaultBranch.equals(pr.path("base").path("ref").asText())||!pr.path("draft").asBoolean())
            throw new IllegalStateException("An existing pull request no longer matches the approved draft; review it directly on GitHub.");
          String url=pr.path("html_url").asText();checkPrUrl(slug,url);job.pullRequestUrl=url;
        }
        else {String body="Approved Codebase Doctor repair.\n\n"+redactor.clean(job.changeSummary)+"\n\nSource: `"+job.sourceRevision+"`\nBuild: "+(job.finalBuild==null?"not run":job.finalBuild.status())+"\nTests: "+(job.finalTests==null?"not run":job.finalTests.status())+"\n\nReview the diff and verification before merging. This application does not merge PRs.";JsonNode pr=api("POST","/repos/"+slug+"/pulls",Map.of("title","Codebase Doctor: approved repair","body",body,"head",branch,"base",job.sourceDefaultBranch,"draft",true),201);String url=pr.path("html_url").asText();checkPrUrl(slug,url);job.pullRequestUrl=url;}
      }
      refreshReport(job);store.save(job);return store.snapshot(id);
    }
  }
  private void refreshReport(Job job){job.updatedAt=java.time.Instant.now().toString();job.reportMarkdown=redactor.clean(reports.generate(job));}
  public static void validateApproval(Job job,String digest){if(!"COMPLETED".equals(job.status)||job.cancelled||job.changes.isEmpty()||digest==null||!digest.equals(job.publicationDigest)||!digest.equals(DoctorService.publicationDigest(job)))throw new IllegalStateException("Publication requires explicit approval of the current completed diff and verification.");}
  private JsonNode api(String method,String path,Object body,int... accepted){try{var builder=HttpRequest.newBuilder(URI.create("https://api.github.com"+path)).timeout(Duration.ofSeconds(45)).header("Authorization","Bearer "+token).header("User-Agent","Codebase-Doctor/0.2").header("Accept","application/vnd.github+json").header("X-GitHub-Api-Version","2022-11-28");if(body==null)builder.GET();else builder.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)));var response=http.send(builder.build(),2_000_000);if(Arrays.stream(accepted).noneMatch(v->v==response.status()))throw new IllegalStateException("GitHub publication request failed (HTTP "+response.status()+"). Existing remote changes are recorded; retry the same approval to recover.");if(response.status()==404)return json.missingNode();return json.readTree(response.body());}catch(java.io.IOException e){throw new IllegalStateException("GitHub returned an unreadable response.");}}
  private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
  private static void requireSha(String sha){if(!sha.matches("[a-f0-9]{40}"))throw new IllegalStateException("Invalid GitHub object ID.");}
  private static void checkPrUrl(String slug,String url){if(!url.matches(java.util.regex.Pattern.quote("https://github.com/"+slug+"/pull/")+"[1-9][0-9]*"))throw new IllegalStateException("Unexpected pull request URL.");}
}
