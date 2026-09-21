package example.tokens;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/tokens")
public class TokenController {
  private final TokenExpiryService expiry;
  public TokenController(TokenExpiryService expiry) { this.expiry = expiry; }

  @PostMapping("/status")
  public TokenStatus status(@RequestBody TokenDeadline request) {
    if (request.expiresAt() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expiresAt is required");
    return new TokenStatus(request.expiresAt(), expiry.isExpired(request.expiresAt()));
  }

  public record TokenDeadline(Instant expiresAt) {}
  public record TokenStatus(Instant expiresAt, boolean expired) {}
}
