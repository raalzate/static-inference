package com.extractor.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Redacted pointer to where a secret lives in the source tree.
 * Per FR-009: NEVER emits the raw value. The {@code redacted} flag is
 * always {@code true} and acts as an explicit signal to downstream
 * consumers that no material was leaked.
 */
public class SecretLocation {
    @JsonProperty("path")
    private String path;

    @JsonProperty("line")
    private int line;

    @JsonProperty("kind")
    private String kind;

    @JsonProperty("redacted")
    private boolean redacted = true;

    public SecretLocation() {}

    public SecretLocation(String path, int line, String kind) {
        this.path = path;
        this.line = line;
        this.kind = kind;
        this.redacted = true;
    }

    public String getPath() { return path; }
    public int getLine() { return line; }
    public String getKind() { return kind; }
    public boolean isRedacted() { return redacted; }

    public void setPath(String path) { this.path = path; }
    public void setLine(int line) { this.line = line; }
    public void setKind(String kind) { this.kind = kind; }
}
