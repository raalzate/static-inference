package com.extractor.model;

/**
 * Version of the formal output contract ({@code schema/VERSION}) declared in every artifact as
 * {@code meta.schema_version} (005-json-schema-contract, FR-008). Single place in the Java engine;
 * {@code make schema-check} verifies it matches {@code schema/VERSION} and the .NET analyzer.
 */
public final class SchemaVersion {

    /** Current contract version. */
    public static final String CURRENT = "1.0.0";

    private SchemaVersion() {
    }
}
