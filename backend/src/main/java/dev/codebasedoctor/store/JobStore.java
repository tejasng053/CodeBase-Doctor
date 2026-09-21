package dev.codebasedoctor.store;

import com.fasterxml.jackson.databind.*;
import dev.codebasedoctor.model.Models.Job;
import dev.codebasedoctor.repository.RepositorySource.Snapshot;
import dev.codebasedoctor.repository.RepositoryPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.io.*;
import java.util.*;

@Service
public class JobStore {
  public static class Workspace {
    public String revision,defaultBranch,slug;
    public Map<String,byte[]> original=new TreeMap<>(),edits=new TreeMap<>();
    public Map<String,String> modes=new TreeMap<>();
    public Set<String> deleted=new TreeSet<>();
    public Workspace(){}
    public Workspace(Snapshot s){revision=s.revision();defaultBranch=s.defaultBranch();slug=s.slug();original.putAll(s.files());modes.putAll(s.modes());}
    public Map<String,byte[]> files(){Map<String,byte[]> result=new TreeMap<>(original);result.putAll(edits);deleted.forEach(result::remove);return result;}
  }
  private final ObjectMapper mapper;private final Path directory;
  private final Map<String,Job> jobs=new LinkedHashMap<>();private final Map<String,Workspace> workspaces=new HashMap<>();
  public JobStore(ObjectMapper mapper,@Value("${doctor.data-directory}")String directory)throws IOException {
    this.mapper=mapper;this.directory=Path.of(directory).toAbsolutePath().normalize();Files.createDirectories(this.directory);
    try{Files.setPosixFilePermissions(this.directory,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));}catch(UnsupportedOperationException ignored){}
    try(var files=Files.list(this.directory)) {
      for(Path path:files.filter(p->p.getFileName().toString().matches("[a-f0-9-]{36}\\.json")).sorted().limit(20).toList()) {
        if(Files.isSymbolicLink(path)||Files.size(path)>8_000_000)continue;
        try {
          Job job=mapper.readValue(path.toFile(),Job.class);
          if(!path.getFileName().toString().equals(job.id+".json"))continue;
          if(Set.of("QUEUED","RUNNING","AWAITING_APPROVAL").contains(job.status)){job.status="FAILED";job.error="Server restarted. Start a fresh analysis; automatic resumption is disabled.";job.approvalDigest=null;job.publicationDigest=null;}
          jobs.put(job.id,job);
        }catch(IOException ignored){}
      }
    }
  }
  public synchronized void add(Job job){if(jobs.size()>=20)throw new IllegalStateException("Local history is limited to 20 jobs. Archive the backend data folder manually before starting a fresh history.");save(job);}
  public synchronized Job get(String id){Job job=jobs.get(id);if(job==null)throw new NoSuchElementException("Job not found.");return job;}
  public Job snapshot(String id){Job job=get(id);synchronized(job){return mapper.convertValue(job,Job.class);}}
  public List<Job> list(){List<Job> references;synchronized(this){references=new ArrayList<>(jobs.values());}return references.stream().map(j->{synchronized(j){return mapper.convertValue(j,Job.class);}}).sorted(Comparator.comparing((Job j)->j.createdAt).reversed()).toList();}
  public synchronized void save(Job job){validate(job.id);write(job.id+".json",job,8_000_000);jobs.put(job.id,job);}
  public synchronized void saveWorkspace(String id,Workspace value){validate(id);write(id+".snapshot",value,70_000_000);workspaces.put(id,value);}
  public synchronized Workspace workspace(String id){validate(id);if(workspaces.containsKey(id))return workspaces.get(id);Path p=directory.resolve(id+".snapshot");try{if(Files.isSymbolicLink(p)||Files.size(p)>70_000_000)throw new IOException();Workspace w=mapper.readValue(p.toFile(),Workspace.class);w.files().keySet().forEach(RepositoryPolicy::path);workspaces.put(id,w);return w;}catch(IOException e){throw new IllegalStateException("Source snapshot is unavailable; run a new analysis.");}}
  public synchronized void release(String id){workspaces.remove(id);}
  private void write(String name,Object value,int limit){try{byte[] data=mapper.writeValueAsBytes(value);if(data.length>limit)throw new IllegalStateException("Local snapshot exceeded its size limit.");Path temp=directory.resolve(name+".tmp");if(Files.isSymbolicLink(temp))throw new IllegalStateException("Unsafe local data path.");Files.write(temp,data,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);Files.move(temp,directory.resolve(name),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(IOException e){throw new IllegalStateException("Could not save the local job snapshot.");}}
  private static void validate(String id){if(id==null||!id.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Invalid job ID.");}
}
