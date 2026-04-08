package com.extractor.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Detects sensitive data patterns in Java code.
 */
public class SensitiveDataDetector {
    
    private static final Logger logger = LoggerFactory.getLogger(SensitiveDataDetector.class);
    
    // Sensitive data keywords
    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
        "password", "passwd", "pwd", "secret", "key", "token", "auth",
        "ssn", "socialsecurity", "social_security", "social-security",
        "creditcard", "credit_card", "credit-card", "cardnumber", "card_number",
        "bankaccount", "bank_account", "bank-account", "routing", "swift",
        "pin", "cvv", "cvc", "expiry", "expiration",
        "api_key", "apikey", "api-key", "private_key", "privatekey",
        "certificate", "cert", "pem", "p12", "keystore",
        "oauth", "bearer", "jwt", "session", "cookie",
        "email", "phone", "mobile", "address", "zip", "postal",
        "license", "passport", "driver", "identity", "personal",
        "salary", "income", "revenue", "financial", "payment"
    );
    
    // Sensitive annotation patterns
    private static final Set<String> SENSITIVE_ANNOTATIONS = Set.of(
        "Sensitive", "Secret", "Confidential", "Private", "Encrypted",
        "Password", "PersonalData", "PII"
    );
    
    /**
     * Check if a type contains sensitive data.
     */
    public boolean hasSensitiveData(CtType<?> type) {
        String typeName = type.getQualifiedName();
        
        // Check class name
        if (containsSensitiveKeywords(type.getSimpleName())) {
            logger.debug("Sensitive data detected in class name: {}", typeName);
            return true;
        }
        
        // Check field names and types
        for (CtField<?> field : type.getFields()) {
            if (isSensitiveField(field)) {
                logger.debug("Sensitive data detected in field '{}' of class: {}", field.getSimpleName(), typeName);
                return true;
            }
        }
        
        // Check method names
        for (CtMethod<?> method : type.getMethods()) {
            if (isSensitiveMethod(method)) {
                logger.debug("Sensitive data detected in method '{}' of class: {}", method.getSimpleName(), typeName);
                return true;
            }
        }
        
        // Check for sensitive annotations
        if (hasSensitiveAnnotations(type)) {
            logger.debug("Sensitive data detected in annotations of class: {}", typeName);
            return true;
        }
        
        // Check source code for sensitive literals
        if (containsSensitiveLiterals(type.toString())) {
            logger.debug("Sensitive data detected in source literals of class: {}", typeName);
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if a field is sensitive based on its name, type, or annotations.
     */
    private boolean isSensitiveField(CtField<?> field) {
        // Check field name
        if (containsSensitiveKeywords(field.getSimpleName())) {
            return true;
        }
        
        // Check field type
        String typeName = field.getType().getSimpleName();
        if (containsSensitiveKeywords(typeName)) {
            return true;
        }
        
        // Check annotations
        return field.getAnnotations().stream()
            .anyMatch(annotation -> SENSITIVE_ANNOTATIONS.contains(annotation.getAnnotationType().getSimpleName()));
    }
    
    /**
     * Check if a method is sensitive based on its name or annotations.
     */
    private boolean isSensitiveMethod(CtMethod<?> method) {
        // Check method name
        if (containsSensitiveKeywords(method.getSimpleName())) {
            return true;
        }
        
        // Check return type
        if (method.getType() != null) {
            String returnTypeName = method.getType().getSimpleName();
            if (containsSensitiveKeywords(returnTypeName)) {
                return true;
            }
        }
        
        // Check annotations
        return method.getAnnotations().stream()
            .anyMatch(annotation -> SENSITIVE_ANNOTATIONS.contains(annotation.getAnnotationType().getSimpleName()));
    }
    
    /**
     * Check if a type has sensitive annotations.
     */
    private boolean hasSensitiveAnnotations(CtType<?> type) {
        return type.getAnnotations().stream()
            .anyMatch(annotation -> SENSITIVE_ANNOTATIONS.contains(annotation.getAnnotationType().getSimpleName()));
    }
    
    /**
     * Check if source code contains sensitive string literals.
     */
    private boolean containsSensitiveLiterals(String sourceCode) {
        // Look for actual string literals containing sensitive keywords
        Pattern stringLiteralPattern = Pattern.compile("[\"']([^\"']*)[\"']");
        Matcher matcher = stringLiteralPattern.matcher(sourceCode);
        
        while (matcher.find()) {
            String literal = matcher.group(1).toLowerCase();
            
            // Check if the literal contains sensitive keywords
            for (String keyword : SENSITIVE_KEYWORDS) {
                if (literal.contains(keyword)) {
                    // Additional check to avoid false positives from common words
                    if (isLikelySensitiveLiteral(literal, keyword)) {
                        return true;
                    }
                }
            }
            
            // Check for potential hardcoded secrets
            if (containsHardcodedSecrets(matcher.group(0))) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if a literal is likely to be sensitive based on context.
     */
    private boolean isLikelySensitiveLiteral(String literal, String keyword) {
        // Avoid false positives from documentation, comments, or common phrases
        if (literal.length() < 3) return false;
        
        // Skip obvious non-sensitive contexts
        String[] nonSensitiveContexts = {
            "password field", "enter password", "password required", "password must",
            "email address", "phone number", "api documentation", "example api",
            "test data", "mock data", "dummy data", "placeholder"
        };
        
        for (String context : nonSensitiveContexts) {
            if (literal.contains(context)) {
                return false;
            }
        }
        
        // Consider it sensitive if it's a key-value pair or looks like actual sensitive data
        return literal.contains("=") || literal.contains(":") || 
               literal.matches(".*" + keyword + "[_-]?\\w*.*");
    }
    
    /**
     * Check if a string contains sensitive keywords.
     */
    private boolean containsSensitiveKeywords(String text) {
        if (text == null) return false;
        
        String lowerText = text.toLowerCase();
        
        return SENSITIVE_KEYWORDS.stream()
            .anyMatch(lowerText::contains);
    }
    
    /**
     * Check for hardcoded secrets patterns.
     */
    private boolean containsHardcodedSecrets(String line) {
        String lowerLine = line.toLowerCase();
        
        // Skip REST endpoint paths (common false positives)
        if (lowerLine.matches(".*[\"']/[a-zA-Z0-9/_\\-{}]*[\"'].*")) { // Paths like "/api/users/{id}"
            return false;
        }
        
        // Check for common secret patterns
        if (lowerLine.matches(".*[\"'][a-f0-9]{32,}[\"'].*")) { // Hex strings (API keys, hashes)
            return true;
        }
        
        if (lowerLine.matches(".*[\"'][A-Za-z0-9+/]{20,}={0,2}[\"'].*")) { // Base64 strings
            return true;
        }
        
        if (lowerLine.matches(".*[\"']sk_[a-zA-Z0-9_]{20,}[\"'].*")) { // Stripe secret keys
            return true;
        }
        
        if (lowerLine.matches(".*[\"']pk_[a-zA-Z0-9_]{20,}[\"'].*")) { // Stripe public keys  
            return true;
        }
        
        if (lowerLine.matches(".*[\"']AIza[a-zA-Z0-9_-]{35}[\"'].*")) { // Google API keys
            return true;
        }
        
        if (lowerLine.matches(".*[\"']ya29\\.[a-zA-Z0-9_-]{68}[\"'].*")) { // Google OAuth tokens
            return true;
        }
        
        if (lowerLine.matches(".*[\"'][0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}[\"'].*")) { // UUIDs (might be secret)
            return true;
        }
        
        return false;
    }
}