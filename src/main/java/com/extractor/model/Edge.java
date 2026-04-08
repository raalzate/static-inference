package com.extractor.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Represents an edge (dependency relationship) in the dependency graph.
 */
public class Edge {
    
    @JsonProperty("from")
    private String from;
    
    @JsonProperty("to") 
    private String to;
    
    @JsonProperty("weight")
    private int weight; // Number of calls/references
    
    @JsonProperty("type")
    private String type; // "call", "db", "external", "reflection"
    
    public Edge() {
    }
    
    public Edge(String from, String to, int weight, String type) {
        this.from = from;
        this.to = to;
        this.weight = weight;
        this.type = type;
    }
    
    public String getFrom() {
        return from;
    }
    
    public void setFrom(String from) {
        this.from = from;
    }
    
    public String getTo() {
        return to;
    }
    
    public void setTo(String to) {
        this.to = to;
    }
    
    public int getWeight() {
        return weight;
    }
    
    public void setWeight(int weight) {
        this.weight = weight;
    }
    
    public void incrementWeight() {
        this.weight++;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Edge edge = (Edge) o;
        return Objects.equals(from, edge.from) && 
               Objects.equals(to, edge.to) && 
               Objects.equals(type, edge.type);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(from, to, type);
    }
    
    @Override
    public String toString() {
        return "Edge{" +
                "from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", weight=" + weight +
                ", type='" + type + '\'' +
                '}';
    }
}