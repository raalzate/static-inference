package com.extractor.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves external dependencies by reading Maven pom.xml and Gradle build files.
 */
public class DependencyResolver {
    
    private static final Logger logger = LoggerFactory.getLogger(DependencyResolver.class);
    
    private Map<String, String> packageToDependency = new HashMap<>();
    private Map<String, String> allProjectDependencies = new HashMap<>();
    
    /**
     * Get all project dependencies with versions.
     */
    public Map<String, String> getAllDependencies() {
        return new HashMap<>(allProjectDependencies);
    }
    
    /**
     * Load dependencies from build files in the project.
     */
    public void loadDependencies(Path projectRoot) {
        logger.info("Loading dependencies from build files...");
        
        // Load from Maven pom.xml files
        loadMavenDependencies(projectRoot);
        
        // Load from Gradle build files
        loadGradleDependencies(projectRoot);
        
        logger.info("Loaded {} package to dependency mappings", packageToDependency.size());
    }
    
    /**
     * Resolve a class name to its Maven/Gradle dependency.
     */
    public String resolveDependency(String className) {
        if (className == null) return null;
        
        // Try exact package match first
        for (Map.Entry<String, String> entry : packageToDependency.entrySet()) {
            if (className.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // Try common known mappings
        String knownDependency = getKnownDependencyMapping(className);
        if (knownDependency != null) {
            return knownDependency;
        }
        
        return null; // Unknown dependency
    }
    
    /**
     * Load dependencies from Maven pom.xml files.
     */
    private void loadMavenDependencies(Path projectRoot) {
        try {
            Files.walk(projectRoot)
                .filter(path -> path.getFileName().toString().equals("pom.xml"))
                .forEach(this::parseMavenPom);
        } catch (IOException e) {
            logger.warn("Error walking project tree for pom.xml files: {}", e.getMessage());
        }
    }
    
    /**
     * Parse a Maven pom.xml file and extract dependencies.
     */
    private void parseMavenPom(Path pomPath) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(pomPath.toFile());
            
            NodeList dependencies = document.getElementsByTagName("dependency");
            
            for (int i = 0; i < dependencies.getLength(); i++) {
                Element dependency = (Element) dependencies.item(i);
                
                String groupId = getTextContent(dependency, "groupId");
                String artifactId = getTextContent(dependency, "artifactId");
                String version = getTextContent(dependency, "version");
                
                if (groupId != null && artifactId != null) {
                    String dependencyString = groupId + ":" + artifactId;
                    if (version != null && !version.startsWith("${")) {
                        dependencyString += ":" + version;
                    }
                    
                    // Store in all dependencies map
                    String key = groupId + ":" + artifactId;
                    allProjectDependencies.put(key, dependencyString);
                    
                    // Map common package prefixes to this dependency
                    mapPackagesToDependency(groupId, artifactId, dependencyString);
                }
            }
            
            logger.debug("Parsed Maven pom.xml: {}", pomPath);
            
        } catch (Exception e) {
            logger.warn("Error parsing pom.xml at {}: {}", pomPath, e.getMessage());
        }
    }
    
    /**
     * Load dependencies from Gradle build files.
     */
    private void loadGradleDependencies(Path projectRoot) {
        try {
            Files.walk(projectRoot)
                .filter(path -> path.getFileName().toString().equals("build.gradle") ||
                               path.getFileName().toString().equals("build.gradle.kts"))
                .forEach(this::parseGradleBuild);
        } catch (IOException e) {
            logger.warn("Error walking project tree for Gradle files: {}", e.getMessage());
        }
    }
    
    /**
     * Parse a Gradle build file and extract dependencies.
     */
    private void parseGradleBuild(Path buildPath) {
        try {
            List<String> lines = Files.readAllLines(buildPath);
            
            boolean inDependenciesBlock = false;
            for (String line : lines) {
                line = line.trim();
                
                if (line.startsWith("dependencies")) {
                    inDependenciesBlock = true;
                    continue;
                }
                
                if (inDependenciesBlock && line.equals("}")) {
                    inDependenciesBlock = false;
                    continue;
                }
                
                if (inDependenciesBlock && (line.contains("implementation") || line.contains("compile") || line.contains("api"))) {
                    parseGradleDependencyLine(line);
                }
            }
            
            logger.debug("Parsed Gradle build file: {}", buildPath);
            
        } catch (IOException e) {
            logger.warn("Error parsing Gradle file at {}: {}", buildPath, e.getMessage());
        }
    }
    
    /**
     * Parse a single Gradle dependency line.
     */
    private void parseGradleDependencyLine(String line) {
        // Extract dependency string from formats like:
        // implementation 'org.springframework:spring-core:5.3.0'
        // implementation "org.springframework:spring-core:5.3.0"
        
        String[] parts = line.split("[\'\"]");
        if (parts.length >= 2) {
            String dependency = parts[1];
            String[] depParts = dependency.split(":");
            
            if (depParts.length >= 2) {
                String groupId = depParts[0];
                String artifactId = depParts[1];
                
                // Store in all dependencies map
                String key = groupId + ":" + artifactId;
                allProjectDependencies.put(key, dependency);
                
                mapPackagesToDependency(groupId, artifactId, dependency);
            }
        }
    }
    
    /**
     * Map package prefixes to a dependency.
     */
    private void mapPackagesToDependency(String groupId, String artifactId, String dependencyString) {
        // Map the groupId directly
        packageToDependency.put(groupId, dependencyString);
        
        // Map common patterns
        String basePackage = groupId;
        packageToDependency.put(basePackage, dependencyString);
        
        // Handle special cases
        if ("org.springframework".equals(groupId)) {
            packageToDependency.put("org.springframework", dependencyString);
        }
        if ("org.hibernate".equals(groupId)) {
            packageToDependency.put("org.hibernate", dependencyString);
        }
        if ("com.fasterxml.jackson".equals(groupId)) {
            packageToDependency.put("com.fasterxml.jackson", dependencyString);
        }
    }
    
    /**
     * Get text content of an XML element by tag name.
     */
    private String getTextContent(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return null;
    }
    
    /**
     * Get known dependency mappings for common libraries.
     */
    private String getKnownDependencyMapping(String className) {
        if (className.startsWith("org.springframework.")) {
            return "org.springframework:spring-context:5.3.0";
        }
        if (className.startsWith("org.hibernate.")) {
            return "org.hibernate:hibernate-core:5.6.0";
        }
        if (className.startsWith("javax.persistence.") || className.startsWith("jakarta.persistence.")) {
            return "javax.persistence:javax.persistence-api:2.2";
        }
        if (className.startsWith("org.apache.commons.")) {
            return "org.apache.commons:commons-lang3:3.12.0";
        }
        if (className.startsWith("com.fasterxml.jackson.")) {
            return "com.fasterxml.jackson.core:jackson-core:2.15.0";
        }
        if (className.startsWith("org.slf4j.")) {
            return "org.slf4j:slf4j-api:2.0.0";
        }
        if (className.startsWith("org.junit.")) {
            return "org.junit.jupiter:junit-jupiter:5.10.0";
        }
        if (className.startsWith("lombok.")) {
            return "org.projectlombok:lombok:1.18.30";
        }
        
        return null;
    }
}