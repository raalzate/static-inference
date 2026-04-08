package com.extractor.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects references to secrets, environment variables, and configuration properties.
 * IMPORTANT: This detector only identifies WHERE secrets are used, NOT their actual values.
 * It's safe for security - never exposes sensitive data.
 */
public class SecretsDetector {
    
    private static final Logger logger = LoggerFactory.getLogger(SecretsDetector.class);
    
    // Common secret-related environment variable names (patterns only, no values)
    private static final Set<String> SECRET_ENV_PATTERNS = Set.of(
        "PASSWORD", "SECRET", "TOKEN", "KEY", "API_KEY", "PRIVATE_KEY",
        "CREDENTIAL", "AUTH", "JWT", "OAUTH", "DATABASE_URL", "DB_PASSWORD"
    );
    
    /**
     * Detect secret/property references in a component.
     * Returns a list of detected patterns (e.g., "System.getenv()", "@Value annotation")
     */
    public List<String> detectSecretReferences(CtType<?> type) {
        List<String> references = new ArrayList<>();
        
        // 1. Detect System.getenv() calls
        if (hasSystemGetenv(type)) {
            references.add("System.getenv()");
        }
        
        // 2. Detect System.getProperty() calls
        if (hasSystemGetProperty(type)) {
            references.add("System.getProperty()");
        }
        
        // 3. Detect @Value annotations (Spring)
        if (hasValueAnnotation(type)) {
            references.add("@Value");
        }
        
        // 4. Detect @ConfigProperty annotations (Quarkus/MicroProfile)
        if (hasConfigPropertyAnnotation(type)) {
            references.add("@ConfigProperty");
        }
        
        // 5. Detect properties file references
        if (hasPropertiesFileAccess(type)) {
            references.add("Properties file access");
        }
        
        // 6. Detect JNDI lookups (often used for datasources)
        if (hasJNDILookup(type)) {
            references.add("JNDI lookup");
        }
        
        // 7. Detect @Resource annotations (JavaEE)
        if (hasResourceAnnotation(type)) {
            references.add("@Resource");
        }
        
        return references;
    }
    
    /**
     * Detect System.getenv() invocations.
     */
    private boolean hasSystemGetenv(CtType<?> type) {
        List<CtInvocation<?>> invocations = type.getElements(new TypeFilter<>(CtInvocation.class));
        
        for (CtInvocation<?> invocation : invocations) {
            String invocationStr = invocation.toString();
            if (invocationStr.contains("System.getenv(")) {
                logger.debug("Detected System.getenv() in {}", type.getQualifiedName());
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Detect System.getProperty() invocations.
     */
    private boolean hasSystemGetProperty(CtType<?> type) {
        List<CtInvocation<?>> invocations = type.getElements(new TypeFilter<>(CtInvocation.class));
        
        for (CtInvocation<?> invocation : invocations) {
            String invocationStr = invocation.toString();
            if (invocationStr.contains("System.getProperty(")) {
                logger.debug("Detected System.getProperty() in {}", type.getQualifiedName());
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Detect @Value annotations (Spring).
     */
    private boolean hasValueAnnotation(CtType<?> type) {
        // Check class-level annotations
        for (CtAnnotation<?> annotation : type.getAnnotations()) {
            if (annotation.getAnnotationType().getSimpleName().equals("Value")) {
                return true;
            }
        }
        
        // Check field-level annotations
        for (CtField<?> field : type.getFields()) {
            for (CtAnnotation<?> annotation : field.getAnnotations()) {
                if (annotation.getAnnotationType().getSimpleName().equals("Value")) {
                    logger.debug("Detected @Value in {}", type.getQualifiedName());
                    return true;
                }
            }
        }
        
        // Check method-level annotations
        for (CtMethod<?> method : type.getAllMethods()) {
            for (CtAnnotation<?> annotation : method.getAnnotations()) {
                if (annotation.getAnnotationType().getSimpleName().equals("Value")) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Detect @ConfigProperty annotations (Quarkus/MicroProfile).
     */
    private boolean hasConfigPropertyAnnotation(CtType<?> type) {
        for (CtField<?> field : type.getFields()) {
            for (CtAnnotation<?> annotation : field.getAnnotations()) {
                String annotationName = annotation.getAnnotationType().getSimpleName();
                if (annotationName.equals("ConfigProperty") || annotationName.equals("Config")) {
                    logger.debug("Detected @ConfigProperty in {}", type.getQualifiedName());
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Detect properties file access (ResourceBundle, Properties class).
     */
    private boolean hasPropertiesFileAccess(CtType<?> type) {
        List<CtInvocation<?>> invocations = type.getElements(new TypeFilter<>(CtInvocation.class));
        
        for (CtInvocation<?> invocation : invocations) {
            String invocationStr = invocation.toString();
            if (invocationStr.contains("ResourceBundle.getBundle") ||
                invocationStr.contains(".properties") ||
                invocationStr.contains("Properties.load")) {
                logger.debug("Detected properties file access in {}", type.getQualifiedName());
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Detect JNDI lookups.
     */
    private boolean hasJNDILookup(CtType<?> type) {
        List<CtInvocation<?>> invocations = type.getElements(new TypeFilter<>(CtInvocation.class));
        
        for (CtInvocation<?> invocation : invocations) {
            String invocationStr = invocation.toString();
            if (invocationStr.contains("InitialContext") ||
                invocationStr.contains(".lookup(") && invocationStr.contains("java:")) {
                logger.debug("Detected JNDI lookup in {}", type.getQualifiedName());
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Detect @Resource annotations (JavaEE).
     */
    private boolean hasResourceAnnotation(CtType<?> type) {
        for (CtField<?> field : type.getFields()) {
            for (CtAnnotation<?> annotation : field.getAnnotations()) {
                if (annotation.getAnnotationType().getSimpleName().equals("Resource")) {
                    logger.debug("Detected @Resource in {}", type.getQualifiedName());
                    return true;
                }
            }
        }
        
        return false;
    }
}
