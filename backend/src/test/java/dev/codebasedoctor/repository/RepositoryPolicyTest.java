package dev.codebasedoctor.repository;
import org.junit.jupiter.api.Test;
import org.apache.commons.compress.archivers.zip.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class RepositoryPolicyTest {
 @Test void acceptsOnlyCanonicalPublicGitHubRoots(){assertEquals("owner/repo",RepositoryPolicy.slug("https://github.com/owner/repo.git/"));for(String u:new String[]{"http://github.com/o/r","https://github.com.evil.test/o/r","https://me:secret@github.com/o/r","https://github.com:443/o/r","https://github.com/o/r?x=1","https://github.com/o/r/tree/main","https://127.0.0.1/o/r","https://github.com/o/%2e%2e"})assertThrows(IllegalArgumentException.class,()->RepositoryPolicy.slug(u));}
 @Test void unsafePathsAndSensitiveEditsRejected(){for(String p:new String[]{"../a","/tmp/a","a/../../x","a\\b","a/.git/config","a//b","a\nfile","C:x"})assertThrows(IllegalArgumentException.class,()->RepositoryPolicy.path(p));assertThrows(IllegalArgumentException.class,()->RepositoryPolicy.editable(".env"));assertThrows(IllegalArgumentException.class,()->RepositoryPolicy.editable(".github/workflows/ci.yml"));assertFalse(RepositoryPolicy.modelReadable("config/private.key"));}
 @Test void archiveIsReadAsDataAndPreservesBytes()throws Exception{var s=RepositorySource.unpack("o/r","a".repeat(40),"main",archive("root/pom.xml","<project/>",0100644));assertEquals("<project/>",new String(s.files().get("pom.xml"),StandardCharsets.UTF_8));}
 @Test void rejectsZipSlipAndSymlinks()throws Exception{byte[] traversal=archive("root/../../escape","x",0100644);assertThrows(IllegalArgumentException.class,()->RepositorySource.unpack("o/r","a".repeat(40),"main",traversal));byte[] link=archive("root/link","/etc/passwd",0120777);assertThrows(IllegalArgumentException.class,()->RepositorySource.unpack("o/r","a".repeat(40),"main",link));}
 @Test void rejectsExpandedOversize()throws Exception{byte[] zip=archive("root/large","x".repeat(RepositoryPolicy.MAX_FILE+1),0100644);assertThrows(IllegalArgumentException.class,()->RepositorySource.unpack("o/r","a".repeat(40),"main",zip));}
 private byte[] archive(String path,String text,int mode)throws IOException{var out=new ByteArrayOutputStream();try(var zip=new ZipArchiveOutputStream(out)){var entry=new ZipArchiveEntry(path);entry.setUnixMode(mode);zip.putArchiveEntry(entry);zip.write(text.getBytes(StandardCharsets.UTF_8));zip.closeArchiveEntry();}return out.toByteArray();}
}
