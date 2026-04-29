package com.extractor.inference;

import com.extractor.model.ApiEndpoint;
import com.extractor.model.Component;
import com.extractor.model.JpaTable;
import com.extractor.model.SecretLocation;
import com.extractor.utils.SecretLocationScanner;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Engine that analyzes microservice candidates and generates consolidated architecture proposal.
 */
public class MicroserviceRecommendationEngine {
    
    private static final Set<String> INFRASTRUCTURE_KEYWORDS = Set.of(
        "config", "configuration", "security", "application", "exception",
        "error", "filter", "interceptor", "aspect", "swagger", "openapi", "main"
    );

    private static final Set<String> REST_METHODS = Set.of(
        "GET", "POST", "PUT", "DELETE", "PATCH", "SOAP", "STRUTS_ACTION"
    );

    private static final Set<String> MESSAGING_METHODS = Set.of(
        "KAFKA_LISTEN", "RABBIT_LISTEN", "JMS_LISTEN"
    );

    /**
     * Analyzes candidates and generates consolidated architecture proposal.
     *
     * <p>Overload without projectRoot skips filesystem-based enrichment
     * (secrets locations scan). Prefer the overload that accepts projectRoot.
     */
    public ConsolidatedArchitecture analyzeConsolidated(MicroserviceCandidates candidates, List<Component> allComponents, Map<String, String> projectDependencies) {
        return analyzeConsolidated(candidates, allComponents, projectDependencies, null, List.of());
    }

    /**
     * Analyzes candidates and generates consolidated architecture proposal,
     * enriched with JPA table attribution per proposal and project-wide
     * redacted secret locations (FR-008, FR-009).
     */
    public ConsolidatedArchitecture analyzeConsolidated(MicroserviceCandidates candidates,
                                                        List<Component> allComponents,
                                                        Map<String, String> projectDependencies,
                                                        Path projectRoot) {
        return analyzeConsolidated(candidates, allComponents, projectDependencies, projectRoot, List.of());
    }

