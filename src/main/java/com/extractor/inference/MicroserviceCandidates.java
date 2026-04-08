package com.extractor.inference;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.ArrayList;

/**
 * Complete output of the inference engine containing clusters and explanations.
 */
public class MicroserviceCandidates {
    @JsonProperty("candidates")
    private List<Cluster> candidates;
    
    @JsonProperty("explanations")
    private List<ClusterExplanation> explanations;
    
    public MicroserviceCandidates() {
        this.candidates = new ArrayList<>();
        this.explanations = new ArrayList<>();
    }
    
    // Getters and setters
    public List<Cluster> getCandidates() {
        return candidates;
    }
    
    public void setCandidates(List<Cluster> candidates) {
        this.candidates = candidates;
    }
    
    public void addCandidate(Cluster candidate) {
        this.candidates.add(candidate);
    }
    
    public List<ClusterExplanation> getExplanations() {
        return explanations;
    }
    
    public void setExplanations(List<ClusterExplanation> explanations) {
        this.explanations = explanations;
    }
    
    public void addExplanation(ClusterExplanation explanation) {
        this.explanations.add(explanation);
    }
}