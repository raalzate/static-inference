package com.extractor.inference;

import com.extractor.model.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Classifies components into architectural layers: Controller, Business, Data, or Shared.
 * Works with any project structure by analyzing annotations, naming patterns, package structure, and relationships.
 */
public class LayerClassifier {
    
    private static final Logger logger = LoggerFactory.getLogger(LayerClassifier.class);
    
    public enum Layer {
        CONTROLLER("Controlador"),
        BUSINESS("Negocio"),
        PERSISTENCE("Persistencia"),   // Database access: @Repository, @Entity, @Dao, uses DB tables
        DOMAIN("Dominio"),              // Business domain objects: VO, ValueObject, domain models
        TRANSFER("Transferencia"),      // API transfer objects: DTO, Request, Response
        WEB("Web"),
        SHARED("Compartida"),
        DATA("Datos");                  // Deprecated: kept for backward compatibility, maps to PERSISTENCE
        
        private final String displayName;
        
        Layer(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    private static final List<String> CONTROLLER_ANNOTATIONS = Arrays.asList(
        "RestController", "Controller", "Resource", "Path", "WebServlet",
        "GET", "POST", "PUT", "DELETE", "PATCH",
        // .NET ASP.NET Core
        "ApiController", "HttpGet", "HttpPost", "HttpPut", "HttpDelete", "HttpPatch", "Route"
    );

    private static final List<String> BUSINESS_ANNOTATIONS = Arrays.asList(
        "Service", "Component", "Stateless", "Stateful", "MessageDriven", "Singleton",
        "Facade", "ApplicationScoped", "SessionScoped", "RequestScoped",
        // .NET
        "FromServices"
    );

    // Persistence layer: actual database access and ORM
    private static final List<String> PERSISTENCE_ANNOTATIONS = Arrays.asList(
        "Repository", "Entity", "Table", "Dao", "Embeddable", "MappedSuperclass",
        "NamedQuery", "NamedQueries", "Column", "JoinColumn", "OneToMany", "ManyToOne",
        "ManyToMany", "OneToOne",
        // .NET Entity Framework
        "Key", "ForeignKey", "DatabaseGenerated", "NotMapped", "Required", "MaxLength",
        "StringLength", "Index"
    );

    // Domain layer: business domain objects (no direct DB access)
    private static final List<String> DOMAIN_ANNOTATIONS = Arrays.asList(
        "ValueObject", "DomainModel", "Immutable"
    );

    // Transfer layer: DTOs for API communication
    private static final List<String> TRANSFER_ANNOTATIONS = Arrays.asList(
        "JsonSerialize", "JsonDeserialize", "XmlRootElement", "XmlElement",
        "ApiModel", "Schema",
        // .NET System.Text.Json / Newtonsoft
        "JsonPropertyName", "JsonIgnore", "JsonConverter"
    );
    
    private static final List<String> CONTROLLER_NAME_PATTERNS = Arrays.asList(
        "Controller", "Endpoint", "Resource", "API", "Rest", "Servlet"
    );
    
    // Patterns that should NOT be classified as controllers
    private static final List<String> NON_CONTROLLER_PATTERNS = Arrays.asList(
        "Consumer", "Client", "RestClient", "HttpClient", "FeignClient", "WebClient"
    );
    
    private static final List<String> BUSINESS_NAME_PATTERNS = Arrays.asList(
        "Service", "Business", "Manager", "Facade", "UseCase", "Handler", "Processor", 
        "Bean", "Mdb", "Ejb"
    );
    
    // Persistence layer patterns
    private static final List<String> PERSISTENCE_NAME_PATTERNS = Arrays.asList(
        "Repository", "Dao", "DAO", "Entity", "Mapper", "Persistence", "DataAccess", "Provider"
    );
    
    // Domain layer patterns  
    private static final List<String> DOMAIN_NAME_PATTERNS = Arrays.asList(
        "VO", "Vo", "ValueObject", "DomainModel", "DomainObject", "Model", "Domain"
    );
    
    // Transfer layer patterns
    private static final List<String> TRANSFER_NAME_PATTERNS = Arrays.asList(
        "DTO", "Dto", "Request", "Response", "Payload", "Message", "Command", "Query", "Event"
    );
    
    private static final List<String> SHARED_NAME_PATTERNS = Arrays.asList(
        "Config", "Configuration", "Util", "Utils", "Helper", "Constants", "Exception",
        "Security", "Filter", "Interceptor", "Aspect", "Validator", "Consumer", "Client", "Factory"
    );
    
    private static final List<String> CONTROLLER_PACKAGE_PATTERNS = Arrays.asList(
        ".controller.", ".rest.", ".endpoint.", ".web.", ".servlet.",
        ".resource.", ".services."
    );
    
    private static final List<String> BUSINESS_PACKAGE_PATTERNS = Arrays.asList(
        ".service.", ".business.", ".usecase.", ".facade.", ".application.", ".handler.",
        ".bean.", ".ejb.", ".mdb.", ".api."
    );
    
    // Persistence layer packages
    private static final List<String> PERSISTENCE_PACKAGE_PATTERNS = Arrays.asList(
        ".repository.", ".dao.", ".persistence.", ".mapper.", ".entity.", ".entities.",
        ".domain.entity.", ".jpa."
    );
    
    // Domain layer packages
    private static final List<String> DOMAIN_PACKAGE_PATTERNS = Arrays.asList(
        ".domain.", ".vo.", ".valueobject.", ".model.", ".core."
    );
    
    // Transfer layer packages  
    private static final List<String> TRANSFER_PACKAGE_PATTERNS = Arrays.asList(
        ".dto.", ".request.", ".response.", ".payload.", ".api.model.", ".contract.",
        ".message.", ".command.", ".query.", ".event."
    );
    
    private static final List<String> SHARED_PACKAGE_PATTERNS = Arrays.asList(
        ".config.", ".util.", ".utils.", ".common.", ".shared.", ".security.",
        ".exception.", ".filter.", ".interceptor.", ".aspect.", ".validation.",
        ".provider."
    );
    
    /**
     * Classify a component into its architectural layer with refined data layer classification.
     */
    public Layer classifyComponent(Component component) {
        String componentId = component.getId().toLowerCase();
        String simpleClassName = extractSimpleClassName(component.getId());
        
        int controllerScore = 0;
        int businessScore = 0;
        int persistenceScore = 0;   // Database access layer
        int domainScore = 0;         // Business domain objects
        int transferScore = 0;       // API transfer objects
        int webScore = 0;
        int sharedScore = 0;
        
        // Web layer gets priority if web_role is set
        if (component.getWebRole() != null && !component.getWebRole().isEmpty()) {
            webScore += 20; // Strong signal for web layer
        }
        
        // Score by annotations
        controllerScore += scoreByAnnotations(component, CONTROLLER_ANNOTATIONS) * 10;
        businessScore += scoreByAnnotations(component, BUSINESS_ANNOTATIONS) * 10;
        persistenceScore += scoreByAnnotations(component, PERSISTENCE_ANNOTATIONS) * 10;
        domainScore += scoreByAnnotations(component, DOMAIN_ANNOTATIONS) * 10;
        transferScore += scoreByAnnotations(component, TRANSFER_ANNOTATIONS) * 10;
        
        // Score by name patterns
        controllerScore += scoreByNamePatterns(simpleClassName, CONTROLLER_NAME_PATTERNS) * 5;
        businessScore += scoreByNamePatterns(simpleClassName, BUSINESS_NAME_PATTERNS) * 5;
        persistenceScore += scoreByNamePatterns(simpleClassName, PERSISTENCE_NAME_PATTERNS) * 5;
        domainScore += scoreByNamePatterns(simpleClassName, DOMAIN_NAME_PATTERNS) * 5;
        transferScore += scoreByNamePatterns(simpleClassName, TRANSFER_NAME_PATTERNS) * 5;
        sharedScore += scoreByNamePatterns(simpleClassName, SHARED_NAME_PATTERNS) * 5;
        
        // Score by package patterns
        controllerScore += scoreByPackagePatterns(componentId, CONTROLLER_PACKAGE_PATTERNS, ".services.") * 3;
        businessScore += scoreByPackagePatterns(componentId, BUSINESS_PACKAGE_PATTERNS) * 3;
        persistenceScore += scoreByPackagePatterns(componentId, PERSISTENCE_PACKAGE_PATTERNS) * 3;
        domainScore += scoreByPackagePatterns(componentId, DOMAIN_PACKAGE_PATTERNS) * 3;
        transferScore += scoreByPackagePatterns(componentId, TRANSFER_PACKAGE_PATTERNS) * 3;
        sharedScore += scoreByPackagePatterns(componentId, SHARED_PACKAGE_PATTERNS) * 3;
        
        // DISAMBIGUATION RULES
        
        // Rule 0: Explicit exclusions for consumers/clients (NOT controllers)
        for (String pattern : NON_CONTROLLER_PATTERNS) {
            if (simpleClassName.contains(pattern)) {
                controllerScore = 0; // Never classify consumers/clients as controllers
                sharedScore += 8; // Boost shared/business layer score
                break;
            }
        }
        
        // Rule 1: Database access = PERSISTENCE (highest priority for data layers)
        boolean usesDatabase = !component.getTablesUsed().isEmpty();
        if (usesDatabase) {
            persistenceScore += 15; // Strong boost for persistence
            domainScore = Math.max(0, domainScore - 10); // Domain objects shouldn't access DB directly
        }
        
        // Rule 1.5: Provider with database access = PERSISTENCE (e.g., AfiMaeAfiliadoProvider with JDBC)
        if (simpleClassName.toLowerCase().contains("provider") && usesDatabase) {
            persistenceScore += 20; // Very strong signal for persistence layer
            sharedScore = Math.max(0, sharedScore - 10); // Reduce shared score
            businessScore = Math.max(0, businessScore - 5); // Reduce business score
        }
        
        // Rule 2: @Entity annotation = PERSISTENCE (not domain)
        if (hasAnnotation(component, "Entity") || hasAnnotation(component, "Table")) {
            persistenceScore += 10;
            domainScore = 0; // @Entity is persistence, not domain
        }
        
        // Rule 3: Repository/Dao interface = PERSISTENCE
        if (component.isInterface() && 
            (simpleClassName.toLowerCase().contains("repository") || 
             simpleClassName.toLowerCase().contains("dao"))) {
            persistenceScore += 10;
            businessScore = Math.max(0, businessScore - 5);
        }
        
        // Rule 4: DTO/Request/Response near controllers = TRANSFER
        if ((simpleClassName.toLowerCase().matches(".*(dto|request|response|payload).*")) &&
            (componentId.contains(".controller.") || componentId.contains(".rest.") || 
             componentId.contains(".api."))) {
            transferScore += 8;
            domainScore = Math.max(0, domainScore - 5); // Reduce domain score for API objects
        }
        
        // Rule 5: Model/Domain without DB access = DOMAIN
        if ((simpleClassName.toLowerCase().contains("model") || 
             simpleClassName.toLowerCase().contains("domain") ||
             componentId.contains(".domain.")) && !usesDatabase) {
            domainScore += 5;
        }
        
        // Rule 6: .services. and .api. packages are ambiguous
        if (componentId.contains(".services.") && !hasRestAnnotations(component)) {
            controllerScore -= 3;
            businessScore += 3;
        }
        if (componentId.contains(".api.") && !hasRestAnnotations(component)) {
            businessScore += 3;
        }
        
        // Rule 7: Interfaces without REST annotations default to Business
        if (component.isInterface() && !hasRestAnnotations(component)) {
            boolean isPersistenceInterface = simpleClassName.toLowerCase().matches(".*(repository|dao|mapper).*") ||
                                             componentId.contains(".repository.") || componentId.contains(".dao.");
            if (!isPersistenceInterface) {
                businessScore += 5;
                controllerScore = Math.max(0, controllerScore - 5);
            }
        }
        
        // Find maximum score
        int maxScore = Math.max(Math.max(Math.max(Math.max(controllerScore, businessScore), 
                                Math.max(persistenceScore, domainScore)), 
                                Math.max(transferScore, webScore)), sharedScore);
        
        if (maxScore == 0) {
            logger.debug("Component {} has no clear layer signals, classifying as SHARED", component.getId());
            return Layer.SHARED;
        }
        
        // Return layer with highest score
        if (webScore == maxScore && webScore > 0) {
            logger.debug("Component {} classified as WEB (score: {})", component.getId(), webScore);
            return Layer.WEB;
        } else if (controllerScore == maxScore) {
            logger.debug("Component {} classified as CONTROLLER (score: {})", component.getId(), controllerScore);
            return Layer.CONTROLLER;
        } else if (businessScore == maxScore) {
            logger.debug("Component {} classified as BUSINESS (score: {})", component.getId(), businessScore);
            return Layer.BUSINESS;
        } else if (persistenceScore == maxScore) {
            logger.debug("Component {} classified as PERSISTENCE (score: {}, DB: {})", 
                component.getId(), persistenceScore, usesDatabase);
            return Layer.PERSISTENCE;
        } else if (domainScore == maxScore) {
            logger.debug("Component {} classified as DOMAIN (score: {}, DB: {})", 
                component.getId(), domainScore, usesDatabase);
            return Layer.DOMAIN;
        } else if (transferScore == maxScore) {
            logger.debug("Component {} classified as TRANSFER (score: {})", component.getId(), transferScore);
            return Layer.TRANSFER;
        } else {
            logger.debug("Component {} classified as SHARED (score: {})", component.getId(), sharedScore);
            return Layer.SHARED;
        }
    }
    
    /**
     * Check if component has a specific annotation.
     */
    private boolean hasAnnotation(Component component, String annotation) {
        List<String> annotations = component.getAnnotations();
        if (annotations == null || annotations.isEmpty()) {
            return false;
        }
        for (String ann : annotations) {
            if (ann.equalsIgnoreCase(annotation)) {
                return true;
            }
        }
        return false;
    }
    
    private int scoreByAnnotations(Component component, List<String> annotations) {
        int score = 0;
        List<String> componentAnnotations = component.getAnnotations();
        if (componentAnnotations == null || componentAnnotations.isEmpty()) {
            return 0;
        }
        
        for (String targetAnnotation : annotations) {
            for (String componentAnnotation : componentAnnotations) {
                if (componentAnnotation.equalsIgnoreCase(targetAnnotation)) {
                    score++;
                    break;
                }
            }
        }
        return score;
    }
    
    private int scoreByNamePatterns(String className, List<String> patterns) {
        int score = 0;
        for (String pattern : patterns) {
            if (className.toLowerCase().contains(pattern.toLowerCase())) {
                score++;
            }
        }
        return score;
    }
    
    private int scoreByPackagePatterns(String fullClassName, List<String> patterns) {
        return scoreByPackagePatterns(fullClassName, patterns, null);
    }
    
    private int scoreByPackagePatterns(String fullClassName, List<String> patterns, String patternToExclude) {
        int score = 0;
        for (String pattern : patterns) {
            if (patternToExclude != null && pattern.equals(patternToExclude)) {
                continue; // Skip this pattern, will be handled specially
            }
            if (fullClassName.contains(pattern)) {
                score++;
            }
        }
        return score;
    }
    
    private boolean hasRestAnnotations(Component component) {
        List<String> annotations = component.getAnnotations();
        if (annotations == null || annotations.isEmpty()) {
            return false;
        }
        
        // JAX-RS and Spring REST annotations
        List<String> restAnnotations = Arrays.asList(
            "Path", "GET", "POST", "PUT", "DELETE", "PATCH",
            "RestController", "Controller", "WebServlet",
            "RequestMapping", "GetMapping", "PostMapping", "PutMapping", 
            "DeleteMapping", "PatchMapping"
        );
        
        for (String restAnnotation : restAnnotations) {
            for (String componentAnnotation : annotations) {
                if (componentAnnotation.equalsIgnoreCase(restAnnotation)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private String extractSimpleClassName(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < fullyQualifiedName.length() - 1) {
            return fullyQualifiedName.substring(lastDot + 1);
        }
        return fullyQualifiedName;
    }
}
