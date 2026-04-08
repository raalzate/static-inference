package com.extractor.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Represents information about an external dependency.
 */
public class DependencyInfo {
    
    @JsonProperty("class")
    private String className;
    
    @JsonProperty("dependency")
    private String dependency; // groupId:artifactId:version or null
    
    public DependencyInfo() {
    }
    
    public DependencyInfo(String className, String dependency) {
        this.className = className;
        this.dependency = dependency;
    }
    
    public String getClassName() {
        return className;
    }
    
    public void setClassName(String className) {
        this.className = className;
    }
    
    public String getDependency() {
        return dependency;
    }
    
    public void setDependency(String dependency) {
        this.dependency = dependency;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DependencyInfo that = (DependencyInfo) o;
        return Objects.equals(className, that.className) && Objects.equals(dependency, that.dependency);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(className, dependency);
    }
    
    @Override
    public String toString() {
        return "DependencyInfo{" +
                "className='" + className + '\'' +
                ", dependency='" + dependency + '\'' +
                '}';
    }
}