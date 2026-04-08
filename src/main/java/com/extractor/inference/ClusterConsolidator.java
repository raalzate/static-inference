package com.extractor.inference;

import com.extractor.model.Component;
import java.util.*;
import java.util.stream.Collectors;

public class ClusterConsolidator {
    private static final Set<String> INFRASTRUCTURE_KEYWORDS = Set.of(
        "config", "configuration", "security", "application", "exception", 
        "error", "filter", "interceptor", "aspect", "swagger", "openapi", "main"
    );
    
    private final List<Cluster> clusters;
    private final InterClusterGraph graph;
    private final Map<Integer, Set<Integer>> mergedGroups;

    public ClusterConsolidator(List<Cluster> clusters, List<Component> allComponents) {
        this.clusters = clusters;
        this.graph = new InterClusterGraph(clusters, allComponents);
        this.mergedGroups = new HashMap<>();
        for (Cluster c : clusters) {
            Set<Integer> group = new HashSet<>();
            group.add(c.getClusterId());
            mergedGroups.put(c.getClusterId(), group);
        }
    }

    public List<Set<Integer>> consolidate() {
        consolidateByDomainName();
        
        List<EdgeCandidate> candidates = findMergeCandidates();
        
        for (EdgeCandidate candidate : candidates) {
            int rootA = findRoot(candidate.clusterIdA);
            int rootB = findRoot(candidate.clusterIdB);
            
            if (rootA != rootB && canMerge(rootA, rootB, candidate.signals)) {
                merge(rootA, rootB);
            }
        }
        
        return mergedGroups.values().stream()
            .filter(group -> !group.isEmpty())
            .distinct()
            .collect(Collectors.toList());
    }
    
    private void consolidateByDomainName() {
        Map<String, List<Integer>> nameToClusterIds = new HashMap<>();
        Set<String> genericNames = Set.of("Componente de Negocio", "Componente Desconocido", 
            "Componente de Infraestructura", "Componente de Configuración");
        
        for (Cluster cluster : clusters) {
            Set<Integer> singleClusterSet = new HashSet<>();
            singleClusterSet.add(cluster.getClusterId());
            String name = MicroserviceNameGenerator.generateName(singleClusterSet, clusters);
            
            if (!genericNames.contains(name)) {
                nameToClusterIds.computeIfAbsent(name, k -> new ArrayList<>())
                    .add(cluster.getClusterId());
            }
        }
        
        for (Map.Entry<String, List<Integer>> entry : nameToClusterIds.entrySet()) {
            List<Integer> clusterIds = entry.getValue();
            if (clusterIds.size() > 1) {
                int firstRoot = findRoot(clusterIds.get(0));
                for (int i = 1; i < clusterIds.size(); i++) {
                    int nextRoot = findRoot(clusterIds.get(i));
                    if (firstRoot != nextRoot && canMergeSameDomain(firstRoot, nextRoot)) {
                        merge(firstRoot, nextRoot);
                        firstRoot = findRoot(firstRoot);
                    }
                }
            }
        }
    }
    
    private boolean canMergeSameDomain(int rootA, int rootB) {
        if (isSupport(rootA) && !isSupport(rootB)) return false;
        if (isSupport(rootB) && !isSupport(rootA)) return false;
        
        boolean isInfraA = hasSignificantInfrastructure(rootA);
        boolean isInfraB = hasSignificantInfrastructure(rootB);
        if (isInfraA != isInfraB) return false;
        
        Set<Integer> groupA = mergedGroups.get(rootA);
        Set<Integer> groupB = mergedGroups.get(rootB);
        int totalSize = getTotalSize(groupA) + getTotalSize(groupB);
        
        if (totalSize > 50) return false;
        
        return true;
    }
    
    private boolean hasSignificantInfrastructure(int clusterId) {
        Set<Integer> group = mergedGroups.get(clusterId);
        if (group == null) return false;
        
        long totalCount = 0;
        long infraCount = 0;
        
        for (int id : group) {
            Cluster cluster = clusters.stream()
                .filter(c -> c.getClusterId() == id)
                .findFirst()
                .orElse(null);
            
            if (cluster != null) {
                totalCount += cluster.getMembers().size();
                infraCount += cluster.getMembers().stream()
                    .filter(member -> {
                        String simpleClassName = member.contains(".") 
                            ? member.substring(member.lastIndexOf('.') + 1).toLowerCase()
                            : member.toLowerCase();
                        return INFRASTRUCTURE_KEYWORDS.stream().anyMatch(simpleClassName::contains);
                    })
                    .count();
            }
        }
        
        return totalCount > 0 && ((double) infraCount / totalCount) >= 0.3;
    }

