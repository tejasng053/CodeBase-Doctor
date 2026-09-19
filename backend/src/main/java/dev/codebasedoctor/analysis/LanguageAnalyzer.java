package dev.codebasedoctor.analysis;

import dev.codebasedoctor.model.Models.Analysis;
import java.util.Map;

/** Analyzes an already bounded, sandbox-provided source snapshot; never executes project code. */
public interface LanguageAnalyzer {
  Analysis analyze(Map<String, String> sources);
}
