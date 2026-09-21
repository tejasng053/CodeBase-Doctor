package dev.codebasedoctor.repository;

import java.net.URI;
import java.nio.charset.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.*;

public final class RepositoryPolicy {
  public static final int MAX_FILES=3000, MAX_FILE=1_048_576, MAX_TOTAL=24*1024*1024;
  private RepositoryPolicy() {}
  public static String slug(String input) {
    if(input==null || input.length()>250)throw new IllegalArgumentException("Enter a public https://github.com/owner/repository URL.");
    URI u;try {u=URI.create(input.strip());}catch(Exception e){throw new IllegalArgumentException("Invalid repository URL.");}
    if(!"https".equals(u.getScheme())||!"github.com".equalsIgnoreCase(u.getHost())||u.getPort()!=-1||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null)throw new IllegalArgumentException("Only public HTTPS github.com repository URLs are accepted; credentials, query strings, and ports are forbidden.");
    String p=u.getRawPath();if(p.endsWith("/"))p=p.substring(0,p.length()-1);if(p.endsWith(".git"))p=p.substring(0,p.length()-4);
    if(!p.matches("/[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9_.-]{1,100}")||p.endsWith("/.")||p.endsWith("/.."))throw new IllegalArgumentException("Use a repository root URL, without an issue, branch, or encoded path.");
    return p.substring(1);
  }
  public static String path(String path) {
    if(path==null||path.isBlank()||path.length()>240||path.startsWith("/")||path.contains("\\")||path.chars().anyMatch(c->c<32||c>126)||path.contains(":"))throw new IllegalArgumentException("Unsafe repository path.");
    for(String part:path.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals("..")||part.equalsIgnoreCase(".git"))throw new IllegalArgumentException("Unsafe repository path: "+path);
    return path;
  }
  public static boolean modelReadable(String path) {
    String p=path.toLowerCase(Locale.ROOT),name=p.substring(p.lastIndexOf('/')+1);
    return !name.equals(".env")&&!name.startsWith(".env.")&&!p.endsWith(".pem")&&!p.endsWith(".key")&&!p.endsWith(".p12")&&!p.endsWith(".jks")&&!name.contains("credential")&&!name.equals("id_rsa")&&!name.equals("id_ed25519");
  }
  public static void editable(String path) {
    path(path);
    if(!modelReadable(path)||path.startsWith(".github/")||path.startsWith(".gitlab/")||path.startsWith(".husky/")||path.equals(".gitmodules")||path.equals(".gitattributes"))throw new IllegalArgumentException("This sensitive/automation file cannot be edited by the agent.");
  }
  public static String text(byte[] value) {
    if(value==null)throw new IllegalArgumentException("File not found.");
    if(value.length>MAX_FILE)throw new IllegalArgumentException("File too large.");
    for(byte b:value)if(b==0)throw new IllegalArgumentException("Binary file is not readable by the agent.");
    try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(value)).toString();}catch(CharacterCodingException e){throw new IllegalArgumentException("Only UTF-8 text files are supported by the agent.");}
  }
  public static String digest(byte[] data) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
