package dev.codebasedoctor.analysis;

import dev.codebasedoctor.model.Models.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JavaSpringAnalyzerTest {
  private final JavaSpringAnalyzer analyzer = new JavaSpringAnalyzer();

  @Test void readsMavenParentPropertiesModulesAndRootVersionFirst() {
    Analysis analysis = analyzer.analyze(Map.of(
      "pom.xml", """
        <project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
        <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.5.16</version></parent>
        <properties><java.version>21</java.version></properties><modules><module>api</module><module>domain</module></modules></project>
        """,
      "api/pom.xml", "<project><properties><maven.compiler.release>17</maven.compiler.release></properties></project>"));
    assertEquals("Maven", analysis.buildTool());
    assertEquals("21", analysis.javaVersion());
    assertEquals("3.5.16", analysis.springBootVersion());
    assertEquals(List.of("api", "domain"), analysis.modules());
    assertTrue(analysis.observations().stream().anyMatch(s -> s.contains("Multiple declared Java versions")));
  }

  @Test void recognizesCompilerPluginAndPropertyReference() {
    Analysis analysis = analyzer.analyze(Map.of("pom.xml", """
      <project><properties><compiler.level>17</compiler.level></properties><build><plugins>
      <plugin><artifactId>maven-compiler-plugin</artifactId><configuration><release>${compiler.level}</release></configuration></plugin>
      </plugins></build></project>
      """));
    assertEquals("17", analysis.javaVersion());
  }

  @Test void rejectsDoctypeWithoutResolvingExternalEntity() {
    Analysis analysis = analyzer.analyze(Map.of("pom.xml", """
      <?xml version="1.0"?><!DOCTYPE project [<!ENTITY file SYSTEM "file:///etc/passwd">]>
      <project><properties><java.version>&file;</java.version></properties></project>
      """));
    assertEquals("Unknown", analysis.javaVersion());
    assertTrue(analysis.findings().stream().anyMatch(f -> f.title().equals("Maven XML could not be safely parsed")));
    assertFalse(analysis.toString().contains("root:"));
  }

  @Test void recognizesKotlinGradleToolchainBootPluginAndModulesButIgnoresComments() {
    Analysis analysis = analyzer.analyze(Map.of(
      "build.gradle.kts", """
        // sourceCompatibility = JavaVersion.VERSION_8
        /* id("org.springframework.boot") version "1.0.0" */
        plugins { id("org.springframework.boot") version "3.5.16" }
        java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
        """,
      "settings.gradle.kts", "include(\":api\", \":core:domain\")\n// include(\":fake\")"));
    assertEquals("Gradle", analysis.buildTool());
    assertEquals("21", analysis.javaVersion());
    assertEquals("3.5.16", analysis.springBootVersion());
    assertEquals(List.of("api", "core/domain"), analysis.modules());
  }

  @Test void recognizesGroovyJavaEightAndDoesNotStripUrlStringsAsComments() {
    Analysis analysis = analyzer.analyze(Map.of("build.gradle", """
      repositories { maven { url 'https://repo.example.invalid/releases' } }
      sourceCompatibility = JavaVersion.VERSION_1_8
      plugins { id 'org.springframework.boot' version '2.7.18' }
      """));
    assertEquals("8", analysis.javaVersion());
    assertEquals("2.7.18", analysis.springBootVersion());
  }

  @Test void extractsRealTypesAnnotationsAndOnlyResolvableRepositoryRelationships() {
    Analysis analysis = analyzer.analyze(Map.of(
      "src/main/java/web/Api.java", """
        package web;
        import org.springframework.web.bind.annotation.RestController;
        import domain.TokenService;
        @RestController public class Api { private final TokenService service; private MissingService missing;
          public Api(TokenService service) { this.service = service; } }
        """,
      "src/main/java/domain/TokenService.java", "package domain; import org.springframework.stereotype.Service; @Service public class TokenService {}",
      "src/main/java/elsewhere/MissingService.java", "package elsewhere; public class MissingService {}"));
    Symbol controller = analysis.symbols().stream().filter(s -> s.name().equals("Api")).findFirst().orElseThrow();
    assertEquals("controller", controller.layer());
    assertEquals(List.of("domain.TokenService"), controller.dependencies());
    assertEquals(4, controller.line());
    assertEquals("service", analysis.symbols().stream().filter(s -> s.name().equals("TokenService")).findFirst().orElseThrow().layer());
    assertFalse(analysis.symbols().stream().anyMatch(s -> s.name().equals("MissingService") && !s.packageName().equals("elsewhere")));
  }

  @Test void resolvesSamePackageAndUnambiguousWildcardImportsWithoutGuessing() {
    Analysis analysis = analyzer.analyze(Map.of(
      "a/Owner.java", "package a; import b.*; import c.*; class Owner { Local local; Available available; Ambiguous ambiguous; }",
      "a/Local.java", "package a; class Local {}",
      "b/Available.java", "package b; class Available {}",
      "b/Ambiguous.java", "package b; class Ambiguous {}",
      "c/Ambiguous.java", "package c; class Ambiguous {}"));
    Symbol owner = analysis.symbols().stream().filter(s -> s.name().equals("Owner")).findFirst().orElseThrow();
    assertEquals(List.of("a.Local", "b.Available"), owner.dependencies());
  }

  @Test void genericTypeParametersDoNotBecomeFalseRepositoryDependencies() {
    Analysis analysis = analyzer.analyze(Map.of(
      "a/Box.java", "package a; class Box<T> { T value; <V> V identity(V input) { return input; } }",
      "a/T.java", "package a; class T {}",
      "a/V.java", "package a; class V {}"));
    assertTrue(analysis.symbols().stream().filter(s -> s.name().equals("Box")).findFirst().orElseThrow().dependencies().isEmpty());
  }

  @Test void recordsAndNestedClassesHaveDistinctNamesAndTestLayers() {
    Analysis analysis = analyzer.analyze(Map.of(
      "src/main/java/a/Outer.java", "package a; class Outer { record Token(String value) {} }",
      "src/test/java/a/OuterTest.java", "package a; class OuterTest {}"));
    assertTrue(analysis.symbols().stream().anyMatch(s -> s.name().equals("Outer.Token") && s.kind().equals("record")));
    assertTrue(analysis.symbols().stream().anyMatch(s -> s.name().equals("OuterTest") && s.layer().equals("test")));
  }

  @Test void malformedJavaIsExplicitlyExcludedInsteadOfInventingSymbols() {
    Analysis analysis = analyzer.analyze(Map.of("Broken.java", "class Broken { public void broken( {"));
    assertTrue(analysis.symbols().isEmpty());
    assertTrue(analysis.findings().stream().anyMatch(f -> f.title().equals("Java source could not be parsed")));
  }

  @Test void findingsDifferentiateObservedPatternsFromSuspectedSecretsAndRedactValues() {
    Analysis analysis = analyzer.analyze(Map.of("Example.java", """
      class Example {
        @Autowired private Object service;
        private String apiKey = "do-not-publish-this-literal";
        void run() { try { throw new Exception(); } catch (Exception ignored) { } }
      }
      """));
    assertTrue(analysis.findings().stream().anyMatch(f -> f.title().equals("Empty catch block") && f.type().equals("deterministic")));
    assertTrue(analysis.findings().stream().anyMatch(f -> f.title().equals("Spring field injection") && f.type().equals("deterministic")));
    assertTrue(analysis.findings().stream().anyMatch(f -> f.title().equals("Possible hardcoded credential") && f.type().equals("suspected")));
    assertFalse(analysis.toString().contains("do-not-publish-this-literal"));
  }

  @Test void unsafePathsAndOversizedEntriesCannotEnterSnapshot() {
    var source = new HashMap<String, String>();
    source.put("../escape.java", "class Escape {}");
    source.put("/absolute.java", "class Absolute {}");
    source.put("C:\\host.java", "class Host {}");
    source.put("Large.java", " ".repeat(262_145));
    source.put(null, "class NullPath {}");
    source.put("Safe.java", "class Safe {}");
    Analysis analysis = analyzer.analyze(source);
    assertEquals(List.of("Safe.java"), analysis.files());
    assertTrue(analysis.observations().stream().anyMatch(s -> s.startsWith("5 source entry/entries")));
  }

  @Test void dynamicBuildValuesRemainUnknownAndNoJavaIsNotAJavaProject() {
    Analysis gradle = analyzer.analyze(Map.of("build.gradle", "sourceCompatibility = project.targetJava\nplugins { alias(libs.plugins.spring.boot) }"));
    assertEquals("Unknown", gradle.javaVersion());
    assertEquals("Not detected", gradle.springBootVersion());
    Analysis other = analyzer.analyze(Map.of("README.md", "This repository has no source code."));
    assertEquals("Unknown", other.language());
    assertEquals("Unknown", other.buildTool());
  }
}
