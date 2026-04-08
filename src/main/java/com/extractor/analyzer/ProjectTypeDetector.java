package com.extractor.analyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Examines a directory and determines the project type based on marker files.
 * Detection: .sln/.csproj → DOTNET, pom.xml/build.gradle → JAVA,
 * both → AMBIGUOUS, neither → UNKNOWN.
 */
public class ProjectTypeDetector {

    private static final Logger logger = LoggerFactory.getLogger(ProjectTypeDetector.class);

    /**
     * Detects the project type by scanning for marker files in the given directory.
     */
    public ProjectType detect(Path projectRoot) {
        boolean hasJava = hasJavaMarkers(projectRoot);
        boolean hasDotnet = hasDotnetMarkers(projectRoot);

        if (hasJava && hasDotnet) {
            logger.warn("Ambiguous project type at {}: found both Java and .NET markers", projectRoot);
            return ProjectType.AMBIGUOUS;
        }
        if (hasDotnet) {
            logger.info("Detected .NET project at {}", projectRoot);
            return ProjectType.DOTNET;
        }
        if (hasJava) {
            logger.info("Detected Java project at {}", projectRoot);
            return ProjectType.JAVA;
        }

        logger.warn("Unknown project type at {}: no recognized markers found", projectRoot);
        return ProjectType.UNKNOWN;
    }

    private boolean hasJavaMarkers(Path projectRoot) {
        return Files.exists(projectRoot.resolve("pom.xml"))
                || Files.exists(projectRoot.resolve("build.gradle"))
                || Files.exists(projectRoot.resolve("build.gradle.kts"));
    }

    private boolean hasDotnetMarkers(Path projectRoot) {
        return hasFileWithExtension(projectRoot, "*.sln")
                || hasFileWithExtension(projectRoot, "*.csproj");
    }

    private boolean hasFileWithExtension(Path directory, String glob) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, glob)) {
            return stream.iterator().hasNext();
        } catch (IOException e) {
            logger.debug("Error scanning {} for {}: {}", directory, glob, e.getMessage());
            return false;
        }
    }
}
