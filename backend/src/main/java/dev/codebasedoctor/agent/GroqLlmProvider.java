package dev.codebasedoctor.agent;

import com.fasterxml.jackson.databind.*;
import dev.codebasedoctor.repository.BoundedHttp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Service
public class GroqLlmProvider implements LlmProvider {
  private final ObjectMapper json;private final BoundedHttp http;private final String key,model;
  public GroqLlmProvider(ObjectMapper json,BoundedHttp http,@Value("${doctor.groq-key}")String key,@Value("${doctor.groq-model}")String model){this.json=json;this.http=http;this.key=key;this.model=model;}
  public boolean configured(){return !key.isBlank()&&!model.isBlank();}
  public JsonNode complete(List<Map<String,Object>> messages,List<Map<String,Object>> tools){
    if(!configured())throw new IllegalStateException("Configure GROQ_API_KEY and GROQ_MODEL for AI planning.");
    try {
      byte[] body=json.writeValueAsBytes(Map.of("model",model,"messages",messages,"tools",tools,"tool_choice","auto","parallel_tool_calls",false,"temperature",0.2,"max_completion_tokens",4096));
      if(body.length>150_000)throw new IllegalStateException("Agent context budget reached; use a narrower objective.");
      var request=HttpRequest.newBuilder(URI.create("https://api.groq.com/openai/v1/chat/completions")).timeout(Duration.ofSeconds(60)).header("Authorization","Bearer "+key).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
      var response=http.send(request,256_000);if(response.status()!=200)throw new IllegalStateException("Groq request failed (HTTP "+response.status()+"). Check model, quota, and configuration.");
      return parseResponse(json.readTree(response.body()));
    }catch(java.io.IOException e){throw new IllegalStateException("Invalid Groq response.");}
  }
  public static JsonNode parseResponse(JsonNode response){JsonNode message=response.path("choices").path(0).path("message");if(!message.isObject()||!"assistant".equals(message.path("role").asText()))throw new IllegalStateException("Invalid assistant response.");JsonNode calls=message.path("tool_calls");if(!calls.isMissingNode()&&(!calls.isArray()||calls.size()>4))throw new IllegalStateException("Invalid tool call batch.");return message;}
}
