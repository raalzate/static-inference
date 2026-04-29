package com.extractor.inference;

import com.extractor.model.ApiEndpoint;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LegacyEntrypoint {

    @JsonProperty("type")
    private String type;

    @JsonProperty("primary_entry_class")
    private String primaryEntryClass;

    @JsonProperty("exposed_operations")
    private List<String> exposedOperations;

    @JsonProperty("messaging_channels")
    private List<String> messagingChannels;

    @JsonProperty("endpoints")
    private List<ApiEndpoint> endpoints;

    @JsonProperty("description")
    private String description;

    public LegacyEntrypoint(String type, String primaryEntryClass,
                            List<String> exposedOperations, List<String> messagingChannels,
                            List<ApiEndpoint> endpoints, String description) {
        this.type = type;
        this.primaryEntryClass = primaryEntryClass;
        this.exposedOperations = exposedOperations;
        this.messagingChannels = messagingChannels;
        this.endpoints = endpoints;
        this.description = description;
    }

    public String getType() { return type; }
    public String getPrimaryEntryClass() { return primaryEntryClass; }
    public List<String> getExposedOperations() { return exposedOperations; }
    public List<String> getMessagingChannels() { return messagingChannels; }
    public List<ApiEndpoint> getEndpoints() { return endpoints; }
    public String getDescription() { return description; }
}
