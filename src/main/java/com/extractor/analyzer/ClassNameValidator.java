package com.extractor.analyzer;

import java.util.Set;

public class ClassNameValidator {
    
    private final ComponentRegistry componentRegistry;
    
    public ClassNameValidator(ComponentRegistry componentRegistry) {
        this.componentRegistry = componentRegistry;
    }
    
    public boolean isValidTargetClass(String toClass, String fromClass) {
        if (toClass == null || toClass.isEmpty()) return false;
        
        if (toClass.equals(fromClass)) return false;
        
        if (isInvalidClassName(toClass)) return false;
        
        if (!toClass.contains(".")) {
            return isValidSimpleClassName(toClass);
        }
        
        if (toClass.startsWith("com.extractor.") && !componentRegistry.hasComponent(toClass)) {
            String simpleName = toClass.substring(toClass.lastIndexOf(".") + 1);
            if (simpleName.startsWith("Ct") || isInvalidClassName(simpleName)) {
                return false;
            }
        }
        
        return true;
    }
    
    public boolean isInvalidClassName(String className) {
        if (className == null) return true;
        
        Set<String> invalidNames = Set.of(
            "annotation", "iface", "type", "element", "node", "value",
            "SPRING_DATA_REPOSITORIES", "JPA_METHODS", "JDBC_METHODS",
            "<nulltype>", "nulltype", "<unknown>", "unknown"
        );
        
        if (invalidNames.contains(className)) return true;
        
        if (className.matches("^[A-Z][A-Z0-9_]*$") && className.contains("_")) return true;
        
        if (className.matches("^<.*>$")) return true;
        
        return false;
    }
    
    public boolean isValidSimpleClassName(String className) {
        if (className == null || className.isEmpty()) return false;
        
        if (!className.matches("^[A-Z][a-zA-Z0-9]*$")) return false;
        
        Set<String> validSimpleNames = Set.of(
            "String", "Object", "Integer", "Boolean", "Long", "Double", 
            "List", "Set", "Map", "Collection", "Optional"
        );
        
        return validSimpleNames.contains(className);
    }
}
