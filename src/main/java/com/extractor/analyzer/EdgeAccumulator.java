package com.extractor.analyzer;

import com.extractor.model.Edge;
import com.extractor.model.EdgeData;
import com.extractor.constants.AnalysisConstants;

import java.util.*;
import java.util.stream.Collectors;

public class EdgeAccumulator {
    
    private final Map<String, EdgeData> edgeDataMap = new HashMap<>();
    private final ComponentRegistry componentRegistry;
    
    public EdgeAccumulator(ComponentRegistry componentRegistry) {
        this.componentRegistry = componentRegistry;
    }
    
    public void addDependency(String fromClass, String toClass, String edgeType, int weight) {
        String key = fromClass + "->" + toClass;
        
        EdgeData data = edgeDataMap.computeIfAbsent(key, k -> new EdgeData(fromClass, toClass));
        
        data.addDependency(edgeType, weight);
    }
    
    public void addDependency(String fromClass, String toClass, String edgeType) {
        addDependency(fromClass, toClass, edgeType, AnalysisConstants.CALL_DEPENDENCY_WEIGHT);
    }
    
    public EdgeData getEdgeData(String fromClass, String toClass) {
        String key = fromClass + "->" + toClass;
        return edgeDataMap.get(key);
    }
    
    public boolean hasEdge(String fromClass, String toClass) {
        String key = fromClass + "->" + toClass;
        return edgeDataMap.containsKey(key);
    }
    
    public int size() {
        return edgeDataMap.size();
    }
    
    public void clear() {
        edgeDataMap.clear();
    }
    
    public List<Edge> finalizeEdges() {
        List<Edge> edges = new ArrayList<>();
        
        for (EdgeData data : edgeDataMap.values()) {
            String from = data.getFrom();
            String to = data.getTo();
            
            if (componentRegistry.hasComponent(from) && componentRegistry.hasComponent(to)) {
                String typeString = String.join(",", data.getTypes());
                Edge edge = new Edge(from, to, data.getWeight(), typeString);
                edges.add(edge);
                
                componentRegistry.getComponent(from).addCallOut(to);
                componentRegistry.getComponent(to).addCallIn(from);
            }
        }
        
        edges.sort(Comparator.comparing(Edge::getFrom).thenComparing(Edge::getTo));
        
        return edges;
    }
}
