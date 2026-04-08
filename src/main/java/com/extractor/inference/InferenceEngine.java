package com.extractor.inference;

import com.extractor.inference.rules.*;
import com.extractor.model.DependencyGraph;
import com.extractor.model.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.*;

/**
 * Main inference engine that analyzes dependency graphs and generates microservice candidates.
 */
public class InferenceEngine {
    private final ClusteringAlgorithm clusteringAlgorithm;
    private final MetricsCalculator metricsCalculator;
    private final ExplanationGenerator explanationGenerator;
    private final List<InferenceRule> rules;
    private final ObjectMapper objectMapper;
    
    public InferenceEngine() {
        this.clusteringAlgorithm = new ClusteringAlgorithm();
        this.metricsCalculator = new MetricsCalculator();
        this.explanationGenerator = new ExplanationGenerator();
        this.rules = initializeRules();
        this.objectMapper = createObjectMapper();
    }
    
    /**
     * Analyzes a dependency graph and generates microservice candidates.
     */
    public MicroserviceCandidates analyze(DependencyGraph dependencyGraph) {
        // Step 1: Create initial clusters
        List<Cluster> clusters = clusteringAlgorithm.createClusters(dependencyGraph);
        
        // Step 2: Calculate metrics for each cluster
        for (Cluster cluster : clusters) {
            ClusterMetrics metrics = metricsCalculator.calculateMetrics(cluster, dependencyGraph);
            cluster.setMetrics(metrics);
        }
        
        // Step 3: Apply inference rules and calculate scores
        for (Cluster cluster : clusters) {
            applyRules(cluster, dependencyGraph.getComponents());
        }
        
        // Step 4: Generate explanations
        MicroserviceCandidates result = new MicroserviceCandidates();
        result.setCandidates(clusters);
        
        for (Cluster cluster : clusters) {
            ClusterExplanation explanation = explanationGenerator.generateExplanation(cluster, dependencyGraph.getComponents());
            result.addExplanation(explanation);
        }
        
        return result;
    }
    
    /**
     * Applies inference rules to a cluster and calculates final score.
     */
    private void applyRules(Cluster cluster, List<Component> allComponents) {
        double totalScore = 0.0;
        
        for (InferenceRule rule : rules) {
            if (rule.evaluate(cluster, allComponents)) {
                cluster.addRuleFired(rule.getRuleName());
                totalScore += rule.getScoreContribution();
            }
        }
        
        cluster.setFinalScore(Math.min(1.0, totalScore)); // Cap at 1.0
    }
    
    /**
     * Initializes the set of inference rules.
     */
    private List<InferenceRule> initializeRules() {
        List<InferenceRule> ruleList = new ArrayList<>();
        ruleList.add(new HighCohesionRule());
        ruleList.add(new LowCouplingRule());
        ruleList.add(new SharedDataRule());
        return ruleList;
    }
    
    /**
     * Creates JSON object mapper with proper formatting.
     */
    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        return mapper;
    }
    
    /**
     * Converts result to JSON string.
     */
    public String toJson(MicroserviceCandidates candidates) throws Exception {
        return objectMapper.writeValueAsString(candidates);
    }
}