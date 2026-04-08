package com.extractor.inference;

import com.extractor.model.Component;
import com.extractor.model.DependencyGraph;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Algorithm for clustering components into microservice candidates.
 * Uses domain information and call relationships to form clusters.
 */
public class ClusteringAlgorithm {

    /**
     * Creates clusters from the dependency graph.
     * Enhanced strategy: Detects project structure and uses appropriate clustering.
     */
    public List<Cluster> createClusters(DependencyGraph dependencyGraph) {
        List<Cluster> clusters = new ArrayList<>();
        Map<String, List<Component>> componentsByDomain = groupComponentsByDomain(dependencyGraph.getComponents());

        // Detect if this is a single-domain project (layered architecture)
        boolean isSingleDomain = isSingleDomainProject(componentsByDomain, dependencyGraph.getComponents().size());

        if (isSingleDomain) {
            // For single-domain projects, use entity-based clustering directly
            clusters = createEntityBasedClusters(dependencyGraph, componentsByDomain);
        } else {
            // For multi-domain projects, try business responsibility clustering first
            clusters = createBusinessResponsibilityClusters(dependencyGraph, componentsByDomain);

            // VALIDATE: Check for domain purity (no cluster should mix multiple domains)
            boolean hasMultiDomainClusters = hasCrossDomainMixing(clusters, dependencyGraph.getComponents());

            // If clusters mix domains, or clustering doesn't work well, fallback to
            // domain-based
            if (hasMultiDomainClusters || clusters.size() < 2
                    || hasLargeSingleCluster(clusters, dependencyGraph.getComponents().size())) {
                clusters = createDomainBasedClusters(dependencyGraph, componentsByDomain);
            }

            // If we still get too few clusters, try to split based on JPA entities and
            // dependencies
            if (clusters.size() < 2) {
                clusters = createEntityBasedClusters(dependencyGraph, componentsByDomain);
            }
        }

        return clusters;
    }

    /**
     * Detects if this is a single-domain project (layered architecture).
     * Returns true if >75% of components are in the same domain.
     */
    private boolean isSingleDomainProject(Map<String, List<Component>> componentsByDomain, int totalComponents) {
        if (componentsByDomain.isEmpty())
            return false;

        // Find the largest domain
        int maxDomainSize = componentsByDomain.values().stream()
                .mapToInt(List::size)
                .max()
                .orElse(0);

        // If one domain has >75% of components, it's a single-domain project
        return maxDomainSize > (totalComponents * 0.75);
    }

    /**
     * Create clusters primarily based on domain packages.
     * Completely dynamic - no hardcoded domain lists.
     */
    private List<Cluster> createDomainBasedClusters(DependencyGraph dependencyGraph,
            Map<String, List<Component>> componentsByDomain) {
        List<Cluster> clusters = new ArrayList<>();
        int clusterId = 0;

        Set<String> usedComponents = new HashSet<>();

        // Process all domains dynamically (except "core")
        // Sort domains by component count (descending) to prioritize larger domains
        List<Map.Entry<String, List<Component>>> sortedDomains = componentsByDomain.entrySet().stream()
                .filter(e -> !e.getKey().equals("core") && e.getValue().size() >= 2)
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .collect(Collectors.toList());

        for (Map.Entry<String, List<Component>> entry : sortedDomains) {
            String domain = entry.getKey();
            List<Component> domainComponents = entry.getValue();

            Cluster cluster = new Cluster(clusterId++);

            for (Component component : domainComponents) {
                if (!usedComponents.contains(component.getId())) {
                    cluster.addMember(component.getId());
                    usedComponents.add(component.getId());
                }
            }

            if (!cluster.getMembers().isEmpty()) {
                clusters.add(cluster);
            }
        }

        // Handle core components - distribute to existing clusters or create new ones
        List<Component> coreComponents = componentsByDomain.get("core");
        if (coreComponents != null && !coreComponents.isEmpty()) {
            for (Component component : coreComponents) {
                if (!usedComponents.contains(component.getId())) {
                    if (!clusters.isEmpty()) {
                        // Add to the first cluster (usually the main application cluster)
                        clusters.get(0).addMember(component.getId());
                        usedComponents.add(component.getId());
                    } else {
                        // Create a core cluster if no other clusters exist
                        Cluster coreCluster = new Cluster(clusterId++);
                        coreCluster.addMember(component.getId());
                        clusters.add(coreCluster);
                        usedComponents.add(component.getId());
                    }
                }
            }
        }

        // Add any remaining components to appropriate clusters
        for (Component component : dependencyGraph.getComponents()) {
            if (!usedComponents.contains(component.getId())) {
                if (!clusters.isEmpty()) {
                    // Find the best cluster based on package similarity
                    int bestClusterIndex = findBestClusterForComponent(component, clusters,
                            dependencyGraph.getComponents());
                    clusters.get(bestClusterIndex).addMember(component.getId());
                } else {
                    // Create a new cluster if none exist
                    Cluster newCluster = new Cluster(clusterId++);
                    newCluster.addMember(component.getId());
                    clusters.add(newCluster);
                }
            }
        }

        return clusters;
    }

