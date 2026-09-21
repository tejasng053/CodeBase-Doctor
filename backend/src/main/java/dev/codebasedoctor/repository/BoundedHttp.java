package dev.codebasedoctor.repository;

import org.springframework.stereotype.Component;
import java.net.http.*;
import java.io.*;
import java.time.Duration;
import java.util.concurrent.*;

@Component
public class BoundedHttp {
  private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
  public record Result(int status,byte[] body) {}
  public Result send(HttpRequest request,int limit) {
    try {
      var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
      try(InputStream stream=response.body()) {
        ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"bounded-http-deadline");t.setDaemon(true);return t;});
        var deadline=timer.schedule(()->{try{stream.close();}catch(IOException ignored){}},45,TimeUnit.SECONDS);
        try {
          ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int read;
          while((read=stream.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedException();if(out.size()+read>limit)throw new IllegalStateException("Remote response exceeds the configured size limit.");out.write(buffer,0,read);}
          return new Result(response.statusCode(),out.toByteArray());
        }finally{deadline.cancel(false);timer.shutdownNow();}
      }
    }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Remote request cancelled.");}
    catch(IOException e){throw new IllegalStateException("Remote request failed or timed out.");}
  }
}
