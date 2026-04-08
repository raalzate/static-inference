package com.extractor.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Represents an API entry point (REST or Listener).
 */
public class ApiEndpoint {
    private String id;
    private String path;
    private String method; // GET, POST, KAFKA_LISTEN, etc.
    private String description;
    private List<Parameter> parameters = new ArrayList<>();
    private String requestBodySchema;
    private String responseSchema;
    private String componentId;

    public ApiEndpoint() {
    }

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @JsonProperty("path")
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @JsonProperty("method")
    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @JsonProperty("parameters")
    public List<Parameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<Parameter> parameters) {
        this.parameters = parameters;
    }

    @JsonProperty("request_body_schema")
    public String getRequestBodySchema() {
        return requestBodySchema;
    }

    public void setRequestBodySchema(String requestBodySchema) {
        this.requestBodySchema = requestBodySchema;
    }

    @JsonProperty("response_schema")
    public String getResponseSchema() {
        return responseSchema;
    }

    public void setResponseSchema(String responseSchema) {
        this.responseSchema = responseSchema;
    }

    @JsonProperty("component_id")
    public String getComponentId() {
        return componentId;
    }

    public void setComponentId(String componentId) {
        this.componentId = componentId;
    }

    public static class Parameter {
        private String name;
        private String in; // path, query, header
        private String type;
        private boolean required;

        @JsonProperty("name")
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @JsonProperty("in")
        public String getIn() {
            return in;
        }

        public void setIn(String in) {
            this.in = in;
        }

        @JsonProperty("type")
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        @JsonProperty("required")
        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }
    }
}
