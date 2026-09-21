package dev.codebasedoctor.agent;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public interface LlmProvider {
  boolean configured();
  JsonNode complete(List<Map<String,Object>> messages,List<Map<String,Object>> tools);
}
