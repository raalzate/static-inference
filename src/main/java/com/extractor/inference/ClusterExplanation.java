package com.extractor.inference;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.ArrayList;

/**
 * Explanation for why a cluster was formed and its evaluation.
 */
public class ClusterExplanation {
    @JsonProperty("cluster_id")
    private int clusterId;
    
    @JsonProperty("reasoning")
    private List<String> reasoning;
    
    public ClusterExplanation() {
        this.reasoning = new ArrayList<>();
    }
    
    public ClusterExplanation(int clusterId) {
        this();
        this.clusterId = clusterId;
    }
    
    // Getters and setters
    public int getClusterId() {
        return clusterId;
    }
    
    public void setClusterId(int clusterId) {
        this.clusterId = clusterId;
    }
    
    public List<String> getReasoning() {
        return reasoning;
    }
    
    public void setReasoning(List<String> reasoning) {
        this.reasoning = reasoning;
    }
    
    public void addReasoning(String reason) {
        this.reasoning.add(reason);
    }
}