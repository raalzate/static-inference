package com.extractor.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.HashMap;

/**
 * Represents a simplified OpenAPI schema for DTOs/Entities.
 */
public class ApiSchema {
    private String name;
    private String type = "object";
    private Map<String, Property> properties = new HashMap<>();

    public ApiSchema(String name) {
        this.name = name;
    }

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @JsonProperty("properties")
    public Map<String, Property> getProperties() {
        return properties;
    }

    public void addProperty(String name, String type) {
        properties.put(name, new Property(type));
    }

    public static class Property {
        private String type;

        public Property(String type) {
            this.type = type;
        }

        @JsonProperty("type")
        public String getType() {
            return type;
        }
    }
}
