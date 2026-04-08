package com.extractor.service;

import com.extractor.inference.ConsolidatedArchitecture;
import com.extractor.model.DependencyGraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Value object wrapping the full analysis pipeline output.
 * Used by both CLI and MCP adapters.
 */
public class AnalysisResult {

    private final DependencyGraph dependencyGraph;
    private final ConsolidatedArchitecture architecture;
    private final DependencyGraph.ApiContracts apiContracts;
    private final int componentCount;
    private final int edgeCount;
    private final List<String> warnings;

    public AnalysisResult(DependencyGraph dependencyGraph,
                          ConsolidatedArchitecture architecture,
                          DependencyGraph.ApiContracts apiContracts,
                          List<String> warnings) {
        this.dependencyGraph = dependencyGraph;
        this.architecture = architecture;
        this.apiContracts = apiContracts;
        this.componentCount = dependencyGraph.getComponents().size();
        this.edgeCount = dependencyGraph.getEdges().size();
        this.warnings = warnings != null ? new ArrayList<>(warnings) : new ArrayList<>();
    }

    public DependencyGraph getDependencyGraph() {
        return dependencyGraph;
    }

    public ConsolidatedArchitecture getArchitecture() {
        return architecture;
    }

    public DependencyGraph.ApiContracts getApiContracts() {
        return apiContracts;
    }

    public int getComponentCount() {
        return componentCount;
    }

    public int getEdgeCount() {
        return edgeCount;
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }
}