    /**
     * Primary implementation: analyzes candidates and generates consolidated architecture proposal
     * with {@code legacy_entrypoint} on each proposal built from the provided API endpoints.
     */
    public ConsolidatedArchitecture analyzeConsolidated(MicroserviceCandidates candidates,
                                                        List<Component> allComponents,
                                                        Map<String, String> projectDependencies,
                                                        Path projectRoot,
                                                        List<ApiEndpoint> apiEndpoints) {
        List<Cluster> allClusters = candidates.getCandidates();
        
        ClusterConsolidator consolidator = new ClusterConsolidator(allClusters, allComponents);
        List<Set<Integer>> mergedGroups = consolidator.consolidate();
        
        ViabilityScorer scorer = new ViabilityScorer(allClusters, allComponents);
        
        List<MicroserviceProposal> proposals = new ArrayList<>();
        List<ConsolidatedArchitecture.SupportLibrary> supportLibraries = new ArrayList<>();
        Set<String> filteredInfraComponents = new HashSet<>();
        Set<String> assignedComponents = new HashSet<>();

        int proposalId = 0;
        for (Set<Integer> group : mergedGroups) {
            if (group.isEmpty()) continue;

            if (isSupportGroup(group, allClusters)) {
                supportLibraries.add(createSupportLibrary(proposalId++, group, allClusters));
            } else {
                MicroserviceProposal proposal = createProposal(proposalId++, group, allClusters, allComponents, scorer, componentMapById(allComponents), apiEndpoints != null ? apiEndpoints : List.of());
                // Skip proposals that only contain already-assigned components (dedup safety net)
                List<String> newComponents = proposal.getComponentNames().stream()
                    .filter(c -> !assignedComponents.contains(c))
                    .collect(Collectors.toList());
                if (newComponents.isEmpty()) continue;
                assignedComponents.addAll(proposal.getComponentNames());
                proposals.add(proposal);
                
                List<Cluster> clusters = group.stream()
                    .map(cId -> allClusters.stream().filter(c -> c.getClusterId() == cId).findFirst().orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
                
                clusters.stream()
                    .flatMap(c -> c.getMembers().stream())
                    .filter(this::isInfrastructureComponent)
                    .forEach(filteredInfraComponents::add);
            }
        }
        
        if (!filteredInfraComponents.isEmpty()) {
            List<String> sortedInfra = new ArrayList<>(filteredInfraComponents);
            Collections.sort(sortedInfra);
            supportLibraries.add(new ConsolidatedArchitecture.SupportLibrary(
                proposalId++, 
                "Infraestructura y Configuración Filtrada", 
                new ArrayList<>(), 
                sortedInfra
            ));
        }
        
        // Calculate project metadata
        int totalLoc = allComponents.stream().mapToInt(Component::getLoc).sum();
        int componentsWithSecrets = (int) allComponents.stream()
            .filter(c -> c.getSecretsReferences() != null && !c.getSecretsReferences().isEmpty())
            .count();
        
        // Aggregate ALL external dependencies from components (always include)
        Map<String, String> finalDependencies = new java.util.HashMap<>(projectDependencies);
        for (Component comp : allComponents) {
            if (comp.getExternalDependencies() != null) {
                for (String dep : comp.getExternalDependencies()) {
                    // Extract groupId:artifactId as key, full dep as value
                    String[] parts = dep.split(":");
                    if (parts.length >= 2) {
                        String key = parts[0] + ":" + parts[1];
                        finalDependencies.put(key, dep);
                    }
                }
            }
        }
        
        // Aggregate package dependencies
        Map<String, ConsolidatedArchitecture.PackageDependencyInfo> packageDepsMap = aggregatePackageDependencies(allComponents);
        
        // Identify shared domain
        String sharedDomain = identifySharedDomain(allComponents);

        // FR-009: redacted secret locations. Scanner emits pointers only, never values.
        List<SecretLocation> secretsLocations = new ArrayList<>();
        if (projectRoot != null) {
            try {
                secretsLocations = new SecretLocationScanner().scan(projectRoot);
            } catch (RuntimeException ex) {
                secretsLocations = new ArrayList<>();
            }
        }

        ConsolidatedArchitecture.ProjectMetadata metadata = new ConsolidatedArchitecture.ProjectMetadata(
            finalDependencies,
            packageDepsMap,
            allComponents.size(),
            totalLoc,
            componentsWithSecrets,
            sharedDomain,
            secretsLocations
        );
        
        String summary = generateConsolidatedSummary(proposals, supportLibraries);
        
        return new ConsolidatedArchitecture(metadata, proposals, supportLibraries, summary);
    }
    
    private boolean isSupportGroup(Set<Integer> group, List<Cluster> allClusters) {
        List<Cluster> clusters = group.stream()
            .map(id -> allClusters.stream().filter(c -> c.getClusterId() == id).findFirst().orElse(null))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        if (clusters.isEmpty()) return false;
        
        long infraCount = clusters.stream()
            .flatMap(c -> c.getMembers().stream())
            .filter(this::isInfrastructureComponent)
            .count();
        
        long totalCount = clusters.stream()
            .mapToLong(c -> c.getMembers().size())
            .sum();
        
        return totalCount > 0 && ((double) infraCount / totalCount) >= 0.8;
    }
    
    private MicroserviceProposal createProposal(int id, Set<Integer> clusterIds,
                                                List<Cluster> allClusters,
                                                List<Component> allComponents,
                                                ViabilityScorer scorer,
                                                Map<String, Component> componentsById,
                                                List<ApiEndpoint> apiEndpoints) {
        String name = MicroserviceNameGenerator.generateName(clusterIds, allClusters);
        ViabilityScorer.ViabilityResult viabilityResult = scorer.calculateViability(clusterIds);

        List<Cluster> clusters = clusterIds.stream()
            .map(cId -> allClusters.stream().filter(c -> c.getClusterId() == cId).findFirst().orElse(null))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        List<String> componentNames = clusters.stream()
            .flatMap(c -> c.getMembers().stream())
            .distinct()
            .filter(comp -> !isInfrastructureComponent(comp))
            .sorted()
            .collect(Collectors.toList());

        List<JpaTable> jpaTables = buildJpaTables(name, componentNames, componentsById);
        String tablesSource = inferTablesSource(jpaTables, componentNames, componentsById);

        MicroserviceProposal.ConsolidatedMetrics metrics = calculateConsolidatedMetrics(clusters, allComponents, true, tablesSource);
        Map<String, Object> signals = calculateSignalsMap(clusters, allComponents);
        List<String> recommendedActions = generateActions(viabilityResult.getViability(), metrics);
        LegacyEntrypoint legacyEntrypoint = buildLegacyEntrypoint(componentNames, apiEndpoints, componentsById);

        return new MicroserviceProposal(
            id, name, viabilityResult.getViability(),
            new ArrayList<>(clusterIds), componentNames,
            metrics, signals, viabilityResult.getRationale(), recommendedActions,
            jpaTables, legacyEntrypoint
        );
    }

    /**
     * Builds a per-proposal list of JpaTable entries from the members' tables_used sets.
     * Source attribution defaults to DEFAULT; the Java extractor's downstream JPA/SQL
     * detectors may override this in future iterations.
     */
    private List<JpaTable> buildJpaTables(String proposalName,
                                          List<String> componentNames,
                                          Map<String, Component> componentsById) {
        Map<String, JpaTable> byName = new LinkedHashMap<>();
        for (String memberId : componentNames) {
            Component comp = componentsById.get(memberId);
            if (comp == null || comp.getTablesUsed() == null) continue;
            for (String table : comp.getTablesUsed()) {
                if (table == null || table.isBlank()) continue;
                byName.computeIfAbsent(table, t -> new JpaTable(t, proposalName, "DEFAULT", null));
            }
        }
        List<JpaTable> out = new ArrayList<>(byName.values());
        out.sort(Comparator.comparing(JpaTable::getName));
        return out;
    }

    private Map<String, Component> componentMapById(List<Component> allComponents) {
        return allComponents.stream().collect(Collectors.toMap(Component::getId, c -> c, (a, b) -> a));
    }

    /**
     * Picks a value for {@code tables_source} based on which components in
     * the proposal carry ORM markers. Contract: {@code jpa}, {@code ef},
     * {@code sql}, {@code orm}, {@code none}.
     *
     * <p>JPA is inferred from the presence of {@code Entity}/{@code Table}
     * annotations on Java components; EF is inferred from .NET-flavored
     * dependencies ({@code EntityFrameworkCore}) or attributes. Anything
     * else with tables falls back to {@code orm} so non-Java projects
     * still get a meaningful signal.
     */
    private String inferTablesSource(List<JpaTable> jpaTables,
                                     List<String> componentNames,
                                     Map<String, Component> componentsById) {
        if (jpaTables.isEmpty()) return "none";
        boolean jpa = false;
        boolean ef = false;
        for (String id : componentNames) {
            Component comp = componentsById.get(id);
            if (comp == null) continue;
            List<String> annotations = comp.getAnnotations();
            if (annotations != null) {
                for (String a : annotations) {
                    if (a == null) continue;
                    String lower = a.toLowerCase();
                    if (lower.equals("entity") || lower.equals("table")) jpa = true;
                    if (lower.contains("dbcontext") || lower.contains("entityframework")) ef = true;
                }
            }
            List<String> deps = comp.getExternalDependencies();
            if (deps != null) {
                for (String d : deps) {
                    if (d == null) continue;
                    String lower = d.toLowerCase();
                    if (lower.contains("jakarta.persistence") || lower.contains("javax.persistence")
                            || lower.contains("hibernate")) {
                        jpa = true;
                    }
                    if (lower.contains("entityframeworkcore") || lower.contains("entityframework")) {
                        ef = true;
                    }
                }
            }
        }
        if (jpa) return "jpa";
        if (ef) return "ef";
        return "orm";
    }
    
    private LegacyEntrypoint buildLegacyEntrypoint(List<String> componentFQCNs,
                                                    List<ApiEndpoint> allEndpoints,
                                                    Map<String, Component> componentsById) {
        Set<String> componentSet = new HashSet<>(componentFQCNs);
        List<ApiEndpoint> matched = allEndpoints.stream()
            .filter(ep -> ep.getComponentId() != null && componentSet.contains(ep.getComponentId()))
            .collect(Collectors.toList());

        if (matched.isEmpty()) {
            return buildEntrypointFromComponents(componentFQCNs, componentsById);
        }

        boolean hasRest = matched.stream().anyMatch(ep -> isRestMethod(ep.getMethod()));
        boolean hasMessaging = matched.stream().anyMatch(ep -> isMessagingMethod(ep.getMethod()));

        String type = hasRest ? "rest" : hasMessaging ? "messaging" : "service";
        String primaryEntryClass = matched.get(0).getComponentId();

        List<String> exposedOps = matched.stream()
            .map(ep -> ep.getMethod() + " " + ep.getPath())
            .distinct()
            .sorted()
            .collect(Collectors.toList());

        List<String> messagingChannels = null;
        if (hasMessaging) {
            messagingChannels = matched.stream()
                .filter(ep -> isMessagingMethod(ep.getMethod()))
                .map(ApiEndpoint::getPath)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
            if (messagingChannels.isEmpty()) messagingChannels = null;
        }

        return new LegacyEntrypoint(type, primaryEntryClass, exposedOps, messagingChannels,
            matched, generateEntrypointDescription(type, primaryEntryClass, exposedOps, messagingChannels));
    }

    private LegacyEntrypoint buildEntrypointFromComponents(List<String> componentFQCNs,
                                                           Map<String, Component> componentsById) {
        boolean anyMessaging = componentFQCNs.stream()
            .map(componentsById::get)
            .filter(Objects::nonNull)
            .anyMatch(c -> c.getMessagingType() != null);

        if (anyMessaging) {
            String primaryClass = componentFQCNs.stream()
                .map(componentsById::get)
                .filter(c -> c != null && c.getMessagingType() != null)
                .map(Component::getId)
                .findFirst().orElse(null);
            return new LegacyEntrypoint("messaging", primaryClass, List.of(), null, List.of(),
                "Servicio basado en mensajería sin endpoints REST detectados. Integrar vía broker de mensajes.");
        }

        boolean anyEjb = componentFQCNs.stream()
            .map(componentsById::get)
            .filter(Objects::nonNull)
            .anyMatch(c -> c.getEjbType() != null);

        if (anyEjb) {
            String primaryClass = componentFQCNs.stream()
                .map(componentsById::get)
                .filter(c -> c != null && c.getEjbType() != null)
                .map(Component::getId)
                .findFirst().orElse(null);
            return new LegacyEntrypoint("service", primaryClass, List.of(), null, List.of(),
                "Componente EJB sin endpoints REST detectados. Acceso vía JNDI o EJB remoto.");
        }

        return new LegacyEntrypoint("internal", null, List.of(), null, List.of(),
            "No se detectaron endpoints de entrada. Componente interno o de infraestructura.");
    }

    private boolean isRestMethod(String method) {
        return method != null && REST_METHODS.contains(method.toUpperCase());
    }

    private boolean isMessagingMethod(String method) {
        return method != null && MESSAGING_METHODS.contains(method.toUpperCase());
    }

    private String generateEntrypointDescription(String type, String primaryClass,
                                                  List<String> ops, List<String> channels) {
        String simpleName = primaryClass != null
            ? primaryClass.substring(primaryClass.lastIndexOf('.') + 1)
            : "Desconocido";

        return switch (type) {
            case "rest" -> {
                String paths = ops.stream()
                    .map(op -> op.contains(" ") ? op.substring(op.indexOf(' ') + 1) : op)
                    .distinct().limit(3)
                    .collect(Collectors.joining(", "));
                yield "Servicio expuesto vía REST en " + paths + ". Entrada principal: " + simpleName + ".";
            }
            case "messaging" -> {
                String ch = channels != null && !channels.isEmpty()
                    ? String.join(", ", channels)
                    : "canales no identificados";
                yield "Servicio consumidor de mensajes en: " + ch + ". Integrar vía broker de mensajes.";
            }
            case "service" -> "Servicio sin endpoints HTTP directos. Acceso a través de API interna o RPC.";
            default -> "No se detectaron endpoints de entrada. Componente interno o de infraestructura.";
        };
    }

    private ConsolidatedArchitecture.SupportLibrary createSupportLibrary(int id, Set<Integer> clusterIds, List<Cluster> allClusters) {
        String name = MicroserviceNameGenerator.generateName(clusterIds, allClusters);
        
        List<String> componentNames = clusterIds.stream()
            .flatMap(cId -> allClusters.stream()
                .filter(c -> c.getClusterId() == cId)
                .flatMap(c -> c.getMembers().stream()))
            .distinct()
            .sorted()
            .collect(Collectors.toList());
        
        return new ConsolidatedArchitecture.SupportLibrary(id, name, new ArrayList<>(clusterIds), componentNames);
    }
    
    private MicroserviceProposal.ConsolidatedMetrics calculateConsolidatedMetrics(List<Cluster> clusters, List<Component> allComponents, boolean filterInfrastructure, String tablesSource) {
        Set<String> allMembers = clusters.stream()
            .flatMap(c -> c.getMembers().stream())
            .filter(comp -> !filterInfrastructure || !isInfrastructureComponent(comp))
            .collect(Collectors.toSet());
        int size = allMembers.size();
        
        Map<String, Double> componentCohesion = new HashMap<>();
        for (Cluster c : clusters) {
            double clusterCohesion = c.getMetrics().getCohesion();
            for (String member : c.getMembers()) {
                if (!filterInfrastructure || !isInfrastructureComponent(member)) {
                    componentCohesion.put(member, Math.max(
                        componentCohesion.getOrDefault(member, 0.0), clusterCohesion));
                }
            }
        }
        double cohesionAvg = componentCohesion.isEmpty() ? 0.0 : 
            componentCohesion.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        Map<String, Component> componentMap = allComponents.stream()
            .collect(Collectors.toMap(Component::getId, c -> c));
        
        int internalCalls = 0;
        int externalCalls = 0;
        for (String member : allMembers) {
            Component comp = componentMap.get(member);
            if (comp != null && comp.getCallsOut() != null) {
                for (String called : comp.getCallsOut()) {
                    if (allMembers.contains(called)) {
                        internalCalls++;
                    } else {
                        externalCalls++;
                    }
                }
            }
        }
        
        int totalCalls = internalCalls + externalCalls;
        double externalCoupling = totalCalls > 0 ? (double) externalCalls / totalCalls : 0.0;
        
        int possibleEdges = size * (size - 1);
        double internalEdgeDensity = possibleEdges > 0 ? (double) internalCalls / possibleEdges : 0.0;
        
        Set<String> allTables = clusters.stream()
            .flatMap(c -> c.getMetrics().getTablesShared().stream())
            .collect(Collectors.toSet());
        
        double dataJaccard = allTables.size() > 0 ? 0.8 : 0.0;
        
        boolean sensitive = clusters.stream().anyMatch(c -> c.getMetrics().isSensitive());
        
        return new MicroserviceProposal.ConsolidatedMetrics(
            size, cohesionAvg, externalCoupling, internalEdgeDensity,
            dataJaccard, new ArrayList<>(allTables), sensitive, tablesSource
        );
    }
    
    private Map<String, Object> calculateSignalsMap(List<Cluster> clusters, List<Component> allComponents) {
        Map<String, Object> signals = new HashMap<>();
        signals.put("cluster_count", clusters.size());
        signals.put("total_components", clusters.stream().mapToInt(c -> c.getMembers().size()).sum());
        signals.put("avg_cluster_size", clusters.stream().mapToInt(c -> c.getMembers().size()).average().orElse(0.0));
        return signals;
    }
    
    private List<String> generateActions(String viability, MicroserviceProposal.ConsolidatedMetrics metrics) {
        List<String> actions = new ArrayList<>();
        
        if ("Alta".equals(viability)) {
            actions.add("✅ Diseñar como microservicio independiente");
            actions.add("✅ Definir API pública con contratos claros (OpenAPI/gRPC)");
            if (!metrics.getTables().isEmpty()) {
                actions.add("✅ Asignar base de datos exclusiva con ownership de: " + String.join(", ", metrics.getTables()));
            }
            actions.add("✅ Implementar patrones de resiliencia (circuit breaker, retry, timeout)");
            if (metrics.isSensitive()) {
                actions.add("⚠️ Implementar encriptación, auditoría y controles de acceso por datos sensibles");
            }
        } else if ("Media".equals(viability)) {
            actions.add("🔧 Refactorizar para mejorar cohesión y reducir acoplamiento");
            actions.add("🔧 Aplicar principios SOLID (SRP, DIP) para separación de responsabilidades");
            actions.add("🔧 Considerar eventos asíncronos para reducir acoplamiento síncrono");
            actions.add("📋 Re-evaluar después de refactorización");
        } else {
            actions.add("❌ NO implementar como microservicio en estado actual");
            actions.add("🔧 Requiere refactorización profunda o fusión con otros dominios");
            actions.add("💡 Evaluar si debe ser librería compartida o módulo interno");
        }
        
        return actions;
    }
    
    /**
     * Aggregate package dependencies from all components.
     */
    private Map<String, ConsolidatedArchitecture.PackageDependencyInfo> aggregatePackageDependencies(List<Component> allComponents) {
        Map<String, Set<String>> packageToDependsOn = new HashMap<>();
        Map<String, Integer> packageComponentCount = new HashMap<>();
        Map<String, Integer> packageTotalDepsOut = new HashMap<>();
        
        for (Component comp : allComponents) {
            String packageName = extractPackage(comp.getId());
            if (packageName == null) continue;
            
            // Count components per package
            packageComponentCount.put(packageName, packageComponentCount.getOrDefault(packageName, 0) + 1);
            
            // Aggregate package dependencies
            if (comp.getPackageDependencies() != null) {
                for (com.extractor.model.PackageGroup pkgGroup : comp.getPackageDependencies()) {
                    packageToDependsOn.computeIfAbsent(packageName, k -> new HashSet<>())
                        .add(pkgGroup.getPackageName());
                    packageTotalDepsOut.put(packageName, 
                        packageTotalDepsOut.getOrDefault(packageName, 0) + pkgGroup.getCount());
                }
            }
        }
        
        // Convert to PackageDependencyInfo
        Map<String, ConsolidatedArchitecture.PackageDependencyInfo> result = new HashMap<>();
        for (String packageName : packageComponentCount.keySet()) {
            List<String> dependsOn = new ArrayList<>(packageToDependsOn.getOrDefault(packageName, new HashSet<>()));
            Collections.sort(dependsOn);
            
            result.put(packageName, new ConsolidatedArchitecture.PackageDependencyInfo(
                packageComponentCount.get(packageName),
                packageTotalDepsOut.getOrDefault(packageName, 0),
                dependsOn
            ));
        }
        
        return result;
    }
    
    /**
     * Identify the shared domain from components.
     */
    private String identifySharedDomain(List<Component> allComponents) {
        Map<String, Integer> domainCounts = new HashMap<>();
        
        for (Component component : allComponents) {
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
        return domainCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("unknown");
    }
    
    /**
     * Extract package name from fully qualified class name.
     */
    private String extractPackage(String fullyQualifiedName) {
        if (fullyQualifiedName == null || !fullyQualifiedName.contains(".")) {
            return null;
        }
        
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        return fullyQualifiedName.substring(0, lastDot);
    }
    
    private String generateConsolidatedSummary(List<MicroserviceProposal> proposals, List<ConsolidatedArchitecture.SupportLibrary> supportLibraries) {
        StringBuilder summary = new StringBuilder();
        
        summary.append("ANÁLISIS DE ARQUITECTURA - COMPONENTES AGRUPADOS\n");
        summary.append("═════════════════════════════════════════════════\n\n");
        
        long highViability = proposals.stream().filter(p -> "Alta".equals(p.getViability())).count();
        long mediumViability = proposals.stream().filter(p -> "Media".equals(p.getViability())).count();
        long lowViability = proposals.stream().filter(p -> "Baja".equals(p.getViability())).count();
        
        if (!proposals.isEmpty()) {
            summary.append("📋 Módulos Identificados por Cohesión/Acoplamiento:\n");
            summary.append("───────────────────────────────────────────────────\n");
            for (MicroserviceProposal proposal : proposals) {
                summary.append(String.format("• %s → Clusters %s (%d componentes)\n", 
                    proposal.getName(), 
                    proposal.getClusterIds().stream().map(String::valueOf).collect(Collectors.joining(", ")),
                    proposal.getComponentNames().size()));
            }
            summary.append("\n");
        }
        
        if (!supportLibraries.isEmpty()) {
            summary.append("📚 Librerías de Soporte:\n");
            summary.append("────────────────────────\n");
            for (ConsolidatedArchitecture.SupportLibrary lib : supportLibraries) {
                summary.append(String.format("• %s → Clusters %s\n", 
                    lib.getName(), 
                    lib.getClusterIds().stream().map(String::valueOf).collect(Collectors.joining(", "))));
            }
            summary.append("\n");
        }
        
        summary.append("📌 Análisis de Cohesión:\n");
        summary.append("────────────────────────\n");
        summary.append(String.format("✅ Alta cohesión: %d módulo(s) - Componentes fuertemente relacionados\n", highViability));
        summary.append(String.format("⚠️ Media cohesión: %d módulo(s) - Cohesión moderada\n", mediumViability));
        summary.append(String.format("❌ Baja cohesión: %d módulo(s) - Componentes débilmente relacionados\n", lowViability));
        
        return summary.toString();
    }
    
    /**
     * Checks if a component is infrastructure-related.
     * Only checks the simple class name, not the package path, to avoid false positives
     * with package names like "application" in hexagonal architecture.
     */
    private boolean isInfrastructureComponent(String componentName) {
        String simpleClassName = componentName.contains(".") 
            ? componentName.substring(componentName.lastIndexOf('.') + 1).toLowerCase()
            : componentName.toLowerCase();
        
        return INFRASTRUCTURE_KEYWORDS.stream().anyMatch(simpleClassName::contains);
    }
}
