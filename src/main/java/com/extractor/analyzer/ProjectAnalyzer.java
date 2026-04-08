package com.extractor.analyzer;

import com.extractor.model.*;
import com.extractor.constants.AnalysisConstants;
import com.extractor.utils.DependencyResolver;
import com.extractor.utils.DatabaseDetector;
import com.extractor.utils.SensitiveDataDetector;
import com.extractor.utils.EJBDetector;
import com.extractor.utils.SecretsDetector;
import com.extractor.utils.MessagingDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main analyzer class that uses Spoon to analyze Java projects and extract
 * dependency information.
 */
public class ProjectAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(ProjectAnalyzer.class);

    private DependencyResolver dependencyResolver;
    private DatabaseDetector databaseDetector;
    private SensitiveDataDetector sensitiveDataDetector;
    private SecretsDetector secretsDetector;
    private boolean enableLombok;

    // Refactored: Use specialized classes for state management
    private ComponentRegistry componentRegistry;
    private EdgeAccumulator edgeAccumulator;
    private SpoonLauncherFactory launcherFactory;
    private TableNameExtractor tableNameExtractor;
    private ClassNameValidator classNameValidator;
    private StaticCodeAnalyzer staticCodeAnalyzer;
    private OpenApiExtractor openApiExtractor;

    public ProjectAnalyzer() {
        this(false);
    }

    public ProjectAnalyzer(boolean enableLombok) {
        this.enableLombok = enableLombok;
        this.dependencyResolver = new DependencyResolver();
        this.databaseDetector = new DatabaseDetector();
        this.sensitiveDataDetector = new SensitiveDataDetector();
        this.secretsDetector = new SecretsDetector();
        this.componentRegistry = new ComponentRegistry();
        this.edgeAccumulator = new EdgeAccumulator(componentRegistry);
        this.launcherFactory = new SpoonLauncherFactory(enableLombok);
        this.tableNameExtractor = new TableNameExtractor();
        this.classNameValidator = new ClassNameValidator(componentRegistry);
        this.staticCodeAnalyzer = new StaticCodeAnalyzer();
        this.openApiExtractor = new OpenApiExtractor(componentRegistry.getApiContracts());
    }

    /**
     * Get the dependency resolver to access project dependencies.
     */
    public DependencyResolver getDependencyResolver() {
        return dependencyResolver;
    }

    /**
     * Analyzes a Java project and returns a dependency graph.
     */
    public DependencyGraph analyzeProject(Path projectRoot) throws Exception {
        logger.info("Starting analysis of project: {}", projectRoot);

        // Initialize components and edge data
        componentRegistry.clear();
        edgeAccumulator.clear();

        // Load external dependencies from build files
        dependencyResolver.loadDependencies(projectRoot);

        // Set up Spoon launcher using factory
        Launcher launcher = launcherFactory.createLauncher(projectRoot);

        // Build the model
        CtModel model = launcher.buildModel();

        // PASS 1: Analyze all types (classes, interfaces, enums)
        analyzeTypes(model);
        logger.info("Pass 1 completed: {} components found", componentRegistry.size());

        // PASS 2: Analyze method invocations and build call graph
        analyzeInvocations(model);
        logger.info("Pass 2 completed: Call dependencies analyzed");

        // PASS 3: Analyze structural dependencies (repositories, injection, relations)
        analyzeStructuralDependencies(model);
        logger.info("Pass 3 completed: Structural dependencies analyzed");

        // PASS 3.5: Analyze interface implementations and Spring events
        analyzeAdvancedDependencies(model);
        logger.info("Pass 3.5 completed: Interface implementations and events analyzed");

        // PASS 4: Convert EdgeData to final edges and update calls_in/out
        List<Edge> edges = edgeAccumulator.finalizeEdges();
        logger.info("Pass 4 completed: {} edges finalized", edges.size());

        // Normalize all components
        componentRegistry.normalizeAll();

        // Classify components into architectural layers
        componentRegistry.classifyAllLayers();

        // PASS 5: Analyze and group package dependencies
        PackageGroupAnalyzer packageAnalyzer = new PackageGroupAnalyzer(componentRegistry);
        packageAnalyzer.analyzePackageGroups();
        logger.info("Pass 5 completed: Package dependencies grouped by domain");

        logger.info("Analysis completed. Found {} components and {} edges", componentRegistry.size(), edges.size());

        // Get sorted components for deterministic output
        List<Component> sortedComponents = componentRegistry.getSortedComponents();

        DependencyGraph graph = new DependencyGraph(sortedComponents, edges);
        graph.setApiContracts(componentRegistry.getApiContracts());
        return graph;
    }

    /**
     * Analyze all types in the model and create components.
     */
    private void analyzeTypes(CtModel model) {
        logger.info("Analyzing types...");

        // Get all types (classes, interfaces, enums)
        List<CtType<?>> allTypes = model.getAllTypes().stream()
                .filter(type -> !type.isAnonymous()) // Skip anonymous classes
                .collect(Collectors.toList());

        for (CtType<?> type : allTypes) {
            String fullyQualifiedName = type.getQualifiedName();

            // Skip if it's not part of the analyzed project (external library)
            if (isExternalLibrary(fullyQualifiedName)) {
                continue;
            }

            // Skip test classes - we only want production code
            if (isTestType(type)) {
                continue;
            }

            Component component = new Component(fullyQualifiedName);

            // Set file path
            if (type.getPosition().getFile() != null) {
                component.addFile(type.getPosition().getFile().getPath());
            }

            // Mark if this is an interface
            component.setInterface(type instanceof spoon.reflect.declaration.CtInterface);

            // Extract inheritance and interfaces
            extractInheritance(type, component);

            // Extract annotations (class-level and method-level)
            extractAnnotations(type, component);

            // Domain assignment removed - clustering algorithm will handle grouping

            // Count lines of code
            component.setLoc(countLinesOfCode(type));

            // Detect sensitive data
            component.setSensitiveData(sensitiveDataDetector.hasSensitiveData(type));

            // Detect database usage
            List<String> tables = databaseDetector.findTablesUsed(type);
            component.getTablesUsed().addAll(tables);

            // Detect EJB components
            EJBDetector.EJBInfo ejbInfo = EJBDetector.detectEJB(type);
            if (ejbInfo != null) {
                component.setEjbType(ejbInfo.getType());
                component.setUsesJNDI(ejbInfo.usesJNDI());
                logger.debug("Detected EJB: {} of type {}", fullyQualifiedName, ejbInfo.getType());
            }

            // Detect secrets/properties references (patterns only, no values)
            List<String> secretsRefs = secretsDetector.detectSecretReferences(type);
            component.setSecretsReferences(secretsRefs);

            // Detect messaging systems (JMS, Kafka, RabbitMQ, etc.)
            MessagingDetector.MessagingInfo messagingInfo = MessagingDetector.detectMessaging(type);
            if (messagingInfo.getMessagingType() != null) {
                component.setMessagingType(messagingInfo.getMessagingType());
                component.setMessagingRole(messagingInfo.getMessagingRole());
                logger.debug("Detected messaging: {} uses {} as {}",
                        fullyQualifiedName, messagingInfo.getMessagingType(), messagingInfo.getMessagingRole());
            }

            // Find external dependencies
            findExternalDependencies(type, component);

            // Calculate code quality metrics (CBO and LCOM)
            calculateMetrics(type, component);

            // Perform static code analysis for patterns
            staticCodeAnalyzer.analyzeType(type, component);
            // Extract API contracts
            openApiExtractor.extractFromType(type);

            componentRegistry.registerComponent(component);

            logger.debug("Analyzed type: {} (LOC: {}, Tables: {}, Sensitive: {})",
                    fullyQualifiedName, component.getLoc(), tables.size(), component.isSensitiveData());
        }

        logger.info("Analyzed {} types", allTypes.size());
    }

    /**
     * Analyze method invocations to build the call graph.
     */
    private void analyzeInvocations(CtModel model) {
        logger.info("Analyzing method invocations...");

        int totalInvocations = 0;

        for (CtType<?> type : model.getAllTypes()) {
            if (type.isAnonymous() || isExternalLibrary(type.getQualifiedName()) || isTestType(type)) {
                continue;
            }

            String fromClass = type.getQualifiedName();
            Component fromComponent = componentRegistry.getComponent(fromClass);
            if (fromComponent == null)
                continue;

            // Find all method invocations in this type
            List<CtInvocation<?>> invocations = type.getElements(new TypeFilter<>(CtInvocation.class));

            for (CtInvocation<?> invocation : invocations) {
                processInvocation(fromClass, fromComponent, invocation);
                totalInvocations++;
            }

            // Find constructor invocations
            List<CtConstructorCall<?>> constructorCalls = type.getElements(new TypeFilter<>(CtConstructorCall.class));

            for (CtConstructorCall<?> constructorCall : constructorCalls) {
                processConstructorCall(fromClass, fromComponent, constructorCall);
                totalInvocations++;
            }
        }

        logger.info("Processed {} method invocations", totalInvocations);
    }

    /**
     * Process a method invocation and create appropriate edges.
     */
    private void processInvocation(String fromClass, Component fromComponent, CtInvocation<?> invocation) {
        CtExecutableReference<?> executable = invocation.getExecutable();
        if (executable == null)
            return;

        CtTypeReference<?> declaringType = executable.getDeclaringType();
        if (declaringType == null)
            return;

        String toClass = declaringType.getQualifiedName();

        if (!classNameValidator.isValidTargetClass(toClass, fromClass))
            return;

        String edgeType = determineEdgeType(invocation, toClass);

        // Add to edge accumulator
        edgeAccumulator.addDependency(fromClass, toClass, edgeType, AnalysisConstants.CALL_DEPENDENCY_WEIGHT);
    }

    /**
     * Process a constructor call and create appropriate edges.
     */
    private void processConstructorCall(String fromClass, Component fromComponent,
            CtConstructorCall<?> constructorCall) {
        CtTypeReference<?> type = constructorCall.getType();
        if (type == null)
            return;

        String toClass = type.getQualifiedName();
        if (!classNameValidator.isValidTargetClass(toClass, fromClass))
            return;

        String edgeType = determineEdgeType(constructorCall, toClass);

        // Add to edge accumulator
        edgeAccumulator.addDependency(fromClass, toClass, edgeType, AnalysisConstants.CALL_DEPENDENCY_WEIGHT);
    }

    /**
     * Determine the type of edge based on the invocation and target class.
     */
    private String determineEdgeType(Object invocation, String toClass) {
        // Check if it's a database-related call
        if (databaseDetector.isDatabaseCall(toClass, invocation.toString())) {
            return AnalysisConstants.DB_TYPE;
        }

        // Check for reflection-based calls
        if (isReflectionCall(invocation.toString())) {
            return AnalysisConstants.REFLECTION_TYPE;
        }

        // Check if it's an external library call (prioritize external over internal
        // calls)
        if (isRealExternalLibrary(toClass)) {
            return AnalysisConstants.EXTERNAL_TYPE;
        }

        // Check if it's a JDK class call (also external but not third-party library)
        if (isJdkClass(toClass)) {
            return AnalysisConstants.EXTERNAL_TYPE;
        }

        return AnalysisConstants.CALL_TYPE;
    }

    /**
     * PASS 3: Analyze structural dependencies (repositories, injection, relations).
     */
    private void analyzeStructuralDependencies(CtModel model) {
        logger.info("Analyzing structural dependencies...");

        for (CtType<?> type : model.getAllTypes()) {
            if (type.isAnonymous() || isExternalLibrary(type.getQualifiedName()) || isTestType(type)) {
                continue;
            }

            String fromClass = type.getQualifiedName();
            if (!componentRegistry.hasComponent(fromClass))
                continue;

            // (A) Analyze Spring Data Repositories (Repo -> Entity)
            analyzeRepositoryDependencies(type, fromClass);

            // (B) Analyze Field Dependencies (Injection & JPA Relations)
            analyzeFieldDependencies(type, fromClass);

            // (C) Analyze Constructor Dependencies (Injection)
            analyzeConstructorDependencies(type, fromClass);

            // (D) Analyze Method Signature Dependencies (Parameters and Return Types)
            analyzeMethodSignatureDependencies(type, fromClass);
        }
    }

    /**
     * Analyze Spring Data Repository dependencies.
     */
    private void analyzeRepositoryDependencies(CtType<?> type, String fromClass) {
        if (!(type instanceof CtInterface))
            return;

        CtInterface<?> interfaceType = (CtInterface<?>) type;
        for (CtTypeReference<?> superInterface : interfaceType.getSuperInterfaces()) {
            String superInterfaceName = superInterface.getQualifiedName();
            if (superInterfaceName != null && AnalysisConstants.SPRING_REPO_INTERFACES.contains(superInterfaceName)) {
                // Extract entity type from generics
                if (!superInterface.getActualTypeArguments().isEmpty()) {
                    CtTypeReference<?> entityType = superInterface.getActualTypeArguments().get(0);
                    String toClass = entityType.getQualifiedName();

                    if (toClass != null && componentRegistry.hasComponent(toClass)) {
                        edgeAccumulator.addDependency(fromClass, toClass, AnalysisConstants.REPOSITORY_TYPE,
                                AnalysisConstants.REPOSITORY_DEPENDENCY_WEIGHT);
                    }
                }
            }
        }
    }

    /**
     * Analyze field dependencies (injection and JPA relations).
     */
    private void analyzeFieldDependencies(CtType<?> type, String fromClass) {
        for (CtField<?> field : type.getFields()) {
            CtTypeReference<?> fieldType = field.getType();
            if (fieldType == null)
                continue;

            String toClass = fieldType.getQualifiedName();
            if (toClass == null || !componentRegistry.hasComponent(toClass) || fromClass.equals(toClass))
                continue;

            // Check for injection annotations
            boolean hasInjectionAnnotation = field.getAnnotations().stream()
                    .anyMatch(ann -> {
                        String annType = ann.getAnnotationType().getQualifiedName();
                        return AnalysisConstants.SPRING_INJECTION_ANNOTATIONS.contains(annType);
                    });

            // Check for JPA relation annotations
            boolean hasJpaRelationAnnotation = field.getAnnotations().stream()
                    .anyMatch(ann -> {
                        String annType = ann.getAnnotationType().getQualifiedName();
                        return AnalysisConstants.JPA_RELATION_ANNOTATIONS.contains(annType);
                    });

            if (hasInjectionAnnotation || hasJpaRelationAnnotation) {
                String dependencyType = hasInjectionAnnotation ? AnalysisConstants.INJECTION_FIELD_TYPE
                        : AnalysisConstants.RELATION_TYPE;
                edgeAccumulator.addDependency(fromClass, toClass, dependencyType,
                        AnalysisConstants.INJECTION_DEPENDENCY_WEIGHT);
            }
        }
    }

    /**
     * Analyze constructor dependencies (injection).
     */
    private void analyzeConstructorDependencies(CtType<?> type, String fromClass) {
        for (CtConstructor<?> constructor : type.getElements(new TypeFilter<>(CtConstructor.class))) {
            for (CtParameter<?> param : constructor.getParameters()) {
                CtTypeReference<?> paramType = param.getType();
                if (paramType == null)
                    continue;

                String toClass = paramType.getQualifiedName();

                if (toClass == null || !componentRegistry.hasComponent(toClass) || fromClass.equals(toClass))
                    continue;

                edgeAccumulator.addDependency(fromClass, toClass, AnalysisConstants.INJECTION_CONSTRUCTOR_TYPE,
                        AnalysisConstants.INJECTION_DEPENDENCY_WEIGHT);
            }
        }
    }

    /**
     * Analyze method signature dependencies (parameters and return types).
     * This captures types used in method parameters and return types, which are
     * important
     * for clustering related components together.
     */
    private void analyzeMethodSignatureDependencies(CtType<?> type, String fromClass) {
        Set<String> processedTypes = new HashSet<>();

        for (CtMethod<?> method : type.getMethods()) {
            if (method.getType() != null) {
                String returnType = method.getType().getQualifiedName();
                if (returnType != null && componentRegistry.hasComponent(returnType) &&
                        !fromClass.equals(returnType) && !isJdkClass(returnType) &&
                        processedTypes.add(returnType)) {
                    edgeAccumulator.addDependency(fromClass, returnType, AnalysisConstants.USES_TYPE, 1);
                }
            }

            for (CtParameter<?> param : method.getParameters()) {
                CtTypeReference<?> paramType = param.getType();
                if (paramType == null)
                    continue;

                String toClass = paramType.getQualifiedName();
                if (toClass != null && componentRegistry.hasComponent(toClass) &&
                        !fromClass.equals(toClass) && !isJdkClass(toClass) &&
                        processedTypes.add(toClass)) {
                    edgeAccumulator.addDependency(fromClass, toClass, AnalysisConstants.USES_TYPE, 1);
                }
            }
        }

        for (CtField<?> field : type.getFields()) {
            CtTypeReference<?> fieldType = field.getType();
            if (fieldType == null)
                continue;

            String toClass = fieldType.getQualifiedName();
            if (toClass != null && componentRegistry.hasComponent(toClass) &&
                    !fromClass.equals(toClass) && !isJdkClass(toClass) &&
                    processedTypes.add(toClass)) {
                edgeAccumulator.addDependency(fromClass, toClass, AnalysisConstants.USES_TYPE, 1);
            }
        }
    }

    /**
     * PASS 3.5: Analyze advanced dependencies - interface implementations and
     * Spring events.
     */
    private void analyzeAdvancedDependencies(CtModel model) {
        logger.info("Analyzing interface implementations and Spring events...");

        // Analyze interface implementations
        analyzeInterfaceImplementations(model);

        // Analyze Spring events
        analyzeSpringEvents(model);
    }

    /**
     * Analyze interface implementations to connect interfaces with their concrete
     * classes.
     */
    private void analyzeInterfaceImplementations(CtModel model) {
        Map<String, List<String>> interfaceToImplementations = new HashMap<>();

        // First pass: collect all implementations
        for (CtType<?> type : model.getAllTypes()) {
            if (type.isAnonymous() || isExternalLibrary(type.getQualifiedName()) || isTestType(type)) {
                continue;
            }

            if (type instanceof CtClass) {
                CtClass<?> clazz = (CtClass<?>) type;
                String className = clazz.getQualifiedName();

                // Check implemented interfaces
                for (CtTypeReference<?> interfaceRef : clazz.getSuperInterfaces()) {
                    String interfaceName = interfaceRef.getQualifiedName();
                    if (interfaceName != null && componentRegistry.hasComponent(interfaceName)) {
                        interfaceToImplementations.computeIfAbsent(interfaceName, k -> new ArrayList<>())
                                .add(className);
                    }
                }
            }
        }

        // Second pass: create edges between interface usages and implementations
        for (CtType<?> type : model.getAllTypes()) {
            if (type.isAnonymous() || isExternalLibrary(type.getQualifiedName()) || isTestType(type)) {
                continue;
            }

            String fromClass = type.getQualifiedName();
            if (!componentRegistry.hasComponent(fromClass))
                continue;

            // Check field types that are interfaces
            for (CtField<?> field : type.getFields()) {
                CtTypeReference<?> fieldType = field.getType();
                if (fieldType == null)
                    continue;

                String interfaceName = fieldType.getQualifiedName();
                if (interfaceName != null && interfaceToImplementations.containsKey(interfaceName)) {
                    // Connect to all implementations of this interface
                    for (String implementation : interfaceToImplementations.get(interfaceName)) {
                        if (!fromClass.equals(implementation)) {
                            edgeAccumulator.addDependency(fromClass, implementation, AnalysisConstants.INTERFACE_IMPL_TYPE,
                                    AnalysisConstants.INJECTION_DEPENDENCY_WEIGHT);
                        }
                    }
                }
            }

            // Check constructor parameters that are interfaces
            for (CtConstructor<?> constructor : type.getElements(new TypeFilter<>(CtConstructor.class))) {
                for (CtParameter<?> param : constructor.getParameters()) {
                    CtTypeReference<?> paramType = param.getType();
                    if (paramType == null)
                        continue;

                    String interfaceName = paramType.getQualifiedName();
                    if (interfaceName != null && interfaceToImplementations.containsKey(interfaceName)) {
                        // Connect to all implementations of this interface
                        for (String implementation : interfaceToImplementations.get(interfaceName)) {
                            if (!fromClass.equals(implementation)) {
                                edgeAccumulator.addDependency(fromClass, implementation, AnalysisConstants.INTERFACE_IMPL_TYPE,
                                        AnalysisConstants.INJECTION_DEPENDENCY_WEIGHT);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Analyze Spring events - @EventListener methods and
     * ApplicationEventPublisher.publishEvent calls.
     */
    private void analyzeSpringEvents(CtModel model) {
        Map<String, List<String>> eventToListeners = new HashMap<>();
        Map<String, List<String>> publisherToEvents = new HashMap<>();

        // First pass: collect all event listeners
        for (CtType<?> type : model.getAllTypes()) {
            if (type.isAnonymous() || isExternalLibrary(type.getQualifiedName()) || isTestType(type)) {
                continue;
            }

            String className = type.getQualifiedName();
            if (!componentRegistry.hasComponent(className))
                continue;

            // Find @EventListener methods
            for (CtMethod<?> method : type.getMethods()) {
                boolean hasEventListener = method.getAnnotations().stream()
                        .anyMatch(ann -> {
                            String annType = ann.getAnnotationType().getQualifiedName();
                            return "org.springframework.context.event.EventListener".equals(annType);
                        });

                if (hasEventListener && !method.getParameters().isEmpty()) {
                    CtParameter<?> param = method.getParameters().get(0);
                    String eventType = param.getType().getQualifiedName();
                    if (eventType != null) {
                        eventToListeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(className);
                    }
                }
            }
        }

        // Second pass: find publishEvent calls and connect to listeners
        for (CtType<?> type : model.getAllTypes()) {
            if (type.isAnonymous() || isExternalLibrary(type.getQualifiedName()) || isTestType(type)) {
                continue;
            }

            String fromClass = type.getQualifiedName();
            if (!componentRegistry.hasComponent(fromClass))
                continue;

            // Find ApplicationEventPublisher.publishEvent calls
            List<CtInvocation<?>> invocations = type.getElements(new TypeFilter<>(CtInvocation.class));

            for (CtInvocation<?> invocation : invocations) {
                if (invocation.getExecutable() != null &&
                        "publishEvent".equals(invocation.getExecutable().getSimpleName())) {

                    // Try to extract event type from the method call
                    if (!invocation.getArguments().isEmpty()) {
                        String eventType = extractEventType(invocation);
                        if (eventType != null && eventToListeners.containsKey(eventType)) {
                            // Connect to all listeners of this event
                            for (String listener : eventToListeners.get(eventType)) {
                                if (!fromClass.equals(listener)) {
                                    edgeAccumulator.addDependency(fromClass, listener, "spring_event",
                                            AnalysisConstants.CALL_DEPENDENCY_WEIGHT);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Extract event type from publishEvent call.
     */
    private String extractEventType(CtInvocation<?> invocation) {
        if (invocation.getArguments().isEmpty())
            return null;

        try {
            // Try to get type from the first argument
            Object firstArg = invocation.getArguments().get(0);
            if (firstArg instanceof CtConstructorCall) {
                CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) firstArg;
                return constructorCall.getType().getQualifiedName();
            }
            // Could add more sophisticated type extraction here
        } catch (Exception e) {
            // Ignore extraction errors
        }
        return null;
    }

    /**
     * Count lines of code in a type.
     */
    private int countLinesOfCode(CtType<?> type) {
        if (type.getPosition().getFile() == null)
            return 0;

        int startLine = type.getPosition().getLine();
        int endLine = type.getPosition().getEndLine();

        if (startLine > 0 && endLine > 0) {
            return Math.max(1, endLine - startLine + 1);
        }

        return 0;
    }

    /**
     * Find external dependencies used by a type.
     */
    private void findExternalDependencies(CtType<?> type, Component component) {
        Set<String> externalDeps = new HashSet<>();

        // Check imports and used types
        type.getReferencedTypes().forEach(typeRef -> {
            String className = typeRef.getQualifiedName();
            if (isRealExternalLibrary(className)) {
                // Resolve class to Maven/Gradle dependency with version
                String dependency = dependencyResolver.resolveDependency(className);
                if (dependency != null) {
                    externalDeps.add(dependency);
                }
            }
        });

        // Store external dependencies in component
        for (String dep : externalDeps) {
            component.addExternalDependency(dep);
        }
    }

    /**
     * Check if a class belongs to the internal project (not external library).
     */
    private boolean isInternalProjectClass(String className) {
        if (className == null || className.isEmpty())
            return false;

        // Exclude truncated or malformed class names
        if (className.length() < 10 || className.endsWith(".i") ||
                className.contains("<>") || className.contains("nulltype") ||
                !className.contains(".")) {
            return false;
        }

        // Check if it's an external library first
        if (className.startsWith("java.") ||
                className.startsWith("javax.") ||
                className.startsWith("org.springframework") ||
                className.startsWith("org.modelmapper") ||
                className.startsWith("spoon.")) {
            return false;
        }

        // Check if this class is in our components map (which means it's internal to
        // this project)
        return componentRegistry.hasComponent(className);
    }

    /**
     * More strict check for real external libraries, excluding primitives, JDK
     * classes,
     * Spoon classes, and project internal classes.
     */
    private boolean isRealExternalLibrary(String className) {
        if (className == null || className.isEmpty())
            return false;

        // Exclude primitives and built-in types
        if (className.equals("int") || className.equals("boolean") || className.equals("void") ||
                className.equals("long") || className.equals("double") || className.equals("float") ||
                className.equals("char") || className.equals("byte") || className.equals("short") ||
                className.equals("<nulltype>") || className.equals("annotation") ||
                className.startsWith("TypeFilter") || className.contains("<>")) {
            return false;
        }

        // Include JavaEE/Jakarta EE specifications as external dependencies
        if (className.startsWith("javax.persistence.") || // JPA
                className.startsWith("javax.ejb.") || // EJB
                className.startsWith("javax.ws.rs.") || // JAX-RS
                className.startsWith("javax.servlet.") || // Servlets
                className.startsWith("javax.faces.") || // JSF
                className.startsWith("javax.inject.") || // CDI
                className.startsWith("javax.validation.") || // Bean Validation
                className.startsWith("javax.jms.") || // JMS
                className.startsWith("javax.mail.") || // JavaMail
                className.startsWith("javax.transaction.") || // JTA
                className.startsWith("javax.annotation.") || // Common Annotations
                className.startsWith("jakarta.")) { // Jakarta EE
            return true;
        }

        // Exclude other JDK classes
        if (className.startsWith("java.") || className.startsWith("javax.") ||
                className.startsWith("sun.") || className.startsWith("com.sun.")) {
            return false;
        }

        // Exclude Spoon framework classes
        if (className.startsWith("spoon.")) {
            return false;
        }

        // Exclude project internal classes
        if (componentRegistry.hasComponent(className)) {
            return false;
        }

        // Must be a real external library class
        return className.startsWith("org.") || className.startsWith("com.") ||
                className.startsWith("io.") || className.startsWith("net.") ||
                className.startsWith("edu.") || className.startsWith("gov.");
    }

    /**
     * Check if a class is a JDK class.
     */
    private boolean isJdkClass(String className) {
        return className != null &&
                (className.startsWith("java.") || className.startsWith("javax."));
    }

    /**
     * Check if a class is from an external library (not part of the analyzed
     * project).
     */
    private boolean isExternalLibrary(String className) {
        return className != null &&
                (className.startsWith("java.") ||
                        className.startsWith("javax.") ||
                        className.startsWith("org.springframework.") ||
                        className.startsWith("org.hibernate.") ||
                        className.startsWith("org.apache.") ||
                        className.startsWith("com.fasterxml.") ||
                        !isProjectClass(className));
    }

    /**
     * Check if a type is a test class that should be excluded from analysis.
     */
    private boolean isTestType(CtType<?> type) {
        if (type == null)
            return false;

        String className = type.getQualifiedName();
        if (className == null)
            return false;

        // Check file path - exclude anything in test directories
        if (type.getPosition() != null && type.getPosition().getFile() != null) {
            String filePath = type.getPosition().getFile().getPath();
            if (filePath != null && filePath.contains("/src/test/java")) {
                return true;
            }
        }

        // Check class name patterns
        String simpleName = type.getSimpleName();
        if (simpleName != null && (simpleName.endsWith("Test") ||
                simpleName.endsWith("Tests") ||
                simpleName.endsWith("IT") ||
                simpleName.endsWith("Spec"))) {
            return true;
        }

        // Check package name patterns
        if (className.contains(".test.")) {
            return true;
        }

        // Check for test annotations
        return type.getAnnotations().stream()
                .anyMatch(annotation -> {
                    String annotationType = annotation.getType().getQualifiedName();
                    return annotationType.contains("junit") ||
                            annotationType.contains("SpringBootTest") ||
                            annotationType.contains("RunWith") ||
                            annotationType.contains("ExtendWith") ||
                            annotationType.contains("Test");
                });
    }

    /**
     * Check if a class belongs to the project being analyzed.
     */
    private boolean isProjectClass(String className) {
        // Check if class is already in components or if it comes from known project
        // packages
        if (componentRegistry.hasComponent(className)) {
            return true;
        }

        // Check if it's a typical project package (not well-known external library
        // packages)
        return !className.startsWith("java.") &&
                !className.startsWith("javax.") &&
                !className.startsWith("org.springframework.") &&
                !className.startsWith("org.hibernate.") &&
                !className.startsWith("org.apache.") &&
                !className.startsWith("com.fasterxml.") &&
                !className.startsWith("org.modelmapper.") &&
                !className.startsWith("org.slf4j.") &&
                !className.startsWith("lombok.");
    }

    /**
     * Check if an invocation is reflection-based.
     */
    private boolean isReflectionCall(String invocationString) {
        return invocationString.contains("java.lang.reflect.") ||
                invocationString.contains(".getClass()") ||
                invocationString.contains(".forName(") ||
                invocationString.contains(".newInstance(") ||
                invocationString.contains(".getMethod(") ||
                invocationString.contains(".invoke(");
    }

    /**
     * Extract table name from a class name (heuristic).
     */
    private String extractTableNameFromClass(String className) {
        if (className.contains(".")) {
            className = className.substring(className.lastIndexOf(".") + 1);
        }

        // Convert CamelCase to snake_case
        return className.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    /**
     * Extract annotations from a type (class-level and method-level).
     */
    /**
     * Extract inheritance information (extends and implements).
     */
    private void extractInheritance(CtType<?> type, Component component) {
        // Extract superclass (extends)
        if (type instanceof spoon.reflect.declaration.CtClass<?>) {
            spoon.reflect.declaration.CtClass<?> ctClass = (spoon.reflect.declaration.CtClass<?>) type;
            spoon.reflect.reference.CtTypeReference<?> superClass = ctClass.getSuperclass();

            if (superClass != null) {
                String superClassName = superClass.getQualifiedName();
                // Only include if it's not java.lang.Object (default superclass)
                if (superClassName != null && !superClassName.equals("java.lang.Object")) {
                    component.setExtendsClass(superClassName);
                }
            }
        }

        // Extract interfaces (implements)
        java.util.Set<spoon.reflect.reference.CtTypeReference<?>> interfaces = type.getSuperInterfaces();
        if (interfaces != null && !interfaces.isEmpty()) {
            for (spoon.reflect.reference.CtTypeReference<?> interfaceRef : interfaces) {
                String interfaceName = interfaceRef.getQualifiedName();
                if (interfaceName != null) {
                    component.addImplementsInterface(interfaceName);
                }
            }
        }
    }

    private void extractAnnotations(CtType<?> type, Component component) {
        // Extract class-level annotations
        if (type.getAnnotations() != null) {
            for (CtAnnotation<?> annotation : type.getAnnotations()) {
                String annotationName = annotation.getAnnotationType().getSimpleName();
                component.addAnnotation(annotationName);
            }
        }

        // Extract method-level annotations (HTTP methods, etc.)
        // Use getMethods() instead of getAllMethods() to avoid inherited JDK
        // annotations
        if (type instanceof CtClass<?>) {
            CtClass<?> ctClass = (CtClass<?>) type;
            for (CtMethod<?> method : ctClass.getMethods()) {
                if (method.getAnnotations() != null) {
                    for (CtAnnotation<?> annotation : method.getAnnotations()) {
                        String annotationName = annotation.getAnnotationType().getSimpleName();
                        component.addAnnotation(annotationName);
                    }
                }
            }
        }

        // For interfaces, extract method annotations as well
        if (type instanceof CtInterface<?>) {
            CtInterface<?> ctInterface = (CtInterface<?>) type;
            for (CtMethod<?> method : ctInterface.getMethods()) {
                if (method.getAnnotations() != null) {
                    for (CtAnnotation<?> annotation : method.getAnnotations()) {
                        String annotationName = annotation.getAnnotationType().getSimpleName();
                        component.addAnnotation(annotationName);
                    }
                }
            }
        }
    }

    /**
     * Calculate code quality metrics (CBO and LCOM) for a type.
     */
    private void calculateMetrics(CtType<?> type, Component component) {
        try {
            // Calculate CBO (Coupling Between Objects)
            int cbo = MetricsCalculator.calculateCBO(type);
            component.setCbo(cbo);

            // Calculate LCOM (Lack of Cohesion in Methods)
            Double lcom = MetricsCalculator.calculateLCOM(type);
            component.setLcom(lcom);

            logger.debug("Metrics for {}: CBO={}, LCOM={}",
                    type.getQualifiedName(), cbo, lcom != null ? String.format("%.2f", lcom) : "N/A");
        } catch (Exception e) {
            logger.warn("Failed to calculate metrics for {}: {}", type.getQualifiedName(), e.getMessage());
        }
    }
}