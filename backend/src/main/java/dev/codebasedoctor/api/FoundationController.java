package dev.codebasedoctor.api;

import dev.codebasedoctor.model.Models.Job;
import dev.codebasedoctor.service.DoctorService;
import dev.codebasedoctor.github.GitHubPublisher;
import dev.codebasedoctor.sandbox.DockerSandbox;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@RestController
@RequestMapping("/api")
public class FoundationController {
  public record CreateRequest(String repository,String objective,String mode){}
  public record Approval(String approvalDigest){}
  public record PublishRequest(String publicationDigest,boolean createDraftPr){}
  private final DockerSandbox sandbox;private final DoctorService doctor;private final GitHubPublisher publisher;
  private final ScheduledExecutorService streams=Executors.newScheduledThreadPool(2);
  public FoundationController(DockerSandbox sandbox,DoctorService doctor,GitHubPublisher publisher){this.sandbox=sandbox;this.doctor=doctor;this.publisher=publisher;}
  @GetMapping("/health")public Map<String,Object> health(){var health=sandbox.health();return Map.of("status","UP","ingestionEnabled",true,"executionEnabled",Boolean.TRUE.equals(health.get("available")),"groqConfigured",doctor.groqConfigured(),"publishingConfigured",publisher.configured(),"sandbox",health,"message","Read-only analysis is available. Repairs require a model, approved plan, and enforced sandbox.");}
  @GetMapping("/jobs")public List<Job> jobs(){return doctor.list();}
  @PostMapping("/jobs")public Job create(@RequestBody CreateRequest request){return doctor.create(request.repository(),request.objective(),request.mode()==null?"SCAN":request.mode());}
  @GetMapping("/jobs/{id}")public Job get(@PathVariable String id){return doctor.get(id);}
  @PostMapping("/jobs/{id}/approve")public Job approve(@PathVariable String id,@RequestBody Approval approval){return doctor.approve(id,approval.approvalDigest());}
  @PostMapping("/jobs/{id}/cancel")public Job cancel(@PathVariable String id){return doctor.cancel(id);}
  @PostMapping("/jobs/{id}/publish")public Job publish(@PathVariable String id,@RequestBody PublishRequest request){return publisher.publish(id,request.publicationDigest(),request.createDraftPr());}
  @GetMapping("/jobs/{id}/report")public ResponseEntity<String> report(@PathVariable String id){Job job=doctor.get(id);if(job.reportMarkdown.isBlank())throw new IllegalStateException("Report is still being prepared.");return download(job.reportMarkdown,"text/markdown; charset=utf-8","doctor-report-"+job.id+".md");}
  @GetMapping("/jobs/{id}/report.html")public ResponseEntity<String> html(@PathVariable String id){doctor.get(id);return ResponseEntity.ok().header("Content-Type","text/html; charset=utf-8").header("Content-Disposition","attachment; filename=doctor-report-"+id+".html").header("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'; sandbox").body(doctor.html(id));}
  @GetMapping("/jobs/{id}/diff")public ResponseEntity<String> diff(@PathVariable String id){Job job=doctor.get(id);return download(job.diff,"text/plain; charset=utf-8","doctor-changes-"+job.id+".patch");}
  @GetMapping(value="/jobs/{id}/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE)public SseEmitter events(@PathVariable String id){doctor.get(id);SseEmitter emitter=new SseEmitter(300_000L);AtomicReference<ScheduledFuture<?>> handle=new AtomicReference<>();AtomicBoolean closed=new AtomicBoolean();Runnable stop=()->{closed.set(true);ScheduledFuture<?> future=handle.get();if(future!=null)future.cancel(false);};emitter.onCompletion(stop);emitter.onTimeout(()->{stop.run();emitter.complete();});emitter.onError(error->stop.run());AtomicReference<String> previous=new AtomicReference<>("");ScheduledFuture<?> future=streams.scheduleAtFixedRate(()->{if(closed.get())return;try{Job job=doctor.get(id);if(!Objects.equals(previous.get(),job.updatedAt)){emitter.send(SseEmitter.event().name("snapshot").id(job.updatedAt).reconnectTime(2000).data(job));previous.set(job.updatedAt);}else emitter.send(SseEmitter.event().comment("keepalive"));}catch(Exception e){stop.run();emitter.complete();}},0,1,TimeUnit.SECONDS);handle.set(future);if(closed.get())future.cancel(false);return emitter;}
  private ResponseEntity<String> download(String body,String type,String filename){return ResponseEntity.ok().header("Content-Type",type).header("Content-Disposition","attachment; filename="+filename).body(body);}
  @PreDestroy public void close(){streams.shutdownNow();}
}
