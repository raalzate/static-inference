package com.extractor.analyzer;

public enum ProjectType {
    JAVA("Java (Maven/Gradle)"),
    DOTNET(".NET (C#)"),
    AMBIGUOUS("Ambiguous (both Java and .NET markers found)"),
    UNKNOWN("Unknown (no recognized project markers)");

    private final String displayName;

    ProjectType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
