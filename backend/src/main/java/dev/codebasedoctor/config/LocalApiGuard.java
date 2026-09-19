package dev.codebasedoctor.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class LocalApiGuard extends OncePerRequestFilter {
  private final byte[] token;
  public LocalApiGuard(@Value("${doctor.api-token}") String token) {
    if (token.length()<32) throw new IllegalStateException("DOCTOR_API_TOKEN must contain at least 32 characters. Run scripts/setup-local.sh first.");
    this.token=token.getBytes(StandardCharsets.UTF_8);
  }
  @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
    String provided=request.getHeader("X-Doctor-Token");
    response.setHeader("X-Content-Type-Options","nosniff");
    response.setHeader("Cache-Control","no-store");
    if(provided==null || !MessageDigest.isEqual(token,provided.getBytes(StandardCharsets.UTF_8))) {
      response.setStatus(401); response.setContentType("application/json");response.getWriter().write("{\"error\":\"Local API authorization required\"}");return;
    }
    if(request.getContentLengthLong()>32768) {response.sendError(413);return;}
    chain.doFilter(request,response);
  }
}
