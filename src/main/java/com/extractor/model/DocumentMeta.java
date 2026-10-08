package com.extractor.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Contract metadata of the artifacts that had no {@code meta} object before 005-json-schema-contract
 * (consolidated architecture and entrypoints, FR-008). Additive: carries only {@code schema_version}.
 */
public class DocumentMeta {

    @JsonProperty("schema_version")
    private String schemaVersion = SchemaVersion.CURRENT;

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }
}
