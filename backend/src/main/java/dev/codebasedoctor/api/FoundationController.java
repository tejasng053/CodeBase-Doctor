package dev.codebasedoctor.api;

import dev.codebasedoctor.sandbox.DockerSandbox;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class FoundationController {
  private final DockerSandbox sandbox;
  private final boolean groqConfigured,githubConfigured;
  public FoundationController(DockerSandbox sandbox,@Value("${doctor.groq-key}") String groq,@Value("${doctor.github-token}") String github) {
    this.sandbox=sandbox;groqConfigured=!groq.isBlank();githubConfigured=!github.isBlank();
  }
  @GetMapping("/health") public Map<String,Object> health() {
    return Map.of("status","UP","milestone",1,"executionEnabled",false,"message","Milestone 1 foundation. Repository workflows require the next approved milestone.","groqConfigured",groqConfigured,"githubConfigured",githubConfigured,"sandbox",sandbox.health());
  }
  @GetMapping("/jobs") public List<Object> jobs() {return List.of();}
  @PostMapping("/jobs") public ResponseEntity<Map<String,String>> createJob() {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error","Repository execution is disabled in Milestone 1. Complete and approve the safety and ingestion milestones first."));
  }
}