    /**
     * Create clusters based on JPA entities and their associated components.
     */
    private List<Cluster> createEntityBasedClusters(DependencyGraph dependencyGraph,
            Map<String, List<Component>> componentsByDomain) {
        List<Cluster> clusters = new ArrayList<>();
        Map<String, Cluster> entityToClusters = new HashMap<>();
        int clusterId = 0;

        // Find JPA entities and create clusters around them
        for (Component component : dependencyGraph.getComponents()) {
            if (isJpaEntity(component)) {
                String entityName = extractEntityName(component.getId());
                Cluster cluster = new Cluster(clusterId++);
                cluster.addMember(component.getId());
                clusters.add(cluster);
                entityToClusters.put(entityName, cluster);
            }
        }

        // Group related components (repositories, services) with their entities
        Set<String> assignedComponents = new HashSet<>();

        for (Component component : dependencyGraph.getComponents()) {
            if (assignedComponents.contains(component.getId()))
                continue;

            String componentName = component.getId();
            for (String entityName : entityToClusters.keySet()) {
                if (isRelatedToEntity(componentName, entityName)) {
                    entityToClusters.get(entityName).addMember(componentName);
                    assignedComponents.add(componentName);
                    break;
                }
            }
        }

        // Handle remaining components
        for (Component component : dependencyGraph.getComponents()) {
            if (!assignedComponents.contains(component.getId())) {
                if (!clusters.isEmpty()) {
                    clusters.get(0).addMember(component.getId());
                } else {
                    Cluster defaultCluster = new Cluster(clusterId++);
                    defaultCluster.addMember(component.getId());
                    clusters.add(defaultCluster);
                }
            }
        }

        return clusters.isEmpty() ? List.of(createSingleCluster(dependencyGraph)) : clusters;
    }

