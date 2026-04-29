package com.extractor.service;

import com.extractor.analyzer.DotNetBridgeAnalyzer;
import com.extractor.analyzer.DotNetToolLocator;
import com.extractor.analyzer.JavaSpoonAnalyzer;
import com.extractor.analyzer.LanguageAnalyzer;
import com.extractor.analyzer.ProjectType;
import com.extractor.analyzer.ProjectTypeDetector;
import com.extractor.inference.ConsolidatedArchitecture;
import com.extractor.inference.InferenceEngine;
import com.extractor.inference.MicroserviceCandidates;
import com.extractor.inference.MicroserviceProposal;
import com.extractor.inference.MicroserviceRecommendationEngine;
import com.extractor.model.Component;
import com.extractor.model.DependencyGraph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared core service that orchestrates the analysis pipeline.
 * Both CLI and MCP adapters delegate to this service (Constitution II).
 * Stateless — creates fresh analyzers per invocation.
 */
public class AnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisService.class);

    /**
     * Runs the full analysis pipeline: dependency graph + architecture proposals + API contracts.
     */
    public AnalysisResult analyzeProject(Path projectRoot) throws Exception {
        validateProjectPath(projectRoot);

        List<String> warnings = new ArrayList<>();

        LanguageAnalyzer analyzer = createAnalyzer(projectRoot);
        DependencyGraph dependencyGraph = analyzer.analyzeProject(projectRoot);

        InferenceEngine inferenceEngine = new InferenceEngine();
        MicroserviceCandidates candidates = inferenceEngine.analyze(dependencyGraph);

        DependencyGraph.ApiContracts apiContracts = dependencyGraph.getApiContracts();

        Map<String, String> projectDeps = analyzer.getDependencyResolver().getAllDependencies();
        MicroserviceRecommendationEngine recommendationEngine = new MicroserviceRecommendationEngine();
        ConsolidatedArchitecture architecture = recommendationEngine.analyzeConsolidated(
                candidates, dependencyGraph.getComponents(), projectDeps, projectRoot,
                apiContracts.getEndpoints());

        return new AnalysisResult(dependencyGraph, architecture, apiContracts, warnings);
    }

    /**
     * Returns only the dependency graph for a project.
     */
    public DependencyGraph getDependencyGraph(Path projectRoot) throws Exception {
        validateProjectPath(projectRoot);
        LanguageAnalyzer analyzer = createAnalyzer(projectRoot);
        return analyzer.analyzeProject(projectRoot);
    }

    /**
     * Returns architecture proposals, optionally filtered by minimum viability.
     *
     * @param minViability minimum viability level: "Alta", "Media", or "Baja" (null for all)
     */
    public ConsolidatedArchitecture getArchitectureProposal(Path projectRoot, String minViability) throws Exception {
        validateProjectPath(projectRoot);

        LanguageAnalyzer analyzer = createAnalyzer(projectRoot);
        DependencyGraph dependencyGraph = analyzer.analyzeProject(projectRoot);

        InferenceEngine inferenceEngine = new InferenceEngine();
        MicroserviceCandidates candidates = inferenceEngine.analyze(dependencyGraph);

        DependencyGraph.ApiContracts apiContracts = dependencyGraph.getApiContracts();

        Map<String, String> projectDeps = analyzer.getDependencyResolver().getAllDependencies();
        MicroserviceRecommendationEngine recommendationEngine = new MicroserviceRecommendationEngine();
        ConsolidatedArchitecture architecture = recommendationEngine.analyzeConsolidated(
                candidates, dependencyGraph.getComponents(), projectDeps, projectRoot,
                apiContracts.getEndpoints());

        if (minViability != null && !minViability.isEmpty()) {
            return filterByViability(architecture, minViability);
        }
        return architecture;
    }

    /**
     * Returns only the API contracts (endpoints + schemas) for a project.
     */
    public DependencyGraph.ApiContracts getApiContracts(Path projectRoot) throws Exception {
        validateProjectPath(projectRoot);
        LanguageAnalyzer analyzer = createAnalyzer(projectRoot);
        DependencyGraph dependencyGraph = analyzer.analyzeProject(projectRoot);
        return dependencyGraph.getApiContracts();
    }

    /**
     * Returns metrics for a specific component identified by its fully qualified class name.
     *
     * @return the component, or null if not found
     */
    public Component getComponentMetrics(Path projectRoot, String componentId) throws Exception {
        validateProjectPath(projectRoot);
        LanguageAnalyzer analyzer = createAnalyzer(projectRoot);
        DependencyGraph dependencyGraph = analyzer.analyzeProject(projectRoot);

        return dependencyGraph.getComponents().stream()
                .filter(c -> c.getId().equals(componentId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns all component IDs in the project (useful for error messages).
     */
    public List<String> getComponentIds(Path projectRoot) throws Exception {
        LanguageAnalyzer analyzer = createAnalyzer(projectRoot);
        DependencyGraph dependencyGraph = analyzer.analyzeProject(projectRoot);
        return dependencyGraph.getComponents().stream()
                .map(Component::getId)
                .sorted()
                .toList();
    }

    /**
     * Creates the appropriate LanguageAnalyzer for the given project
     * based on auto-detected project type.
     */
    private LanguageAnalyzer createAnalyzer(Path projectRoot) {
        ProjectTypeDetector detector = new ProjectTypeDetector();
        ProjectType projectType = detector.detect(projectRoot);

        return switch (projectType) {
            case JAVA -> new JavaSpoonAnalyzer();
            case DOTNET -> createDotNetAnalyzer();
            case AMBIGUOUS -> throw new IllegalArgumentException(
                    "Ambiguous project type: found both Java and .NET markers in '"
                            + projectRoot + "'. Please specify the target language explicitly.");
            case UNKNOWN -> {
                // Fall back to Java for backward compatibility (Constitution I).
                // Projects without marker files were always analyzed as Java before.
                logger.info("No project type markers found in '{}', defaulting to Java analyzer", projectRoot);
                yield new JavaSpoonAnalyzer();
            }
        };
    }

    private LanguageAnalyzer createDotNetAnalyzer() {
        DotNetToolLocator locator = new DotNetToolLocator(getJarDirectory());
        return new DotNetBridgeAnalyzer(locator);
    }

    private Path getJarDirectory() {
        try {
            Path jarPath = Paths.get(
                    AnalysisService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return jarPath.getParent();
        } catch (URISyntaxException | NullPointerException e) {
            logger.debug("Could not resolve JAR directory, using current directory: {}", e.getMessage());
            return Path.of(".");
        }
    }

    private void validateProjectPath(Path projectRoot) {
        if (projectRoot == null) {
            throw new IllegalArgumentException("Project path must not be null");
        }
        if (!Files.exists(projectRoot)) {
            throw new IllegalArgumentException(
                    "Project path '" + projectRoot + "' does not exist");
        }
        if (!Files.isDirectory(projectRoot)) {
            throw new IllegalArgumentException(
                    "Project path '" + projectRoot + "' is not a directory");
        }
    }

    private ConsolidatedArchitecture filterByViability(ConsolidatedArchitecture architecture, String minViability) {
        int minLevel = viabilityLevel(minViability);
        List<MicroserviceProposal> filtered = architecture.getProposals().stream()
                .filter(p -> viabilityLevel(p.getViability()) >= minLevel)
                .toList();

        return new ConsolidatedArchitecture(
                architecture.getProjectMetadata(),
                filtered,
                architecture.getSupportLibraries(),
                architecture.getSummary()
        );
    }

    private int viabilityLevel(String viability) {
        return switch (viability) {
            case "Alta" -> 3;
            case "Media" -> 2;
            case "Baja" -> 1;
            default -> 0;
        };
    }
}
