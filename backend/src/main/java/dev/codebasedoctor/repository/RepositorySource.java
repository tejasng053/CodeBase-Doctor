package dev.codebasedoctor.repository;

import com.fasterxml.jackson.databind.*;
import org.apache.commons.compress.archivers.zip.*;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.springframework.stereotype.Service;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.io.*;

@Service
public class RepositorySource {
  public record Snapshot(String slug,String revision,String defaultBranch,Map<String,byte[]> files,Map<String,String> modes) {}
  private final BoundedHttp http;private final ObjectMapper json;
  public RepositorySource(BoundedHttp http,ObjectMapper json){this.http=http;this.json=json;}
  public Snapshot fetch(String repository) {
    String slug=RepositoryPolicy.slug(repository);
    JsonNode meta=getJson("https://api.github.com/repos/"+slug);
    if(meta.path("private").asBoolean(true)||meta.path("archived").isMissingNode())throw new IllegalArgumentException("Only public GitHub repositories are supported.");
    String branch=meta.path("default_branch").asText();
    JsonNode commit=getJson("https://api.github.com/repos/"+slug+"/commits/"+URLEncoder.encode(branch,StandardCharsets.UTF_8));
    String sha=commit.path("sha").asText();if(!sha.matches("[a-f0-9]{40}"))throw new IllegalStateException("GitHub did not return a valid source revision.");
    var response=http.send(get("https://codeload.github.com/"+slug+"/zip/"+sha),16*1024*1024);
    if(response.status()!=200)throw new IllegalStateException("GitHub source download failed (HTTP "+response.status()+").");
    return unpack(slug,sha,branch,response.body());
  }
  public static Snapshot unpack(String slug,String sha,String branch,byte[] archive) {
    if(archive.length>16*1024*1024)throw new IllegalArgumentException("Archive exceeds 16 MiB.");
    Map<String,byte[]> files=new TreeMap<>();Map<String,String> modes=new TreeMap<>();int total=0,count=0;String prefix=null;
    try(var channel=new SeekableInMemoryByteChannel(archive);var zip=ZipFile.builder().setSeekableByteChannel(channel).get()) {
      var entries=zip.getEntries();
      while(entries.hasMoreElements()) {
        if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Intake cancelled.");
        var entry=entries.nextElement();if(++count>RepositoryPolicy.MAX_FILES*2)throw new IllegalArgumentException("Too many archive entries.");
        if(entry.isUnixSymlink()||((entry.getUnixMode()&0170000)!=0&&(entry.getUnixMode()&0170000)!=0100000&&(entry.getUnixMode()&0170000)!=0040000))throw new IllegalArgumentException("Symbolic links and special files are not accepted.");
        String name=entry.getName();int slash=name.indexOf('/');if(slash<1)throw new IllegalArgumentException("Archive must have one repository root.");
        if(prefix==null)prefix=name.substring(0,slash+1);if(!name.startsWith(prefix))throw new IllegalArgumentException("Archive contains multiple roots.");
        String relative=name.substring(prefix.length());if(entry.isDirectory()){if(!relative.isEmpty())RepositoryPolicy.path(relative.endsWith("/")?relative.substring(0,relative.length()-1):relative);continue;}
        RepositoryPolicy.path(relative);if(files.containsKey(relative))throw new IllegalArgumentException("Duplicate archive path.");
        byte[] value;try(var in=zip.getInputStream(entry)){value=in.readNBytes(RepositoryPolicy.MAX_FILE+1);}
        if(value.length>RepositoryPolicy.MAX_FILE||(total+=value.length)>RepositoryPolicy.MAX_TOTAL||files.size()>=RepositoryPolicy.MAX_FILES)throw new IllegalArgumentException("Repository exceeds file, count, or expanded-size limits.");
        files.put(relative,value);modes.put(relative,(entry.getUnixMode()&0111)!=0?"100755":"100644");
      }
      if(files.isEmpty())throw new IllegalArgumentException("Repository has no files.");
      return new Snapshot(slug,sha,branch,files,modes);
    }catch(IOException e){throw new IllegalArgumentException("Repository archive is invalid.");}
  }
  public String issue(String objective,String slug) {
    if(objective==null)return "";String text=objective.strip();
    if(text.matches("https://github\\.com/[^ ]+/issues/[0-9]+/?")) {
      String prefix="https://github.com/"+slug+"/issues/";
      if(!text.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("The issue must belong to the selected repository.");
      String number=text.substring(prefix.length()).replace("/","");
      JsonNode issue=getJson("https://api.github.com/repos/"+slug+"/issues/"+number);
      text=issue.path("title").asText()+"\n\n"+issue.path("body").asText();
    }
    if(text.length()>12000)text=text.substring(0,12000)+"\n[Issue context truncated]";
    return text;
  }
  private JsonNode getJson(String uri) {
    var result=http.send(get(uri),2*1024*1024);
    if(result.status()!=200)throw new IllegalStateException("GitHub public API failed (HTTP "+result.status()+"). Check the repository URL or public API rate limit.");
    try{return json.readTree(result.body());}catch(IOException e){throw new IllegalStateException("Invalid GitHub metadata.");}
  }
  private HttpRequest get(String uri){return HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(45)).header("User-Agent","Codebase-Doctor/0.2").header("Accept","application/vnd.github+json").GET().build();}
}