    /**
     * Creates clusters based on business responsibilities and functional patterns.
     * DOMAIN-AWARE: Partitions by domain first, then groups within each domain.
     */
    private List<Cluster> createBusinessResponsibilityClusters(DependencyGraph dependencyGraph,
            Map<String, List<Component>> componentsByDomain) {
        List<Cluster> clusters = new ArrayList<>();
        Map<String, Cluster> domainFunctionClusters = new HashMap<>();
        Set<String> assignedComponents = new HashSet<>();
        int clusterId = 0;

        // Identify business functions from all components
        Map<String, String> businessFunctions = identifyBusinessFunctions(dependencyGraph.getComponents());

        // Process each domain separately (DOMAIN-AWARE)
        for (Map.Entry<String, List<Component>> domainEntry : componentsByDomain.entrySet()) {
            String domain = domainEntry.getKey();
            List<Component> domainComponents = domainEntry.getValue();

            // Skip infrastructure domain for now
            if (domain.equals("core"))
                continue;

            // Find functions within this domain
            Set<String> domainFunctions = new HashSet<>();
            for (Component component : domainComponents) {
                String function = businessFunctions.get(component.getId());
                if (function != null) {
                    domainFunctions.add(function);
                }
            }

            // If domain has multiple functions, keep them separate; if only one, group all
            // together
            if (domainFunctions.size() == 1) {
                // Single function in domain - one cluster for entire domain
                String function = domainFunctions.iterator().next();
                String clusterKey = domain + "_" + function;
                Cluster domainCluster = new Cluster(clusterId++);

                for (Component component : domainComponents) {
                    domainCluster.addMember(component.getId());
                    assignedComponents.add(component.getId());
                }

                if (!domainCluster.getMembers().isEmpty()) {
                    domainFunctionClusters.put(clusterKey, domainCluster);
                }
            } else if (domainFunctions.size() > 1) {
                // Multiple functions - create clusters per function within domain
                for (String function : domainFunctions) {
                    String clusterKey = domain + "_" + function;
                    if (!domainFunctionClusters.containsKey(clusterKey)) {
                        domainFunctionClusters.put(clusterKey, new Cluster(clusterId++));
                    }
                }

                // Assign components to their function clusters
                for (Component component : domainComponents) {
                    String function = businessFunctions.get(component.getId());
                    if (function != null) {
                        String clusterKey = domain + "_" + function;
                        if (domainFunctionClusters.containsKey(clusterKey)) {
                            domainFunctionClusters.get(clusterKey).addMember(component.getId());
                            assignedComponents.add(component.getId());
                        }
                    }
                }

                // Assign unassigned components within this domain
                for (Component component : domainComponents) {
                    if (!assignedComponents.contains(component.getId())) {
                        String bestMatch = findBestBusinessFunctionMatch(component.getId(), businessFunctions.keySet());
                        if (bestMatch != null) {
                            String function = businessFunctions.get(bestMatch);
                            String clusterKey = domain + "_" + function;
                            if (domainFunctionClusters.containsKey(clusterKey)) {
                                domainFunctionClusters.get(clusterKey).addMember(component.getId());
                                assignedComponents.add(component.getId());
                            }
                        } else {
                            // No best match found - add to any existing cluster for this domain
                            String domainClusterKey = domainFunctionClusters.keySet().stream()
                                    .filter(key -> key.startsWith(domain + "_"))
                                    .findFirst()
                                    .orElse(null);

                            if (domainClusterKey != null) {
                                domainFunctionClusters.get(domainClusterKey).addMember(component.getId());
                                assignedComponents.add(component.getId());
                            }
                        }
                    }
                }
            } else {
                // No functions identified - create single domain cluster
                Cluster domainCluster = new Cluster(clusterId++);
                for (Component component : domainComponents) {
                    domainCluster.addMember(component.getId());
                    assignedComponents.add(component.getId());
                }
                if (!domainCluster.getMembers().isEmpty()) {
                    domainFunctionClusters.put(domain, domainCluster);
                }
            }
        }

        // Handle infrastructure components separately
        Cluster infraCluster = new Cluster(clusterId++);
        for (Component component : dependencyGraph.getComponents()) {
            if (!assignedComponents.contains(component.getId()) && isSharedInfrastructure(component.getId())) {
                infraCluster.addMember(component.getId());
                assignedComponents.add(component.getId());
            }
        }
        if (!infraCluster.getMembers().isEmpty()) {
            domainFunctionClusters.put("infrastructure", infraCluster);
        }

        // Handle core components - assign by domain inference from package/function
        for (Component component : dependencyGraph.getComponents()) {
            if (!assignedComponents.contains(component.getId())) {
                String function = businessFunctions.get(component.getId());
                if (function != null) {
                    // Try to find existing cluster for this function
                    String matchingKey = domainFunctionClusters.keySet().stream()
                            .filter(key -> key.contains(function))
                            .findFirst()
                            .orElse(null);

                    if (matchingKey != null) {
                        domainFunctionClusters.get(matchingKey).addMember(component.getId());
                        assignedComponents.add(component.getId());
                    } else {
                        // Create new cluster for this function
                        Cluster newCluster = new Cluster(clusterId++);
                        newCluster.addMember(component.getId());
                        domainFunctionClusters.put("misc_" + function, newCluster);
                        assignedComponents.add(component.getId());
                    }
                } else {
                    // No function - add to misc cluster
                    if (!domainFunctionClusters.containsKey("misc")) {
                        domainFunctionClusters.put("misc", new Cluster(clusterId++));
                    }
                    domainFunctionClusters.get("misc").addMember(component.getId());
                    assignedComponents.add(component.getId());
                }
            }
        }

        // Convert to list and filter out empty clusters
        clusters = domainFunctionClusters.values().stream()
                .filter(cluster -> !cluster.getMembers().isEmpty())
                .collect(Collectors.toList());

        // CONSOLIDATE: Merge singleton entity/port/DTO clusters into their domain's
        // main cluster
        clusters = consolidateSingletonClusters(clusters, dependencyGraph.getComponents());

        return clusters.isEmpty() ? List.of(createSingleCluster(dependencyGraph)) : clusters;
    }

