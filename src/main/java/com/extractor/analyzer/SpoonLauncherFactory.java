package com.extractor.analyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SpoonLauncherFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(SpoonLauncherFactory.class);
    
    private final boolean enableLombok;
    private final SourcePathDiscoverer sourcePathDiscoverer;
    
    public SpoonLauncherFactory(boolean enableLombok) {
        this.enableLombok = enableLombok;
        this.sourcePathDiscoverer = new SourcePathDiscoverer();
    }
    
    public Launcher createLauncher(Path projectRoot) {
        Launcher launcher = new Launcher();
        
        List<String> sourcePaths = sourcePathDiscoverer.findSourcePaths(projectRoot);
        
        for (String sourcePath : sourcePaths) {
            launcher.addInputResource(sourcePath);
            logger.info("Added source path: {}", sourcePath);
        }
        
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setShouldCompile(false);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.getEnvironment().setPreserveLineNumbers(true);
        launcher.getEnvironment().setCommentEnabled(false);
        
        if (enableLombok) {
            logger.info("Enabling Lombok annotation processing");
            launcher.getEnvironment().setComplianceLevel(17);
            launcher.getEnvironment().setNoClasspath(false);
            configureAnnotationProcessors(launcher);
        } else {
            logger.info("Using no-classpath mode for reliable analysis");
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setComplianceLevel(8);
        }
        
        return launcher;
    }
    
    private void configureAnnotationProcessors(Launcher launcher) {
        try {
            if (enableLombok) {
                String classpath = buildClasspath();
                if (!classpath.isEmpty()) {
                    launcher.getEnvironment().setSourceClasspath(classpath.split(System.getProperty("path.separator")));
                    logger.info("Configured annotation processing with Lombok support");
                } else {
                    logger.warn("Could not find Lombok JAR, using no-classpath mode");
                    launcher.getEnvironment().setNoClasspath(true);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not configure annotation processors: {}", e.getMessage());
            launcher.getEnvironment().setNoClasspath(true);
        }
    }
    
    private String buildClasspath() {
        List<String> classpathElements = new ArrayList<>();
        
        String systemClasspath = System.getProperty("java.class.path");
        if (systemClasspath != null && !systemClasspath.isEmpty()) {
            Collections.addAll(classpathElements, systemClasspath.split(System.getProperty("path.separator")));
        }
        
        return String.join(System.getProperty("path.separator"), classpathElements);
    }
}