    private List<EdgeCandidate> findMergeCandidates() {
        List<EdgeCandidate> candidates = new ArrayList<>();
        
        for (int i = 0; i < clusters.size(); i++) {
            for (int j = i + 1; j < clusters.size(); j++) {
                InterClusterGraph.EdgeSignals signals = graph.getEdge(clusters.get(i).getClusterId(), 
                                                                       clusters.get(j).getClusterId());
                if (signals != null && signals.hasStrongEvidence()) {
                    candidates.add(new EdgeCandidate(clusters.get(i).getClusterId(), 
                                                    clusters.get(j).getClusterId(), 
                                                    signals));
                }
            }
        }
        
        candidates.sort((a, b) -> Double.compare(b.signals.getEvidenceScore(), 
                                                a.signals.getEvidenceScore()));
        return candidates;
    }

    private boolean canMerge(int rootA, int rootB, InterClusterGraph.EdgeSignals signals) {
        Set<Integer> groupA = mergedGroups.get(rootA);
        Set<Integer> groupB = mergedGroups.get(rootB);
        
        if (isSupport(rootA) && !isSupport(rootB)) return false;
        if (isSupport(rootB) && !isSupport(rootA)) return false;
        
        int totalSize = getTotalSize(groupA) + getTotalSize(groupB);
        if (totalSize > 40 && signals.getTokenSimilarity() < 0.75) return false;
        
        if (bothStrongCandidates(groupA, groupB) && 
            signals.getCallDensity() < 0.15 && 
            signals.getTableJaccard() < 0.2) {
            return false;
        }
        
        return true;
    }

    private boolean isSupport(int clusterId) {
        Cluster cluster = clusters.stream()
            .filter(c -> c.getClusterId() == clusterId)
            .findFirst()
            .orElse(null);
        
        if (cluster == null) return false;
        
        long infraCount = cluster.getMembers().stream()
            .filter(member -> {
                String simpleClassName = member.contains(".") 
                    ? member.substring(member.lastIndexOf('.') + 1).toLowerCase()
                    : member.toLowerCase();
                return INFRASTRUCTURE_KEYWORDS.stream().anyMatch(simpleClassName::contains);
            })
            .count();
        
        return cluster.getMembers().size() > 0 && 
               ((double) infraCount / cluster.getMembers().size()) >= 0.8;
    }

    private int getTotalSize(Set<Integer> group) {
        return group.stream()
            .map(id -> clusters.stream()
                .filter(c -> c.getClusterId() == id)
                .findFirst()
                .map(c -> c.getMembers().size())
                .orElse(0))
            .reduce(0, Integer::sum);
    }

    private boolean bothStrongCandidates(Set<Integer> groupA, Set<Integer> groupB) {
        boolean strongA = groupA.stream().anyMatch(this::isStrongCandidate);
        boolean strongB = groupB.stream().anyMatch(this::isStrongCandidate);
        return strongA && strongB;
    }

    private boolean isStrongCandidate(int clusterId) {
        Cluster cluster = clusters.stream()
            .filter(c -> c.getClusterId() == clusterId)
            .findFirst()
            .orElse(null);
        
        if (cluster == null) return false;
        
        ClusterMetrics metrics = cluster.getMetrics();
        return metrics.getCohesion() >= 0.7 && 
               metrics.getCoupling() < 0.3 && 
               cluster.getMembers().size() >= 3;
    }

    private int findRoot(int clusterId) {
        for (Map.Entry<Integer, Set<Integer>> entry : mergedGroups.entrySet()) {
            if (entry.getValue().contains(clusterId)) {
                return entry.getKey();
            }
        }
        return clusterId;
    }

    private void merge(int rootA, int rootB) {
        Set<Integer> groupA = mergedGroups.get(rootA);
        Set<Integer> groupB = mergedGroups.get(rootB);
        
        groupA.addAll(groupB);
        mergedGroups.put(rootB, Collections.emptySet());
    }

    private static class EdgeCandidate {
        final int clusterIdA;
        final int clusterIdB;
        final InterClusterGraph.EdgeSignals signals;

        EdgeCandidate(int clusterIdA, int clusterIdB, InterClusterGraph.EdgeSignals signals) {
            this.clusterIdA = clusterIdA;
            this.clusterIdB = clusterIdB;
            this.signals = signals;
        }
    }
}
