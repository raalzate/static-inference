package com.extractor.model;

import java.util.HashSet;
import java.util.Set;

/**
 * Enhanced edge data that aggregates multiple types of dependencies between components
 * with weighted relationships, following Sofka's approach.
 */
public class EdgeData {
    private String from;
    private String to;
    private int weight;
    private Set<String> types;
    
    public EdgeData(String from, String to) {
        this.from = from;
        this.to = to;
        this.weight = 0;
        this.types = new HashSet<>();
    }
    
    /**
     * Add a dependency of a specific type with given weight.
     */
    public void addDependency(String type, int weight) {
        this.types.add(type);
        this.weight += weight;
    }
    
    /**
     * Convert to Edge for final output.
     */
    public Edge toEdge() {
        Edge edge = new Edge();
        edge.setFrom(from);
        edge.setTo(to);
        edge.setWeight(weight);
        edge.setType(String.join(",", types));
        return edge;
    }
    
    // Getters
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public int getWeight() { return weight; }
    public Set<String> getTypes() { return types; }
    
    @Override
    public String toString() {
        return String.format("EdgeData{%s -> %s, weight=%d, types=%s}", 
                           from, to, weight, types);
    }
}