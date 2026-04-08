package com.extractor.analyzer;

import com.extractor.model.Component;
import com.extractor.model.PackageGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyzes and groups components by their package structure.
 * Identifies shared domains (e.g., com.mx.sura.afore) and infers related package groups.
 */
public class PackageGroupAnalyzer {
    
    private static final Logger logger = LoggerFactory.getLogger(PackageGroupAnalyzer.class);
    
    private final ComponentRegistry componentRegistry;
    private String sharedDomain;
    private Map<String, Set<String>> packageToComponents;
    
    public PackageGroupAnalyzer(ComponentRegistry componentRegistry) {
        this.componentRegistry = componentRegistry;
        this.packageToComponents = new HashMap<>();
    }
    
    /**
     * Analyze all components and group them by package.
     */
    public void analyzePackageGroups() {
        logger.info("Analyzing package groups...");
        
        // Step 1: Identify the shared domain
        identifySharedDomain();
        
        // Step 2: Group components by package
        groupComponentsByPackage();
        
        // Step 3: Assign package dependencies to each component
        assignPackageDependencies();
        
        logger.info("Package group analysis completed. Shared domain: {}", sharedDomain);
    }
    
    /**
     * Identify the most common base package as the shared domain.
     * For example, if most classes start with "com.mx.sura.afore", that becomes the shared domain.
     */
    private void identifySharedDomain() {
        Map<String, Integer> domainCounts = new HashMap<>();
        
        for (Component component : componentRegistry.getAllComponents()) {
            String packageName = extractPackage(component.getId());
            if (packageName == null) continue;
            
            // Extract up to 4 levels of package hierarchy
            String[] parts = packageName.split("\\.");
            for (int i = 2; i <= Math.min(4, parts.length); i++) {
                String baseDomain = String.join(".", Arrays.copyOfRange(parts, 0, i));
                domainCounts.put(baseDomain, domainCounts.getOrDefault(baseDomain, 0) + 1);
            }
        }
        
        // Find the domain with the most components
        sharedDomain = domainCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("unknown");
        
        logger.info("Identified shared domain: {} with {} components", 
            sharedDomain, domainCounts.getOrDefault(sharedDomain, 0));
    }
    
    /**
     * Group all components by their package name.
     */
    private void groupComponentsByPackage() {
        for (Component component : componentRegistry.getAllComponents()) {
            String packageName = extractPackage(component.getId());
            if (packageName == null) continue;
            
            // Use the full package name for grouping
            packageToComponents.computeIfAbsent(packageName, k -> new HashSet<>())
                .add(component.getId());
        }
        
        logger.info("Grouped components into {} packages", packageToComponents.size());
    }
    
    /**
     * For each component, identify which other packages it depends on based on calls_out.
     */
    private void assignPackageDependencies() {
        for (Component component : componentRegistry.getAllComponents()) {
            List<PackageGroup> packageGroups = new ArrayList<>();
            
            // Track which packages this component calls
            Map<String, Set<String>> packageToCalledComponents = new HashMap<>();
            
            for (String calledComponent : component.getCallsOut()) {
                // Skip if it's calling itself
                if (calledComponent.equals(component.getId())) continue;
                
                String calledPackage = extractPackage(calledComponent);
                if (calledPackage == null) continue;
                
                // Only include if it's a different package
                String currentPackage = extractPackage(component.getId());
                if (calledPackage.equals(currentPackage)) continue;
                
                packageToCalledComponents.computeIfAbsent(calledPackage, k -> new HashSet<>())
                    .add(calledComponent);
            }
            
            // Convert to PackageGroup objects, sorted by package name
            List<String> sortedPackages = new ArrayList<>(packageToCalledComponents.keySet());
            Collections.sort(sortedPackages);
            
            for (String packageName : sortedPackages) {
                PackageGroup group = new PackageGroup(packageName);
                List<String> components = new ArrayList<>(packageToCalledComponents.get(packageName));
                Collections.sort(components);
                group.setComponents(components);
                packageGroups.add(group);
            }
            
            component.setPackageDependencies(packageGroups);
            
            if (!packageGroups.isEmpty()) {
                logger.debug("Component {} depends on {} package(s)", 
                    component.getId(), packageGroups.size());
            }
        }
    }
    
    /**
     * Extract the package name from a fully qualified class name.
     */
    private String extractPackage(String fullyQualifiedName) {
        if (fullyQualifiedName == null || !fullyQualifiedName.contains(".")) {
            return null;
        }
        
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        return fullyQualifiedName.substring(0, lastDot);
    }
    
    /**
     * Get the identified shared domain.
     */
    public String getSharedDomain() {
        return sharedDomain;
    }
    
    /**
     * Get all package groups.
     */
    public Map<String, Set<String>> getPackageToComponents() {
        return packageToComponents;
    }
}
