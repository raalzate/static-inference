package com.extractor.analyzer;

import com.extractor.model.DependencyGraph;
import com.extractor.utils.DependencyResolver;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Adapter that wraps the existing {@link ProjectAnalyzer} to implement {@link LanguageAnalyzer}.
 * Delegates all calls without any logic changes — preserves backward compatibility (Constitution I).
 */
public class JavaSpoonAnalyzer implements LanguageAnalyzer {

    private final ProjectAnalyzer delegate;

    public JavaSpoonAnalyzer() {
        this.delegate = new ProjectAnalyzer();
    }

    public JavaSpoonAnalyzer(boolean enableLombok) {
        this.delegate = new ProjectAnalyzer(enableLombok);
    }

    @Override
    public DependencyGraph analyzeProject(Path projectRoot) throws Exception {
        if (projectRoot == null || !Files.exists(projectRoot)) {
            throw new IllegalArgumentException(
                    "Project path '" + projectRoot + "' does not exist");
        }
        if (!Files.isDirectory(projectRoot)) {
            throw new IllegalArgumentException(
                    "Project path '" + projectRoot + "' is not a directory");
        }
        return delegate.analyzeProject(projectRoot);
    }

    @Override
    public DependencyResolver getDependencyResolver() {
        return delegate.getDependencyResolver();
    }
}
