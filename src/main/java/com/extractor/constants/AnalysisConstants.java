package com.extractor.constants;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Centralized constants for dependency analysis, following Sofka's organization approach.
 */
public final class AnalysisConstants {
    
    private AnalysisConstants() {
        // Utility class
    }
    
    // --- Sensitive Data Detection ---
    public static final Set<String> SENSITIVE_KEYWORDS = Set.of(
        "password", "ssn", "creditcard", "apikey", "secretkey", "token", "auth"
    );
    
    // --- Database Detection ---
    public static final Pattern SQL_TABLE_PATTERN = Pattern.compile(
        "\\b(?:FROM|JOIN|INTO|UPDATE)\\s+([a-zA-Z0-9_]+)", Pattern.CASE_INSENSITIVE
    );
    
    public static final Set<String> SPRING_REPO_INTERFACES = Set.of(
        "org.springframework.data.jpa.repository.JpaRepository",
        "org.springframework.data.repository.CrudRepository", 
        "org.springframework.data.repository.PagingAndSortingRepository",
        "org.springframework.data.repository.Repository"
    );
    
    public static final Set<String> JPA_QUERY_ANNOTATIONS = Set.of(
        "javax.persistence.Query",
        "org.springframework.data.jpa.repository.Query",
        "jakarta.persistence.Query"
    );
    
    public static final Set<String> JPA_ENTITY_ANNOTATIONS = Set.of(
        "javax.persistence.Entity",
        "javax.persistence.Table", 
        "jakarta.persistence.Entity",
        "jakarta.persistence.Table"
    );
    
    public static final Set<String> JDBC_METHODS = Set.of(
        "prepareStatement", "executeQuery", "executeUpdate", "execute"
    );
    
    public static final Set<String> JPA_EM_METHODS = Set.of(
        "createQuery", "createNativeQuery", "find", "persist", "merge", "remove"
    );
    
    // --- Dependency Injection Detection ---
    public static final Set<String> SPRING_INJECTION_ANNOTATIONS = Set.of(
        "org.springframework.beans.factory.annotation.Autowired",
        "javax.inject.Inject",
        "jakarta.inject.Inject", 
        "javax.annotation.Resource",
        "jakarta.annotation.Resource"
    );
    
    public static final Set<String> JPA_RELATION_ANNOTATIONS = Set.of(
        "javax.persistence.OneToOne", 
        "javax.persistence.OneToMany",
        "javax.persistence.ManyToOne", 
        "javax.persistence.ManyToMany",
        "javax.persistence.ElementCollection",
        "jakarta.persistence.OneToOne",
        "jakarta.persistence.OneToMany", 
        "jakarta.persistence.ManyToOne",
        "jakarta.persistence.ManyToMany",
        "jakarta.persistence.ElementCollection"
    );
    
    // --- Dependency Weights ---
    public static final int CALL_DEPENDENCY_WEIGHT = 1;
    public static final int STRUCTURAL_DEPENDENCY_WEIGHT = 5;
    public static final int REPOSITORY_DEPENDENCY_WEIGHT = 7;
    public static final int INJECTION_DEPENDENCY_WEIGHT = 6;
    
    // --- Analysis Types ---
    public static final String USES_TYPE = "use";
    public static final String CALL_TYPE = "call";
    public static final String INTERFACE_IMPL_TYPE = "interface-impl";
    public static final String REFLECTION_TYPE = "reflection";
    public static final String REPOSITORY_TYPE = "repository";
    public static final String DB_TYPE = "db";
    public static final String INJECTION_FIELD_TYPE = "injection-field";
    public static final String INJECTION_CONSTRUCTOR_TYPE = "injection-constructor";
    public static final String RELATION_TYPE = "relation";
    public static final String EXTERNAL_TYPE = "external";
}