    /**
     * Identifies business functions from component names (Controller, Service
     * patterns).
     */
    private Map<String, String> identifyBusinessFunctions(List<Component> components) {
        Map<String, String> businessFunctions = new HashMap<>();

        for (Component component : components) {
            String className = component.getId();
            String function = extractBusinessFunction(className);
            if (function != null) {
                businessFunctions.put(className, function);
            }
        }

        return businessFunctions;
    }

    /**
     * Extracts business function/domain from component class name.
     * ONLY considers role-bearing components (Service, Controller, Repository,
     * UseCase, etc.).
     * Returns null for entities, DTOs, events, and domain objects to avoid
     * over-fragmentation.
     */
    private String extractBusinessFunction(String className) {
        // Get simple class name and full package
        String[] parts = className.split("\\.");
        String simpleName = parts[parts.length - 1];

        // Handle classes without package (default package)
        int lastDotIndex = className.lastIndexOf('.');
        String packagePath = lastDotIndex > 0 ? className.substring(0, lastDotIndex) : "";

        // IGNORE entities, DTOs, events, and plain domain objects
        if (simpleName.matches(".*(Entity|Model|Data|Dto|DTO|Event|Command|Query)$")) {
            return null; // Don't extract function from data objects
        }

        // IGNORE simple domain objects in domain/ports packages (likely Lombok
        // entities)
        if ((packagePath.contains(".domain.") || packagePath.contains(".primaryports.") ||
                packagePath.contains(".secondaryports.") || packagePath.endsWith(".domain") ||
                packagePath.endsWith(".primaryports") || packagePath.endsWith(".secondaryports")) &&
                !simpleName.matches(".*(Service|UseCase|Repository|Repo|Db|Publisher|Factory|Handler)$")) {
            return null; // Domain objects without role-bearing suffixes
        }

        // ONLY extract from role-bearing components
        String roleBearingPattern = "^(?:Repository)?(.*?)(?:ServiceImpl|Service|UseCase|Repository|Repo|Controller|Api|API|Operations?|Listener|Publisher|Adapter|Factory|Handler|Db)$";
        java.util.regex.Pattern r = java.util.regex.Pattern.compile(roleBearingPattern);
        java.util.regex.Matcher m = r.matcher(simpleName);

        if (m.find() && m.group(1) != null && !m.group(1).isEmpty()) {
            return m.group(1).toLowerCase();
        }

        return null; // No function extracted if not role-bearing
    }

