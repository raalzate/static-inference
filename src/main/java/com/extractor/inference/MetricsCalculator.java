package com.extractor.inference;

import com.extractor.model.Component;
import com.extractor.model.DependencyGraph;
import com.extractor.model.Edge;
import java.util.*;

/**
 * Calculates metrics for microservice candidate clusters.
 */
public class MetricsCalculator {
    
    /**
     * Calculates all metrics for a cluster.
     */
    public ClusterMetrics calculateMetrics(Cluster cluster, DependencyGraph graph) {
        ClusterMetrics metrics = new ClusterMetrics();
        
        List<Component> clusterComponents = getClusterComponents(cluster, graph.getComponents());
        
        // Calculate cohesion (internal calls / total possible internal calls)
        metrics.setCohesion(calculateCohesion(cluster, graph));
        
        // Calculate coupling (external calls / total calls)
        metrics.setCoupling(calculateCoupling(cluster, graph));
        
        // Find shared tables
        metrics.setTablesShared(findSharedTables(clusterComponents));
        
        // Check for sensitive data
        metrics.setSensitive(hasSensitiveData(clusterComponents));
        
        // Calculate lines of code
        metrics.setLoc(calculateLOC(clusterComponents));
        
        return metrics;
    }
    
    /**
     * Calculates cohesion: how much components within the cluster call each other.
     */
    private double calculateCohesion(Cluster cluster, DependencyGraph graph) {
        Set<String> clusterMembers = new HashSet<>(cluster.getMembers());
        if (clusterMembers.size() <= 1) {
            return 0.0; // Single component has no internal cohesion
        }
        
        int internalCalls = 0;
        int totalPossibleCalls = 0;
        
        for (Edge edge : graph.getEdges()) {
            boolean fromInCluster = clusterMembers.contains(edge.getFrom());
            boolean toInCluster = clusterMembers.contains(edge.getTo());
            
            if (fromInCluster && toInCluster) {
                internalCalls += edge.getWeight();
            }
            
            if (fromInCluster) {
                totalPossibleCalls += edge.getWeight();
            }
        }
        
        return totalPossibleCalls > 0 ? (double) internalCalls / totalPossibleCalls : 0.0;
    }
    
    /**
     * Calculates coupling: how much the cluster depends on external components.
     */
    private double calculateCoupling(Cluster cluster, DependencyGraph graph) {
        Set<String> clusterMembers = new HashSet<>(cluster.getMembers());
        
        int externalCalls = 0;
        int totalCalls = 0;
        
        for (Edge edge : graph.getEdges()) {
            boolean fromInCluster = clusterMembers.contains(edge.getFrom());
            boolean toInCluster = clusterMembers.contains(edge.getTo());
            
            if (fromInCluster) {
                totalCalls += edge.getWeight();
                if (!toInCluster) {
                    externalCalls += edge.getWeight();
                }
            }
        }
        
        return totalCalls > 0 ? (double) externalCalls / totalCalls : 0.0;
    }
    
    /**
     * Finds tables that are shared among cluster components.
     * Only returns tables used by at least 2 components in the cluster.
     */
    private List<String> findSharedTables(List<Component> components) {
        Map<String, Integer> tableCount = new HashMap<>();
        
        for (Component component : components) {
            if (component.getTablesUsed() != null) {
                for (String table : component.getTablesUsed()) {
                    tableCount.put(table, tableCount.getOrDefault(table, 0) + 1);
                }
            }
        }
        
        // Return only tables used by at least 2 components (actually shared)
        return tableCount.entrySet().stream()
            .filter(entry -> entry.getValue() >= 2)
            .map(Map.Entry::getKey)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Checks if any component in the cluster handles sensitive data.
     */
    private boolean hasSensitiveData(List<Component> components) {
        return components.stream().anyMatch(Component::isSensitiveData);
    }
    
    /**
     * Calculates total lines of code for the cluster.
     */
    private int calculateLOC(List<Component> components) {
        return components.stream().mapToInt(Component::getLoc).sum();
    }
    
    /**
     * Gets the Component objects for a cluster.
     */
    private List<Component> getClusterComponents(Cluster cluster, List<Component> allComponents) {
        Set<String> memberIds = new HashSet<>(cluster.getMembers());
        return allComponents.stream()
                .filter(comp -> memberIds.contains(comp.getId()))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
}