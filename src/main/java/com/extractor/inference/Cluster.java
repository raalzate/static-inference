package com.extractor.inference;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.ArrayList;

/**
 * Represents a microservice candidate cluster with its members and evaluation metrics.
 */
public class Cluster {
    @JsonProperty("cluster_id")
    private int clusterId;
    
    @JsonProperty("members")
    private List<String> members;
    
    @JsonProperty("metrics")
    private ClusterMetrics metrics;
    
    @JsonProperty("rules_fired")
    private List<String> rulesFired;
    
    @JsonProperty("final_score")
    private double finalScore;
    
    public Cluster() {
        this.members = new ArrayList<>();
        this.rulesFired = new ArrayList<>();
        this.metrics = new ClusterMetrics();
    }
    
    public Cluster(int clusterId) {
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
    
    public List<String> getMembers() {
        return members;
    }
    
    public void setMembers(List<String> members) {
        this.members = members;
    }
    
    public void addMember(String member) {
        this.members.add(member);
    }
    
    public ClusterMetrics getMetrics() {
        return metrics;
    }
    
    public void setMetrics(ClusterMetrics metrics) {
        this.metrics = metrics;
    }
    
    public List<String> getRulesFired() {
        return rulesFired;
    }
    
    public void setRulesFired(List<String> rulesFired) {
        this.rulesFired = rulesFired;
    }
    
    public void addRuleFired(String rule) {
        this.rulesFired.add(rule);
    }
    
    public double getFinalScore() {
        return finalScore;
    }
    
    public void setFinalScore(double finalScore) {
        this.finalScore = finalScore;
    }
}