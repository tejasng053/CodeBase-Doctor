package dev.codebasedoctor.analysis;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.nodeTypes.NodeWithTypeParameters;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import dev.codebasedoctor.model.Models.*;
import java.io.StringReader;
import java.util.*;
import java.util.regex.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

@Service
public class JavaSpringAnalyzer implements LanguageAnalyzer {
  private static final int MAX_FILES = 2_000, MAX_FILE_CHARACTERS = 262_144, MAX_TOTAL_CHARACTERS = 8_388_608;
  private record Parsed(String file, CompilationUnit unit) {}
  private record Declared(String qualifiedName, String name, String packageName, String file,
                          CompilationUnit unit, TypeDeclaration<?> declaration) {}

  @Override public Analysis analyze(Map<String, String> sources) {
    Objects.requireNonNull(sources, "sources");
    var files = new TreeMap<String, String>();
    int totalCharacters = 0, omitted = 0;
    for (var entry : sources.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.nullsLast(String::compareTo))).toList()) {
      String path = entry.getKey(), source = entry.getValue();
      if (!safeRelative(path) || source == null || source.length() > MAX_FILE_CHARACTERS || files.size() >= MAX_FILES || totalCharacters + source.length() > MAX_TOTAL_CHARACTERS) { omitted++; continue; }
      files.put(path, source); totalCharacters += source.length();
    }
    var findings = new ArrayList<Finding>();
    var observations = new ArrayList<String>();
    observations.add("Static source snapshot only: project code, build scripts, and application configuration were not executed by the analyzer.");
    observations.add("Relationships below reference repository types resolved from imports and packages. This is structural Java parsing, not compiler type checking or a runtime call graph.");
    if (omitted > 0) observations.add(omitted + " source entry/entries were omitted for invalid paths, missing content, or analyzer snapshot size limits.");
    var modules = new TreeSet<String>();
    var javaVersions = new LinkedHashSet<String>();
    var springVersions = new LinkedHashSet<String>();
    boolean maven = files.keySet().stream().anyMatch(p -> baseName(p).equals("pom.xml"));
    boolean gradle = files.keySet().stream().anyMatch(p -> Set.of("build.gradle", "build.gradle.kts").contains(baseName(p)));
    var buildEntries = files.entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().contains("/") ? 1 : 0).thenComparing(Map.Entry::getKey)).toList();
    for (var entry : buildEntries) {
      String file = entry.getKey(), source = entry.getValue();
      if (baseName(file).equals("pom.xml")) {
        inspectMaven(file, source, javaVersions, springVersions, modules, findings, observations);
      } else if (Set.of("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts").contains(baseName(file))) {
        inspectGradle(file, source, javaVersions, springVersions, modules);
      }
    }
    String buildTool = files.containsKey("pom.xml") ? "Maven"
      : (files.containsKey("build.gradle") || files.containsKey("build.gradle.kts")) ? "Gradle"
      : maven ? "Maven" : gradle ? "Gradle" : "Unknown";
    if (maven && gradle) observations.add("Both Maven and Gradle build files are present; selected " + buildTool + " from the root build where available. Nested or alternative builds may require a separate run.");
    if (javaVersions.size() > 1) observations.add("Multiple declared Java versions in snapshot: " + String.join(", ", javaVersions) + ". Inspect module-specific requirements before changing the runtime.");
    if (springVersions.size() > 1) observations.add("Multiple Spring Boot versions are declared: " + String.join(", ", springVersions) + ".");
    JavaParser parser = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE));
    var parsed = new ArrayList<Parsed>();
    for (var entry : files.entrySet()) {
      if (!entry.getKey().endsWith(".java")) continue;
      var result = parser.parse(entry.getValue());
      if (result.isSuccessful() && result.getResult().isPresent()) {
        parsed.add(new Parsed(entry.getKey(), result.getResult().get()));
      } else {
        findings.add(new Finding("Java source could not be parsed", "deterministic", "medium", entry.getKey(), null,
          "JavaParser reported " + result.getProblems().size() + " parsing problem(s).",
          "This file's symbols are excluded from the architecture overview. Parser limitations or invalid source may be responsible.",
          "Inspect this source file and confirm its configured Java language level with the real build."));
      }
    }
    var declared = new ArrayList<Declared>();
    for (var source : parsed) {
      String pkg = source.unit().getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
      for (TypeDeclaration<?> type : source.unit().findAll(TypeDeclaration.class)) {
        String name = nestedName(type);
        declared.add(new Declared(pkg.isEmpty() ? name : pkg + "." + name, name, pkg, source.file(), source.unit(), type));
      }
      inspectPatterns(source, findings);
    }
    Set<String> knownNames = new HashSet<>();
    declared.forEach(type -> knownNames.add(type.qualifiedName()));
    var symbols = new ArrayList<Symbol>();
    for (var type : declared) {
      var annotations = type.declaration().getAnnotations().stream().map(a -> a.getNameAsString()).sorted().toList();
      var dependencies = new TreeSet<String>();
      for (var reference : type.declaration().findAll(ClassOrInterfaceType.class)) {
        if (owningType(reference) != type.declaration()) continue;
        if (isTypeParameter(reference)) continue;
        String resolved = resolve(reference.getNameWithScope(), type, knownNames);
        if (resolved != null && !resolved.equals(type.qualifiedName())) dependencies.add(resolved);
      }
      int start = type.declaration().getBegin().map(p -> p.line).orElse(1);
      int length = type.declaration().getEnd().map(p -> p.line - start + 1).orElse(0);
      symbols.add(new Symbol(type.name(), type.packageName(), kind(type.declaration()), layer(annotations, type.file()),
        type.file(), start, length, annotations, List.copyOf(dependencies)));
      if (length > 500) findings.add(new Finding("Large declared type", "deterministic", "low", type.file(), start,
        type.name() + " spans " + length + " source lines.", "Size is a measured maintainability signal, not evidence of incorrect behavior.",
        "Review whether this type has separable responsibilities before attempting a refactor."));
    }
    symbols.sort(Comparator.comparing(Symbol::file).thenComparingInt(Symbol::line));
    findings.sort(Comparator.comparing(Finding::file).thenComparing(f -> f.line() == null ? 0 : f.line()).thenComparing(Finding::title));
    boolean spring = !springVersions.isEmpty() || parsed.stream().anyMatch(p -> p.unit().getImports().stream().anyMatch(i -> i.getNameAsString().startsWith("org.springframework.boot")));
    if (spring && springVersions.isEmpty()) observations.add("Spring Boot imports were found, but the Boot version could not be resolved from the provided build files.");
    if (gradle) observations.add("Gradle metadata recognizes literal declarations only; build scripts, version catalogs, imported scripts, and dynamic expressions are not evaluated.");
    if (parsed.isEmpty()) observations.add("No Java files were successfully parsed in the provided snapshot.");
    if (modules.isEmpty()) modules.add(".");
    return new Analysis(files.keySet().stream().anyMatch(p -> p.endsWith(".java")) || maven || gradle ? "Java" : "Unknown",
      first(javaVersions), buildTool, spring ? first(springVersions) : "Not detected", List.copyOf(modules),
      files.keySet().stream().filter(JavaSpringAnalyzer::important).toList(), List.copyOf(files.keySet()),
      List.copyOf(symbols), List.copyOf(findings), List.copyOf(observations));
  }

  private static void inspectPatterns(Parsed source, List<Finding> findings) {
    for (var field : source.unit().findAll(FieldDeclaration.class)) {
      if (field.getAnnotations().stream().anyMatch(a -> a.getName().getIdentifier().equals("Autowired"))) {
        findings.add(new Finding("Spring field injection", "deterministic", "low", source.file(), line(field),
          "@Autowired appears on field " + field.getVariables().stream().map(v -> v.getNameAsString()).toList() + ".",
          "The field is injected after construction. This is a maintainability concern; it does not prove a runtime defect.",
          "Consider constructor injection where it improves explicit dependencies and testability."));
      }
    }
    for (CatchClause clause : source.unit().findAll(CatchClause.class)) {
      if (clause.getBody().getStatements().isEmpty()) findings.add(new Finding("Empty catch block", "deterministic", "medium", source.file(), line(clause),
        "catch (" + clause.getParameter().getTypeAsString() + " " + clause.getParameter().getNameAsString() + ") contains no statements.",
        "The exception is ignored by this block. Whether ignoring it is intentional requires application context.",
        "Confirm intended error handling; propagate, record, or explicitly document an expected ignored exception."));
    }
    // Deliberately no secret-value snippets: a suspected credential finding must not reproduce a secret.
    for (var field : source.unit().findAll(FieldDeclaration.class)) for (var variable : field.getVariables()) {
      if (!variable.getNameAsString().matches("(?i).*(password|secret|api_?key).*") || variable.getInitializer().isEmpty()) continue;
      var initializer = variable.getInitializer().get();
      if (initializer.isStringLiteralExpr() && initializer.asStringLiteralExpr().asString().length() >= 4) {
        findings.add(new Finding("Possible hardcoded credential", "suspected", "high", source.file(), line(variable),
          "String literal assigned to credential-like field '" + variable.getNameAsString() + "'; value redacted.",
          "The field name and literal suggest a credential, but the value may be a fixture or placeholder.",
          "Check whether this value is sensitive. If confirmed, remove it from source, rotate it, and load it from a secret provider."));
      }
    }
  }

  private static void inspectMaven(String file, String source, Set<String> javaVersions, Set<String> springVersions,
                                   Set<String> modules, List<Finding> findings, List<String> observations) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      var builder = factory.newDocumentBuilder();
      builder.setErrorHandler(new DefaultHandler());
      builder.setEntityResolver((publicId, systemId) -> { throw new org.xml.sax.SAXException("External XML entities are disabled"); });
      Element project = builder.parse(new InputSource(new StringReader(source))).getDocumentElement();
      Map<String, String> properties = new HashMap<>();
      Element propertiesNode = child(project, "properties");
      if (propertiesNode != null) for (Element value : children(propertiesNode)) properties.put(localName(value), value.getTextContent().trim());
      for (String key : List.of("java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target")) {
        String version = resolveProperty(properties.get(key), properties);
        if (version != null && version.matches("(?:1\\.)?\\d+(?:\\.\\d+)*")) { javaVersions.add(normalizeJava(version)); break; }
      }
      NodeList plugins = project.getElementsByTagNameNS("*", "plugin");
      for (int i = 0; i < plugins.getLength(); i++) {
        Element plugin = (Element) plugins.item(i);
        if (!"maven-compiler-plugin".equals(value(plugin, "artifactId"))) continue;
        Element configuration = child(plugin, "configuration");
        if (configuration == null) continue;
        for (String key : List.of("release", "source", "target")) {
          String version = resolveProperty(value(configuration, key), properties);
          if (version != null && version.matches("(?:1\\.)?\\d+(?:\\.\\d+)*")) { javaVersions.add(normalizeJava(version)); break; }
        }
      }
      Element parent = child(project, "parent");
      if (parent != null && "org.springframework.boot".equals(value(parent, "groupId"))) addVersion(springVersions, resolveProperty(value(parent, "version"), properties));
      for (String tag : List.of("dependency", "plugin")) {
        NodeList dependencies = project.getElementsByTagNameNS("*", tag);
        for (int i = 0; i < dependencies.getLength(); i++) {
          Element dependency = (Element) dependencies.item(i);
          if ("org.springframework.boot".equals(value(dependency, "groupId"))) addVersion(springVersions, resolveProperty(value(dependency, "version"), properties));
        }
      }
      Element moduleRoot = child(project, "modules");
      if (moduleRoot != null) for (Element module : children(moduleRoot)) {
        if (!localName(module).equals("module")) continue;
        String path = directory(file) + module.getTextContent().trim();
        if (safeRelative(path)) modules.add(path);
      }
      if (!file.equals("pom.xml")) modules.add(directory(file).replaceFirst("/$", ""));
    } catch (Exception exception) {
      findings.add(new Finding("Maven XML could not be safely parsed", "deterministic", "medium", file, null,
        "The secure XML parser rejected this build descriptor.",
        "Malformed XML and documents containing DOCTYPE or external entities are rejected. No external resource was loaded.",
        "Inspect the POM and remove external entity declarations or fix its XML syntax before relying on build metadata."));
    }
  }

  private static void inspectGradle(String file, String source, Set<String> javaVersions, Set<String> springVersions, Set<String> modules) {
    // Only literal declarations are recognized. Gradle is executable code and is never evaluated here.
    String text = stripGradleComments(source);
    Matcher java = Pattern.compile("(?:JavaLanguageVersion\\.of\\s*\\(\\s*|(?:sourceCompatibility|targetCompatibility)\\s*=\\s*(?:JavaVersion\\.VERSION_)?[\\\"']?)([0-9]+(?:[._][0-9]+)?)").matcher(text);
    while (java.find()) javaVersions.add(normalizeJava(java.group(1).replace('_', '.')));
    Matcher spring = Pattern.compile("id\\s*(?:\\(\\s*)?[\\\"']org\\.springframework\\.boot[\\\"']\\s*\\)?\\s*version\\s*(?:\\(\\s*)?[\\\"']([^\\\"'$]+)[\\\"']").matcher(text);
    while (spring.find()) addVersion(springVersions, spring.group(1));
    if (baseName(file).startsWith("settings.gradle")) {
      Matcher includes = Pattern.compile("(?m)^\\s*include\\s*(?:\\(([^\\n)]*)\\)|([^\\n]+))").matcher(text);
      while (includes.find()) {
        Matcher names = Pattern.compile("[\\\"'](:?[A-Za-z0-9_.:-]+)[\\\"']").matcher(includes.group(1) == null ? includes.group(2) : includes.group(1));
        while (names.find()) {
          String path = directory(file) + names.group(1).replaceFirst("^:", "").replace(':', '/');
          if (safeRelative(path)) modules.add(path);
        }
      }
    } else if (!directory(file).isEmpty()) modules.add(directory(file).replaceFirst("/$", ""));
  }

  static String stripGradleComments(String source) {
    StringBuilder result = new StringBuilder();
    char quote = 0;
    for (int i = 0; i < source.length(); i++) {
      char c = source.charAt(i), next = i + 1 < source.length() ? source.charAt(i + 1) : 0;
      if (quote != 0) {
        result.append(c);
        if (c == '\\' && next != 0) { result.append(next); i++; }
        else if (c == quote) quote = 0;
      } else if (c == '\'' || c == '"') { quote = c; result.append(c); }
      else if (c == '/' && next == '/') {
        while (i < source.length() && source.charAt(i) != '\n') i++;
        result.append('\n');
      } else if (c == '/' && next == '*') {
        i += 2;
        while (i < source.length() && !(source.charAt(i) == '*' && i + 1 < source.length() && source.charAt(i + 1) == '/')) {
          result.append(source.charAt(i) == '\n' ? '\n' : ' '); i++;
        }
        i++; result.append(' ');
      } else result.append(c);
    }
    return result.toString();
  }

  private static String resolve(String reference, Declared type, Set<String> knownNames) {
    if (knownNames.contains(reference)) return reference;
    var candidates = new HashSet<String>();
    String samePackage = type.packageName().isEmpty() ? reference : type.packageName() + "." + reference;
    if (knownNames.contains(samePackage)) candidates.add(samePackage);
    for (var imported : type.unit().getImports()) {
      if (imported.isStatic()) continue;
      String name = imported.getNameAsString();
      if (imported.isAsterisk()) {
        String full = name + "." + reference;
        if (knownNames.contains(full)) candidates.add(full);
      } else {
        String simple = name.substring(name.lastIndexOf('.') + 1);
        if (reference.equals(simple) || reference.startsWith(simple + ".")) {
          String full = name + reference.substring(simple.length());
          if (knownNames.contains(full)) candidates.add(full);
        }
      }
    }
    String enclosing = type.qualifiedName();
    while (enclosing.contains(".")) {
      String nested = enclosing + "." + reference;
      if (knownNames.contains(nested)) candidates.add(nested);
      enclosing = enclosing.substring(0, enclosing.lastIndexOf('.'));
      if (enclosing.equals(type.packageName())) break;
    }
    return candidates.size() == 1 ? candidates.iterator().next() : null;
  }

  private static boolean isTypeParameter(ClassOrInterfaceType reference) {
    String name = reference.getNameWithScope().split("\\.")[0];
    for (Node parent = reference.getParentNode().orElse(null); parent != null; parent = parent.getParentNode().orElse(null)) {
      if (parent instanceof NodeWithTypeParameters<?> declaration && declaration.getTypeParameters().stream().anyMatch(p -> p.getNameAsString().equals(name))) return true;
    }
    return false;
  }

  private static TypeDeclaration<?> owningType(Node node) {
    for (Node current = node.getParentNode().orElse(null); current != null; current = current.getParentNode().orElse(null))
      if (current instanceof TypeDeclaration<?> type) return type;
    return null;
  }
  private static String nestedName(TypeDeclaration<?> type) {
    TypeDeclaration<?> outer = owningType(type);
    return outer == null ? type.getNameAsString() : nestedName(outer) + "." + type.getNameAsString();
  }
  private static String kind(TypeDeclaration<?> type) {
    if (type.isClassOrInterfaceDeclaration()) return type.asClassOrInterfaceDeclaration().isInterface() ? "interface" : "class";
    if (type.isEnumDeclaration()) return "enum";
    if (type.isRecordDeclaration()) return "record";
    if (type.isAnnotationDeclaration()) return "annotation";
    return "type";
  }
  private static String layer(List<String> annotations, String file) {
    if (file.contains("/src/test/") || file.startsWith("src/test/")) return "test";
    Set<String> simple = new HashSet<>();
    annotations.forEach(a -> simple.add(a.substring(a.lastIndexOf('.') + 1)));
    if (simple.contains("SpringBootApplication")) return "application";
    if (simple.contains("RestController") || simple.contains("Controller")) return "controller";
    if (simple.contains("Service")) return "service";
    if (simple.contains("Repository")) return "repository";
    if (simple.contains("Entity") || simple.contains("Embeddable")) return "entity";
    if (simple.contains("Configuration")) return "configuration";
    if (simple.contains("Component")) return "component";
    return "unclassified";
  }
  private static int line(Node node) { return node.getBegin().map(p -> p.line).orElse(1); }
  private static boolean important(String path) {
    String name = baseName(path);
    return Set.of("pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "gradle.properties", "mvnw", "gradlew", "Dockerfile", "README.md", "application.yml", "application.yaml", "application.properties").contains(name);
  }
  private static String first(Set<String> values) { return values.stream().findFirst().orElse("Unknown"); }
  private static String normalizeJava(String value) { return value.startsWith("1.") ? value.substring(2) : value; }
  private static void addVersion(Set<String> versions, String value) { if (value != null && value.matches("[0-9][A-Za-z0-9.+_-]*")) versions.add(value); }
  private static String resolveProperty(String value, Map<String, String> properties) {
    for (int i = 0; i < 8 && value != null && value.startsWith("${") && value.endsWith("}"); i++) value = properties.get(value.substring(2, value.length() - 1));
    return value != null && !value.contains("${") ? value : null;
  }
  private static String baseName(String path) { return path.substring(path.lastIndexOf('/') + 1); }
  private static String directory(String path) { return path.substring(0, path.lastIndexOf('/') + 1); }
  private static boolean safeRelative(String path) {
    return path != null && !path.isBlank() && !path.startsWith("/") && !path.contains("\\") && !path.contains(":") && !path.chars().anyMatch(Character::isISOControl)
      && Arrays.stream(path.split("/", -1)).noneMatch(part -> part.equals("..") || part.isEmpty());
  }
  private static String localName(Element element) { return element.getLocalName() == null ? element.getTagName() : element.getLocalName(); }
  private static List<Element> children(Element parent) {
    var result = new ArrayList<Element>();
    for (org.w3c.dom.Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) if (node instanceof Element element) result.add(element);
    return result;
  }
  private static Element child(Element parent, String name) { return children(parent).stream().filter(e -> localName(e).equals(name)).findFirst().orElse(null); }
  private static String value(Element parent, String name) { Element value = child(parent, name); return value == null ? null : value.getTextContent().trim(); }
}