    /**
     * Finds the best business function match for unassigned components.
     * Fixed: Use simple class names to avoid false matches with package names.
     */
    private String findBestBusinessFunctionMatch(String componentName, Set<String> existingComponents) {
        String simpleComponentName = getSimpleClassName(componentName).toLowerCase();

        for (String existingComponent : existingComponents) {
            String function = extractBusinessFunction(existingComponent);
            if (function != null) {
                // Check if the simple component name contains the function name as a word
                // boundary
                if (containsAsWordBoundary(simpleComponentName, function)) {
                    return existingComponent;
                }
            }
        }
        return null;
    }

    /**
     * Extracts simple class name from fully qualified name.
     */
    private String getSimpleClassName(String fullyQualifiedName) {
        String[] parts = fullyQualifiedName.split("\\.");
        return parts[parts.length - 1];
    }

    /**
     * Checks if a word appears as a word boundary (not substring) in text.
     */
    private boolean containsAsWordBoundary(String text, String word) {
        // Convert camelCase to word boundaries: BookingMapper -> booking mapper
        String normalizedText = text.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase();
        String normalizedWord = word.toLowerCase();

        // Check for exact word match (with word boundaries)
        return normalizedText.contains(normalizedWord + " ") ||
                normalizedText.startsWith(normalizedWord + " ") ||
                normalizedText.endsWith(" " + normalizedWord) ||
                normalizedText.equals(normalizedWord);
    }

    /**
     * Checks if a component is shared infrastructure (config, security, etc.).
     * Enhanced: More comprehensive detection of infrastructure components.
     */
    private boolean isSharedInfrastructure(String className) {
        String simpleName = getSimpleClassName(className);

        return className.contains("Config") ||
                className.contains("Security") ||
                className.contains("Application") ||
                className.contains("Exception") ||
                className.contains("Error") ||
                className.contains("Jwt") ||
                className.contains("Swagger") ||
                className.contains("Seeder") ||
                simpleName.equals("ErrorHandler") ||
                className.contains("Filter") ||
                className.contains(".config.") ||
                className.contains(".exception.");
    }

    /**
     * Checks if clustering resulted in one large cluster dominating others.
     */
    private boolean hasLargeSingleCluster(List<Cluster> clusters, int totalComponents) {
        if (clusters.isEmpty())
            return false;

        int maxClusterSize = clusters.stream()
                .mapToInt(cluster -> cluster.getMembers().size())
                .max()
                .orElse(0);

        // Lowered threshold: If one cluster has more than 50% of components, consider
        // it too large
        return (double) maxClusterSize / totalComponents > 0.5;
    }

    /**
     * Checks if any cluster contains components from multiple business domains.
     * This indicates poor clustering that should fallback to domain-based approach.
     */
    private boolean hasCrossDomainMixing(List<Cluster> clusters, List<Component> allComponents) {
        Map<String, Component> componentMap = new HashMap<>();
        for (Component comp : allComponents) {
            componentMap.put(comp.getId(), comp);
        }

        for (Cluster cluster : clusters) {
            Set<String> domainsInCluster = new HashSet<>();

            for (String memberId : cluster.getMembers()) {
                Component component = componentMap.get(memberId);
                if (component != null && component.getDomain() != null && !component.getDomain().equals("core")) {
                    domainsInCluster.add(component.getDomain());
                }
            }

            // If cluster has more than 1 non-core domain, it's mixing domains
            if (domainsInCluster.size() > 1) {
                return true;
            }
        }

        return false;
    }

    /**
     * Find the best cluster for a component based on package similarity.
     */
    private int findBestClusterForComponent(Component component, List<Cluster> clusters,
            List<Component> allComponents) {
        String componentPackage = extractPackage(component.getId());

        for (int i = 0; i < clusters.size(); i++) {
            Cluster cluster = clusters.get(i);
            for (String memberId : cluster.getMembers()) {
                Component member = allComponents.stream()
                        .filter(c -> c.getId().equals(memberId))
                        .findFirst()
                        .orElse(null);

                if (member != null) {
                    String memberPackage = extractPackage(member.getId());
                    if (componentPackage.equals(memberPackage)) {
                        return i; // Same package, perfect match
                    }
                }
            }
        }

        return 0; // Default to first cluster if no package match
    }

