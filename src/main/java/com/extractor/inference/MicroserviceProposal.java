package com.extractor.inference;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class MicroserviceProposal {
    @JsonProperty("id")
    private int id;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("viability")
    private String viability;
    
    @JsonProperty("clusters")
    private List<Integer> clusterIds;
    
    @JsonProperty("components")
    private List<String> componentNames;
    
    @JsonProperty("metrics")
    private ConsolidatedMetrics metrics;
    
    @JsonProperty("signals")
    private Map<String, Object> signals;
    
    @JsonProperty("rationale")
    private List<String> rationale;
    
    @JsonProperty("recommended_actions")
    private List<String> recommendedActions;

    public MicroserviceProposal(int id, String name, String viability, 
                               List<Integer> clusterIds, List<String> componentNames,
                               ConsolidatedMetrics metrics, Map<String, Object> signals,
                               List<String> rationale, List<String> recommendedActions) {
        this.id = id;
        this.name = name;
        this.viability = viability;
        this.clusterIds = clusterIds;
        this.componentNames = componentNames;
        this.metrics = metrics;
        this.signals = signals;
        this.rationale = rationale;
        this.recommendedActions = recommendedActions;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getViability() { return viability; }
    public List<Integer> getClusterIds() { return clusterIds; }
    public List<String> getComponentNames() { return componentNames; }
    public ConsolidatedMetrics getMetrics() { return metrics; }
    public Map<String, Object> getSignals() { return signals; }
    public List<String> getRationale() { return rationale; }
    public List<String> getRecommendedActions() { return recommendedActions; }

    public static class ConsolidatedMetrics {
        @JsonProperty("size")
        private int size;
        
        @JsonProperty("cohesion_avg")
        private double cohesionAvg;
        
        @JsonProperty("external_coupling")
        private double externalCoupling;
        
        @JsonProperty("internal_edge_density")
        private double internalEdgeDensity;
        
        @JsonProperty("data_jaccard")
        private double dataJaccard;
        
        @JsonProperty("tables")
        private List<String> tables;
        
        @JsonProperty("sensitive")
        private boolean sensitive;

        public ConsolidatedMetrics(int size, double cohesionAvg, double externalCoupling,
                                  double internalEdgeDensity, double dataJaccard,
                                  List<String> tables, boolean sensitive) {
            this.size = size;
            this.cohesionAvg = cohesionAvg;
            this.externalCoupling = externalCoupling;
            this.internalEdgeDensity = internalEdgeDensity;
            this.dataJaccard = dataJaccard;
            this.tables = tables;
            this.sensitive = sensitive;
        }

        public int getSize() { return size; }
        public double getCohesionAvg() { return cohesionAvg; }
        public double getExternalCoupling() { return externalCoupling; }
        public double getInternalEdgeDensity() { return internalEdgeDensity; }
        public double getDataJaccard() { return dataJaccard; }
        public List<String> getTables() { return tables; }
        public boolean isSensitive() { return sensitive; }
    }
}
