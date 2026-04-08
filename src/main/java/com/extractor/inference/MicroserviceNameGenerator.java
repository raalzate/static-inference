package com.extractor.inference;

import java.util.*;
import java.util.stream.Collectors;

public class MicroserviceNameGenerator {
    private static final Set<String> EXCLUDE_TOKENS = Set.of("entity", "model", "data", "dto", 
        "event", "command", "query", "impl", "repository", "service", "controller", "api", "rest", 
        "http", "adapter", "port", "localevents", "rabbit", "jpa", "repo", "dao", "operations",
        "listener", "publisher", "handler", "factory", "db", "activityupdaterimpl", "usecase",
        "primaryports", "secondaryports", "userprofilejpa", "tweetcrud", "suggestion", "suggestions");
    
    private static final Map<String, String> INFRA_KEYWORDS = Map.of(
        "config", "Configuración",
        "security", "Seguridad",
        "auth", "Autenticación",
        "swagger", "Documentación",
        "email", "Notificaciones por Email",
        "notification", "Notificaciones",
        "log", "Logging",
        "audit", "Auditoría",
        "application", "Aplicación Principal"
    );

    public static String generateName(Set<Integer> clusterIds, List<Cluster> allClusters) {
        List<Cluster> clusters = clusterIds.stream()
            .map(id -> allClusters.stream()
                .filter(c -> c.getClusterId() == id)
                .findFirst()
                .orElse(null))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        if (clusters.isEmpty()) return "Componente Desconocido";
        
        if (isInfrastructure(clusters)) {
            return generateInfrastructureName(clusters);
        }
        
        return generateBusinessName(clusters);
    }

    private static boolean isInfrastructure(List<Cluster> clusters) {
        long infraCount = clusters.stream()
            .flatMap(c -> c.getMembers().stream())
            .filter(member -> {
                String lower = member.toLowerCase();
                return INFRA_KEYWORDS.keySet().stream().anyMatch(lower::contains);
            })
            .count();
        
        long totalComponents = clusters.stream()
            .mapToLong(c -> c.getMembers().size())
            .sum();
        
        return totalComponents > 0 && ((double) infraCount / totalComponents) >= 0.8;
    }

    private static String generateInfrastructureName(List<Cluster> clusters) {
        Map<String, Long> keywordCounts = new HashMap<>();
        
        for (Cluster cluster : clusters) {
            for (String member : cluster.getMembers()) {
                String lower = member.toLowerCase();
                for (String keyword : INFRA_KEYWORDS.keySet()) {
                    if (lower.contains(keyword)) {
                        keywordCounts.merge(keyword, 1L, Long::sum);
                    }
                }
            }
        }
        
        if (keywordCounts.isEmpty()) {
            return "Componente de Infraestructura";
        }
        
        List<String> topKeywords = keywordCounts.entrySet().stream()
            .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
            .limit(2)
            .map(e -> INFRA_KEYWORDS.get(e.getKey()))
            .collect(Collectors.toList());
        
        if (topKeywords.size() == 1) {
            return "Componente de " + topKeywords.get(0);
        } else {
            return "Componente de " + String.join(" & ", topKeywords);
        }
    }

    private static String generateBusinessName(List<Cluster> clusters) {
        Map<String, Integer> tokenFrequency = new HashMap<>();
        
        for (Cluster cluster : clusters) {
            Set<String> domainTokens = extractDomainTokens(cluster.getMembers());
            for (String token : domainTokens) {
                tokenFrequency.merge(token, 1, Integer::sum);
            }
        }
        
        if (tokenFrequency.isEmpty()) {
            return "Componente de Negocio";
        }
        
        List<String> topTokens = tokenFrequency.entrySet().stream()
            .sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()))
            .limit(2)
            .map(Map.Entry::getKey)
            .map(MicroserviceNameGenerator::capitalize)
            .collect(Collectors.toList());
        
        if (topTokens.size() == 1) {
            return "Componente de " + topTokens.get(0);
        } else {
            return "Componente de " + String.join(" y ", topTokens);
        }
    }

    private static Set<String> extractDomainTokens(List<String> components) {
        Set<String> tokens = new HashSet<>();
        Set<String> roleKeywords = Set.of("service", "controller", "repository", "repo", "usecase", 
                                         "operations", "listener", "publisher", "adapter", "factory", "handler", "db");
        
        for (String comp : components) {
            String simpleName = comp.substring(comp.lastIndexOf('.') + 1).toLowerCase();
            String packageName = comp.substring(0, Math.max(0, comp.lastIndexOf('.')));
            
            for (String role : roleKeywords) {
                if (simpleName.contains(role)) {
                    String token = simpleName.replaceAll(role + ".*", "")
                                            .replaceAll("repository", "")
                                            .replaceAll("impl", "");
                    if (!token.isEmpty() && !EXCLUDE_TOKENS.contains(token) && token.length() > 2) {
                        tokens.add(token);
                    }
                    
                    String[] packageParts = packageName.split("\\.");
                    if (packageParts.length > 0) {
                        String lastPackage = packageParts[packageParts.length - 1];
                        if (!EXCLUDE_TOKENS.contains(lastPackage) && lastPackage.length() > 2) {
                            tokens.add(lastPackage);
                        }
                    }
                    break;
                }
            }
        }
        
        return tokens;
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
