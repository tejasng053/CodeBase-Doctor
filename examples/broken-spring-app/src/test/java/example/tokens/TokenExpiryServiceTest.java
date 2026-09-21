package example.tokens;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenExpiryServiceTest {
  private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
  private final TokenExpiryService service = new TokenExpiryService(Clock.fixed(NOW, ZoneOffset.UTC));

  @Test void deadlineInThePastIsExpired() { assertTrue(service.isExpired(NOW.minusNanos(1))); }
  @Test void deadlineInTheFutureIsStillValid() { assertFalse(service.isExpired(NOW.plusNanos(1))); }
  @Test void tokenIsExpiredAtItsExactDeadline() { assertTrue(service.isExpired(NOW)); }
  @Test void missingDeadlineIsRejected() { assertThrows(NullPointerException.class, () -> service.isExpired(null)); }
}
