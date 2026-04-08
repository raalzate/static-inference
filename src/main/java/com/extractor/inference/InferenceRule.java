package com.extractor.inference;

import com.extractor.model.Component;
import java.util.List;

/**
 * Interface for inference rules that evaluate microservice candidate clusters.
 */
public interface InferenceRule {
    /**
     * Evaluates if this rule applies to the given cluster.
     * 
     * @param cluster The cluster to evaluate
     * @param allComponents All components in the system for context
     * @return true if the rule fires (applies), false otherwise
     */
    boolean evaluate(Cluster cluster, List<Component> allComponents);
    
    /**
     * Returns the name of this rule for reporting.
     */
    String getRuleName();
    
    /**
     * Returns the score contribution of this rule (0.0 to 1.0).
     */
    double getScoreContribution();
    
    /**
     * Returns a detailed explanation of why this rule fired.
     */
    String getExplanation(Cluster cluster, List<Component> allComponents);
}