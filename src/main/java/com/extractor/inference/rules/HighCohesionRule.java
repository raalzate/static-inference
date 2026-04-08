package com.extractor.inference.rules;

import com.extractor.inference.InferenceRule;
import com.extractor.inference.Cluster;
import com.extractor.model.Component;
import java.util.List;

/**
 * Rule that fires when a cluster has high internal cohesion.
 * High cohesion means components within the cluster call each other frequently.
 */
public class HighCohesionRule implements InferenceRule {
    private static final double HIGH_COHESION_THRESHOLD = 0.7;
    
    @Override
    public boolean evaluate(Cluster cluster, List<Component> allComponents) {
        return cluster.getMetrics().getCohesion() >= HIGH_COHESION_THRESHOLD;
    }
    
    @Override
    public String getRuleName() {
        return "Alta Cohesión Interna";
    }
    
    @Override
    public double getScoreContribution() {
        return 0.4; // 40% of total score
    }
    
    @Override
    public String getExplanation(Cluster cluster, List<Component> allComponents) {
        double cohesion = cluster.getMetrics().getCohesion();
        return String.format("Alta Cohesión (%.0f%%): Las clases de este clúster se llaman mucho entre sí.", 
                           cohesion * 100);
    }
}