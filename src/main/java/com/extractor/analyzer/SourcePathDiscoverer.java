package com.extractor.analyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SourcePathDiscoverer {
    
    private static final Logger logger = LoggerFactory.getLogger(SourcePathDiscoverer.class);
    
    public List<String> findSourcePaths(Path projectRoot) {
        List<String> sourcePaths = new ArrayList<>();
        
        try {
            Files.walk(projectRoot)
                .filter(Files::isDirectory)
                .filter(path -> path.toString().endsWith("src/main/java"))
                .map(Path::toString)
                .forEach(sourcePaths::add);
            
            if (sourcePaths.isEmpty()) {
                sourcePaths.add(projectRoot.toString());
            }
            
        } catch (IOException e) {
            logger.warn("Error walking project tree: {}", e.getMessage());
            sourcePaths.add(projectRoot.toString());
        }
        
        return sourcePaths;
    }
}