    /**
     * Check if a component is a JPA entity.
     */
    private boolean isJpaEntity(Component component) {
        String className = component.getId().toLowerCase();
        // Check if it has "entity" in the name or uses database tables
        return className.contains("entity") ||
                className.contains(".model.entity.") ||
                (!component.getTablesUsed().isEmpty() && !className.contains("repository")
                        && !className.contains("service"));
    }

    /**
     * Extract entity name from component ID.
     */
    private String extractEntityName(String componentId) {
        String[] parts = componentId.split("\\.");
        String className = parts[parts.length - 1];

        // Remove common suffixes
        String entityName = className.replaceAll("(Entity|Model|Data)$", "").toLowerCase();

        return entityName;
    }

    /**
     * Check if a component is related to a specific entity.
     */
    private boolean isRelatedToEntity(String componentName, String entityName) {
        // Extract simple class name from fully qualified name
        String simpleClassName = componentName.substring(componentName.lastIndexOf('.') + 1).toLowerCase();
        String lowerEntityName = entityName.toLowerCase();

        // Check if class name starts with entity name (e.g., AirportService,
        // AirportRepository, AirportController)
        if (simpleClassName.startsWith(lowerEntityName)) {
            return true;
        }

        // Check if class name contains entity name (e.g., AirportServiceImpl,
        // DefaultAirportService)
        if (simpleClassName.contains(lowerEntityName)) {
            return true;
        }

        // Check for mapper/dto patterns (e.g., AirportMapper, AirportDTO)
        if (simpleClassName.contains(lowerEntityName) || simpleClassName.startsWith(lowerEntityName)) {
            return true;
        }

        return false;
    }

    /**
     * Extract package from fully qualified class name.
     */
    private String extractPackage(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        return lastDot > 0 ? fullyQualifiedName.substring(0, lastDot) : "";
    }

    /**
     * Create a single cluster containing all components (fallback).
     */
    private Cluster createSingleCluster(DependencyGraph dependencyGraph) {
        Cluster cluster = new Cluster(0);
        for (Component component : dependencyGraph.getComponents()) {
            cluster.addMember(component.getId());
        }
        return cluster;
    }

    /**
     * Groups components by their domain field.
     */
    private Map<String, List<Component>> groupComponentsByDomain(List<Component> components) {
        Map<String, List<Component>> groups = new HashMap<>();

        for (Component component : components) {
            String domain = component.getDomain();

            // If domain not pre-assigned, infer from component ID (fully qualified name)
            if (domain == null || domain.isEmpty()) {
                domain = inferDomainFromComponentId(component.getId());
            }

            groups.computeIfAbsent(domain, k -> new ArrayList<>()).add(component);
        }

        return groups;
    }

