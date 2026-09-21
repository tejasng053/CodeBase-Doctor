package example.tokens;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Evaluates an existing token's deadline; does not issue or authenticate tokens. */
@Service
public class TokenExpiryService {
  private final Clock clock;
  public TokenExpiryService(Clock clock) { this.clock = Objects.requireNonNull(clock, "clock"); }

  /** A token is expired when the current time is at or after its deadline. */
  public boolean isExpired(Instant expiresAt) {
    Objects.requireNonNull(expiresAt, "expiresAt");
    return clock.instant().isAfter(expiresAt);
  }
}
