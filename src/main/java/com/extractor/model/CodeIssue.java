package com.extractor.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a detected code quality issue or pattern of concern.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeIssue {

    public enum Type {
        BUG_PATTERN,
        CODE_STYLE,
        SECURITY,
        PERFORMANCE
    }

    public enum Severity {
        INFO,
        CRITICAL,
        WARNING,
        ERROR
    }

    @JsonProperty("type")
    private Type type;

    @JsonProperty("severity")
    private Severity severity;

    @JsonProperty("message")
    private String message;

    @JsonProperty("line")
    private int line;

    @JsonProperty("pattern")
    private String pattern;
}
