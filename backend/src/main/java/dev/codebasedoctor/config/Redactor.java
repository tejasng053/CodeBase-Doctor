package dev.codebasedoctor.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Redactor {
  private final String[] secrets;
  public Redactor(@Value("${doctor.api-token}")String local,@Value("${doctor.groq-key}")String groq,@Value("${doctor.github-token}")String github){secrets=new String[]{local,groq,github};}
  public String clean(String value){String result=value==null?"":value;for(String secret:secrets)if(secret!=null&&!secret.isBlank())result=result.replace(secret,"[REDACTED]");return result.replaceAll("(?i)(gsk_|gh[pousr]_)[A-Za-z0-9_]{16,}","[REDACTED]").replaceAll("(?i)(password|secret|api[_-]?key|access[_-]?token)(\\s*[=:]\\s*)[^\\s,;]+","$1$2[REDACTED]");}
}