    /**
     * Infer domain from component ID using package structure analysis.
     * Completely generic - no hardcoded domain lists.
     * Skips organizational prefixes and framework names to find meaningful domain
     * segments.
     */
    private String inferDomainFromComponentId(String componentId) {
        if (componentId == null || componentId.isEmpty()) {
            return "core";
        }

        String[] parts = componentId.split("\\.");

        // For classes without package structure
        if (parts.length <= 1) {
            return "core";
        }

        // Skip organizational prefixes and framework names to find a meaningful segment
        int startIndex = 0;

        // Skip common organizational prefixes (com, org, net, io, etc.)
        if (parts.length > 0 && parts[0].matches("(com|org|net|io|edu|gov)")) {
            startIndex = 1;
        }

        // Skip company/organization name (usually second segment)
        if (parts.length > startIndex + 1) {
            startIndex++;
        }

        // Skip framework/technology names (spring, boot, etc.)
        while (startIndex < parts.length
                && parts[startIndex].matches("(spring|boot|jakarta|javax|hibernate|jpa|monolith)")) {
            startIndex++;
        }

        // The next segment is likely the domain/module name
        if (startIndex < parts.length) {
            String domainCandidate = parts[startIndex].toLowerCase();

            // Skip generic technical terms
            if (domainCandidate.matches("(main|app|application|common|config|configuration|dto|api|rest|web)")) {
                // Try next segment
                startIndex++;
                if (startIndex < parts.length) {
                    domainCandidate = parts[startIndex].toLowerCase();

                    // Skip more technical terms
                    if (!domainCandidate.matches(
                            "(service|services|util|utils|helper|helpers|model|models|entity|entities|controller|controllers|repository|repositories|dao)")) {
                        return domainCandidate;
                    }
                }
            } else {
                return domainCandidate;
            }
        }

        return "core";
    }

    /**
     * Creates clusters within a domain based on relationships and shared data.
     */
    private List<Cluster> createDomainClusters(List<Component> domainComponents, int startClusterId,
            DependencyGraph graph) {
        List<Cluster> clusters = new ArrayList<>();

        // Strategy: Group components that share tables or have strong call
        // relationships
        Map<String, List<Component>> tableGroups = groupBySharedTables(domainComponents);

        int clusterId = startClusterId;
        Set<Component> processed = new HashSet<>();

        // Create clusters for components that share tables
        for (Map.Entry<String, List<Component>> entry : tableGroups.entrySet()) {
            String table = entry.getKey();
            List<Component> tableComponents = entry.getValue();

            if (tableComponents.size() > 1 && !table.isEmpty()) {
                Cluster cluster = new Cluster(clusterId++);
                for (Component comp : tableComponents) {
                    if (!processed.contains(comp)) {
                        cluster.addMember(comp.getId());
                        processed.add(comp);
                    }
                }
                if (!cluster.getMembers().isEmpty()) {
                    clusters.add(cluster);
                }
            }
        }

        // Add remaining unprocessed components as individual clusters
        for (Component component : domainComponents) {
            if (!processed.contains(component)) {
                Cluster cluster = new Cluster(clusterId++);
                cluster.addMember(component.getId());
                clusters.add(cluster);
            }
        }

        return clusters;
    }

    /**
     * Groups components by the tables they use.
     */
    private Map<String, List<Component>> groupBySharedTables(List<Component> components) {
        Map<String, List<Component>> tableGroups = new HashMap<>();

        for (Component component : components) {
            if (component.getTablesUsed() != null && !component.getTablesUsed().isEmpty()) {
                for (String table : component.getTablesUsed()) {
                    tableGroups.computeIfAbsent(table, k -> new ArrayList<>()).add(component);
                }
            }
        }

        return tableGroups;
    }

