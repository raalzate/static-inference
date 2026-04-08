package com.extractor.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Represents call information between components.
 */
public class CallInfo {
    
    @JsonProperty("to")
    private String to;
    
    @JsonProperty("count")
    private int count;
    
    @JsonProperty("types")
    private List<String> types;
    
    public CallInfo() {
        this.types = new ArrayList<>();
    }
    
    public CallInfo(String to, int count, List<String> types) {
        this.to = to;
        this.count = count;
        this.types = new ArrayList<>(types);
    }
    
    public String getTo() {
        return to;
    }
    
    public void setTo(String to) {
        this.to = to;
    }
    
    public int getCount() {
        return count;
    }
    
    public void setCount(int count) {
        this.count = count;
    }
    
    public void incrementCount() {
        this.count++;
    }
    
    public List<String> getTypes() {
        return types;
    }
    
    public void setTypes(List<String> types) {
        this.types = types;
    }
    
    public void addType(String type) {
        if (!this.types.contains(type)) {
            this.types.add(type);
        }
    }
    
    /**
     * Normalize the call info by sorting types.
     */
    public void normalize() {
        types.sort(String::compareTo);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CallInfo callInfo = (CallInfo) o;
        return Objects.equals(to, callInfo.to);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(to);
    }
    
    @Override
    public String toString() {
        return "CallInfo{" +
                "to='" + to + '\'' +
                ", count=" + count +
                ", types=" + types +
                '}';
    }
}