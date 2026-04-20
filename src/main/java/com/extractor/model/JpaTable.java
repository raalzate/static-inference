package com.extractor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class JpaTable {
    @JsonProperty("name")
    private String name;

    @JsonProperty("component")
    private String component;

    @JsonProperty("source")
    private String source;

    @JsonProperty("schema")
    private String schema;

    public JpaTable() {}

    public JpaTable(String name, String component, String source, String schema) {
        this.name = name;
        this.component = component;
        this.source = source;
        this.schema = schema;
    }

    public String getName() { return name; }
    public String getComponent() { return component; }
    public String getSource() { return source; }
    public String getSchema() { return schema; }

    public void setName(String name) { this.name = name; }
    public void setComponent(String component) { this.component = component; }
    public void setSource(String source) { this.source = source; }
    public void setSchema(String schema) { this.schema = schema; }
}
