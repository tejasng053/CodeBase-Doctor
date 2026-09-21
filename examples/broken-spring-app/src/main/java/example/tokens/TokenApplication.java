package example.tokens;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class TokenApplication {
  public static void main(String[] args) { SpringApplication.run(TokenApplication.class, args); }
  @Bean Clock clock() { return Clock.systemUTC(); }
}