    /**
     * Consolidates singleton clusters by merging entities/DTOs/ports into their
     * domain's main cluster.
     * This prevents over-fragmentation from Lombok entities and simple domain
     * objects.
     */
    private List<Cluster> consolidateSingletonClusters(List<Cluster> clusters, List<Component> allComponents) {
        // Build component map for quick lookup
        Map<String, Component> componentMap = new HashMap<>();
        for (Component comp : allComponents) {
            componentMap.put(comp.getId(), comp);
        }

        // Group clusters by domain
        Map<String, List<Cluster>> clustersByDomain = new HashMap<>();
        for (Cluster cluster : clusters) {
            Set<String> clusterDomains = new HashSet<>();
            for (String memberId : cluster.getMembers()) {
                Component comp = componentMap.get(memberId);
                if (comp != null && comp.getDomain() != null && !comp.getDomain().equals("core")) {
                    clusterDomains.add(comp.getDomain());
                }
            }

            // Assign cluster to its primary domain (or "core" if none)
            String primaryDomain = clusterDomains.isEmpty() ? "core" : clusterDomains.iterator().next();
            clustersByDomain.computeIfAbsent(primaryDomain, k -> new ArrayList<>()).add(cluster);
        }

        // For each domain, consolidate singleton entity/DTO/port clusters into the
        // largest cluster
        List<Cluster> consolidatedClusters = new ArrayList<>();

        for (Map.Entry<String, List<Cluster>> entry : clustersByDomain.entrySet()) {
            String domain = entry.getKey();
            List<Cluster> domainClusters = entry.getValue();

            if (domainClusters.size() <= 1) {
                // No consolidation needed - only one cluster for this domain
                consolidatedClusters.addAll(domainClusters);
                continue;
            }

            // Find the largest cluster in this domain (target for merging)
            Cluster largestCluster = domainClusters.stream()
                    .max(Comparator.comparingInt(c -> c.getMembers().size()))
                    .orElse(null);

            if (largestCluster == null) {
                consolidatedClusters.addAll(domainClusters);
                continue;
            }

            // Identify singleton clusters with entities/DTOs/ports
            for (Cluster cluster : domainClusters) {
                if (cluster == largestCluster) {
                    continue; // Skip the target cluster
                }

                if (cluster.getMembers().size() == 1) {
                    String memberId = cluster.getMembers().iterator().next();
                    Component member = componentMap.get(memberId);

                    // Merge singleton if:
                    // 1. It's an entity/DTO/port (non-role-bearing), OR
                    // 2. The largest cluster has 3+ members (strong domain cluster exists)
                    boolean shouldMerge = (member != null && shouldMergeIntoMainCluster(member)) ||
                            (largestCluster.getMembers().size() >= 3);

                    if (shouldMerge) {
                        // Merge this singleton into the largest cluster
                        largestCluster.addMember(memberId);
                        // Don't add this cluster to consolidatedClusters (it's been merged)
                    } else {
                        // Keep as separate cluster
                        consolidatedClusters.add(cluster);
                    }
                } else {
                    // Multi-member cluster - keep it
                    consolidatedClusters.add(cluster);
                }
            }

            // Add the (possibly enlarged) largest cluster if not already added
            if (!consolidatedClusters.contains(largestCluster)) {
                consolidatedClusters.add(largestCluster);
            }
        }

        return consolidatedClusters;
    }

    /**
     * Determines if a component should be merged into the main cluster of its
     * domain.
     * Returns true for entities, DTOs, events, ports, and other non-role-bearing
     * components.
     */
    private boolean shouldMergeIntoMainCluster(Component component) {
        String className = component.getId();
        String[] parts = className.split("\\.");
        String simpleName = parts[parts.length - 1];

        // Handle classes without package (default package)
        int lastDotIndex = className.lastIndexOf('.');
        if (lastDotIndex <= 0) {
            return false; // No package info, keep as separate cluster
        }

        String packagePath = className.substring(0, lastDotIndex);

        // Merge entities, DTOs, events, and data objects
        if (simpleName.matches(".*(Entity|Model|Data|Dto|DTO|Event|Command|Query)$")) {
            return true;
        }

        // Merge simple domain objects in domain/ports packages (likely Lombok entities)
        if ((packagePath.contains(".domain.") || packagePath.contains(".primaryports.") ||
                packagePath.contains(".secondaryports.") || packagePath.endsWith(".domain") ||
                packagePath.endsWith(".primaryports") || packagePath.endsWith(".secondaryports"))) {
            // Only keep as separate if it's a role-bearing component
            if (!simpleName.matches(
                    ".*(Service|UseCase|Repository|Repo|Db|Publisher|Factory|Handler|Operations?|Listener|Adapter|Controller)$")) {
                return true; // Merge non-role-bearing domain components
            }
        }

        return false; // Keep as separate cluster
    }
}