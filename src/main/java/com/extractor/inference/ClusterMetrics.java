package com.extractor.inference;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.ArrayList;

/**
 * Metrics calculated for a microservice candidate cluster.
 */
public class ClusterMetrics {
    @JsonProperty("cohesion")
    private double cohesion;
    
    @JsonProperty("coupling")
    private double coupling;
    
    @JsonProperty("tables_shared")
    private List<String> tablesShared;
    
    @JsonProperty("sensitive")
    private boolean sensitive;
    
    @JsonProperty("loc")
    private int loc;
    
    public ClusterMetrics() {
        this.tablesShared = new ArrayList<>();
    }
    
    // Getters and setters
    public double getCohesion() {
        return cohesion;
    }
    
    public void setCohesion(double cohesion) {
        this.cohesion = cohesion;
    }
    
    public double getCoupling() {
        return coupling;
    }
    
    public void setCoupling(double coupling) {
        this.coupling = coupling;
    }
    
    public List<String> getTablesShared() {
        return tablesShared;
    }
    
    public void setTablesShared(List<String> tablesShared) {
        this.tablesShared = tablesShared;
    }
    
    public void addSharedTable(String table) {
        if (!this.tablesShared.contains(table)) {
            this.tablesShared.add(table);
        }
    }
    
    public boolean isSensitive() {
        return sensitive;
    }
    
    public void setSensitive(boolean sensitive) {
        this.sensitive = sensitive;
    }
    
    public int getLoc() {
        return loc;
    }
    
    public void setLoc(int loc) {
        this.loc = loc;
    }
}