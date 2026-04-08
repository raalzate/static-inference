package com.extractor.inference.rules;

import com.extractor.inference.InferenceRule;
import com.extractor.inference.Cluster;
import com.extractor.model.Component;
import java.util.List;

/**
 * Rule that fires when a cluster has low external coupling.
 * Low coupling means the cluster has few dependencies on components outside the cluster.
 */
public class LowCouplingRule implements InferenceRule {
    private static final double LOW_COUPLING_THRESHOLD = 0.3;
    
    @Override
    public boolean evaluate(Cluster cluster, List<Component> allComponents) {
        return cluster.getMetrics().getCoupling() <= LOW_COUPLING_THRESHOLD;
    }
    
    @Override
    public String getRuleName() {
        return "Bajo Acoplamiento Externo";
    }
    
    @Override
    public double getScoreContribution() {
        return 0.4; // 40% of total score
    }
    
    @Override
    public String getExplanation(Cluster cluster, List<Component> allComponents) {
        double coupling = cluster.getMetrics().getCoupling();
        return String.format(" Bajo Acoplamiento (%.0f%%): El grupo tiene pocas dependencias externas, facilitando su aislamiento.", 
                           coupling * 100);
    }
}