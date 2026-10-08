package com.extractor.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Contract metadata of the artifacts that had no {@code meta} object
 * before 005-json-schema-contract (consolidated architecture and
 * entrypoints, FR-008). Additive: carries only {@code schema_version}.
 */
public final class DocumentMeta {

    /** Contract version the document was produced with. */
    @JsonProperty("schema_version")
    private String schemaVersion = SchemaVersion.CURRENT;

    /**
     * Returns the contract version.
     *
     * @return the contract version
     */
    public String getSchemaVersion() {
        return schemaVersion;
    }

    /**
     * Sets the contract version.
     *
     * @param version the contract version
     */
    public void setSchemaVersion(final String version) {
        this.schemaVersion = version;
    }
}
