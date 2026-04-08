package com.extractor.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a group of components from the same package domain.
 * Used to subdivide internal dependencies by package structure.
 */
public class PackageGroup {
    
    @JsonProperty("package_name")
    private String packageName;
    
    @JsonProperty("components")
    private List<String> components;
    
    @JsonProperty("count")
    private int count;
    
    public PackageGroup() {
        this.components = new ArrayList<>();
        this.count = 0;
    }
    
    public PackageGroup(String packageName) {
        this();
        this.packageName = packageName;
    }
    
    public String getPackageName() {
        return packageName;
    }
    
    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }
    
    public List<String> getComponents() {
        return components;
    }
    
    public void setComponents(List<String> components) {
        this.components = components;
        this.count = components.size();
    }
    
    public void addComponent(String componentId) {
        if (!this.components.contains(componentId)) {
            this.components.add(componentId);
            this.count = this.components.size();
        }
    }
    
    public int getCount() {
        return count;
    }
    
    public void setCount(int count) {
        this.count = count;
    }
}
