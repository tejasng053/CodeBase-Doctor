package dev.codebasedoctor.agent;

import dev.codebasedoctor.repository.RepositoryPolicy;
import dev.codebasedoctor.store.JobStore.Workspace;
import dev.codebasedoctor.model.Models.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class SourceTools {
  private SourceTools(){}
  public static String read(Workspace workspace,String path){RepositoryPolicy.path(path);if(!RepositoryPolicy.modelReadable(path))throw new IllegalArgumentException("Sensitive file is excluded from model context.");return RepositoryPolicy.text(workspace.files().get(path));}
  public static void patch(Workspace workspace,Set<String> approved,String path,String expectedDigest,String oldText,String newText,String operation){
    RepositoryPolicy.editable(path);if(!approved.contains(path))throw new IllegalArgumentException("File is outside the approved plan.");
    if(newText==null||newText.getBytes(StandardCharsets.UTF_8).length>200_000)throw new IllegalArgumentException("Patch text is too large.");
    Map<String,byte[]> files=workspace.files();byte[] original=files.get(path);
    String content;
    switch(operation){
      case "create" -> {if(original!=null)throw new IllegalArgumentException("File already exists.");if(!"missing".equals(expectedDigest))throw new IllegalArgumentException("New files require expectedDigest=missing.");content=newText;}
      case "replace","delete" -> {if(original==null)throw new IllegalArgumentException("File not found.");if(!RepositoryPolicy.digest(original).equals(expectedDigest))throw new IllegalArgumentException("File changed since inspection; read it again.");String before=RepositoryPolicy.text(original);if(operation.equals("delete")){workspace.edits.remove(path);workspace.deleted.add(path);return;}if(oldText==null||oldText.isEmpty()||before.indexOf(oldText)<0||before.indexOf(oldText)!=before.lastIndexOf(oldText))throw new IllegalArgumentException("The old text must match exactly one location.");content=before.replace(oldText,newText);}
      default -> throw new IllegalArgumentException("Operation must be create, replace, or delete.");
    }
    byte[] updated=content.getBytes(StandardCharsets.UTF_8);if(updated.length>RepositoryPolicy.MAX_FILE)throw new IllegalArgumentException("Edited file is too large.");
    long total=files.values().stream().mapToLong(v->v.length).sum()-(original==null?0:original.length)+updated.length;
    if(total>RepositoryPolicy.MAX_TOTAL||(!files.containsKey(path)&&files.size()>=RepositoryPolicy.MAX_FILES))throw new IllegalArgumentException("Workspace capacity exceeded.");
    workspace.deleted.remove(path);workspace.edits.put(path,updated);
  }
  public static List<Change> changes(Workspace workspace,Map<String,String> reasons){
    List<Change> result=new ArrayList<>();Map<String,byte[]> now=workspace.files();Set<String> paths=new TreeSet<>(workspace.original.keySet());paths.addAll(now.keySet());
    for(String path:paths){byte[] before=workspace.original.get(path),after=now.get(path);if(Arrays.equals(before,after))continue;String old=before==null?"":RepositoryPolicy.text(before),next=after==null?"":RepositoryPolicy.text(after);result.add(new Change(path,after==null?"Deleted file":before==null?"Created file":"Updated file",reasons.getOrDefault(path,"Changed during the approved repair; review the diff."),lines(next).size(),lines(old).size()));}
    return result;
  }
  public static String diff(Workspace workspace){
    StringBuilder out=new StringBuilder();Map<String,byte[]> now=workspace.files();
    for(Change change:changes(workspace,Map.of())){String p=change.file();byte[] a=workspace.original.get(p),b=now.get(p);String old=a==null?"":RepositoryPolicy.text(a),next=b==null?"":RepositoryPolicy.text(b);List<String> ol=lines(old),nl=lines(next);out.append("diff --git ").append(quote("a/"+p)).append(' ').append(quote("b/"+p)).append('\n');if(a==null)out.append("new file mode 100644\n");if(b==null)out.append("deleted file mode ").append(workspace.modes.getOrDefault(p,"100644")).append('\n');out.append("--- ").append(a==null?"/dev/null":quote("a/"+p)).append("\n+++ ").append(b==null?"/dev/null":quote("b/"+p)).append("\n@@ -").append(ol.isEmpty()?"0,0":"1,"+ol.size()).append(" +").append(nl.isEmpty()?"0,0":"1,"+nl.size()).append(" @@\n");append(out,ol,old,'-');append(out,nl,next,'+');if(out.length()>1_500_000)throw new IllegalStateException("Diff exceeds the review budget; reduce the repair scope.");}
    return out.toString();
  }
  private static String quote(String p){return "\""+p.replace("\"","\\\"")+"\"";}
  private static List<String> lines(String s){if(s.isEmpty())return List.of();String[] lines=s.split("\n",-1);return Arrays.asList(lines).subList(0,lines.length-(s.endsWith("\n")?1:0));}
  private static void append(StringBuilder out,List<String> lines,String full,char marker){for(int i=0;i<lines.size();i++){out.append(marker).append(lines.get(i)).append('\n');if(i==lines.size()-1&&!full.endsWith("\n"))out.append("\\ No newline at end of file\n");}}
}
