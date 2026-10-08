package com.extractor.analyzer;

import com.extractor.model.DependencyGraph;
import com.extractor.utils.DependencyResolver;

import java.nio.file.Path;
import java.util.List;

/**
 * Abstraction for language-specific static analysis.
 * <p>
 * Each supported language (e.g. Java/Spoon, .NET/Roslyn) provides an
 * implementation that knows how to parse its own source artifacts and
 * produce a unified {@link DependencyGraph}.
 */
public interface LanguageAnalyzer {

    /**
     * Analyze the project rooted at the given path and return its dependency graph.
     *
     * @param projectRoot root directory of the project to analyze
     * @return the computed dependency graph
     * @throws Exception if analysis fails
     */
    DependencyGraph analyzeProject(Path projectRoot) throws Exception;

    /**
     * Return the dependency resolver appropriate for this language.
     *
     * @return the language-specific dependency resolver
     */
    DependencyResolver getDependencyResolver();

    /**
     * Non-fatal warnings of the last {@link #analyzeProject(Path)} call
     * (Constitution V).
     *
     * @return the warnings, empty by default
     */
    default List<String> getWarnings() {
        return List.of();
    }
}
