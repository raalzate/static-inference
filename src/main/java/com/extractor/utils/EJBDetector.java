package com.extractor.utils;

import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;
import java.util.stream.Collectors;

public class EJBDetector {
    
    private static final Set<String> EJB_SESSION_ANNOTATIONS = Set.of(
        "javax.ejb.Stateless",
        "javax.ejb.Stateful", 
        "javax.ejb.Singleton",
        "jakarta.ejb.Stateless",
        "jakarta.ejb.Stateful",
        "jakarta.ejb.Singleton"
    );
    
    private static final Set<String> EJB_MESSAGE_ANNOTATIONS = Set.of(
        "javax.ejb.MessageDriven",
        "jakarta.ejb.MessageDriven"
    );
    
    private static final Set<String> EJB_INJECTION_ANNOTATIONS = Set.of(
        "javax.ejb.EJB",
        "jakarta.ejb.EJB"
    );
    
    public static class EJBInfo {
        private final String type;
        private final String name;
        private final List<String> dependencies;
        private final boolean usesJNDI;
        
        public EJBInfo(String type, String name, List<String> dependencies, boolean usesJNDI) {
            this.type = type;
            this.name = name;
            this.dependencies = dependencies;
            this.usesJNDI = usesJNDI;
        }
        
        public String getType() { return type; }
        public String getName() { return name; }
        public List<String> getDependencies() { return dependencies; }
        public boolean usesJNDI() { return usesJNDI; }
    }
    
    public static EJBInfo detectEJB(CtType<?> type) {
        String ejbType = getEJBType(type);
        if (ejbType == null) {
            return null;
        }
        
        String name = type.getQualifiedName();
        List<String> dependencies = detectEJBDependencies(type);
        boolean usesJNDI = detectJNDIUsage(type);
        
        return new EJBInfo(ejbType, name, dependencies, usesJNDI);
    }
    
    private static String getEJBType(CtType<?> type) {
        List<CtAnnotation<? extends java.lang.annotation.Annotation>> annotations = type.getAnnotations();
        
        for (CtAnnotation<?> annotation : annotations) {
            String annotationType = annotation.getAnnotationType().getQualifiedName();
            
            if (EJB_SESSION_ANNOTATIONS.contains(annotationType)) {
                if (annotationType.contains("Stateless")) return "Stateless";
                if (annotationType.contains("Stateful")) return "Stateful";
                if (annotationType.contains("Singleton")) return "Singleton";
            }
            
            if (EJB_MESSAGE_ANNOTATIONS.contains(annotationType)) {
                return "MessageDriven";
            }
        }
        
        return null;
    }
    
    private static List<String> detectEJBDependencies(CtType<?> type) {
        List<String> dependencies = new ArrayList<>();
        
        List<CtField<?>> fields = type.getFields();
        for (CtField<?> field : fields) {
            for (CtAnnotation<?> annotation : field.getAnnotations()) {
                String annotationType = annotation.getAnnotationType().getQualifiedName();
                if (EJB_INJECTION_ANNOTATIONS.contains(annotationType)) {
                    CtTypeReference<?> fieldType = field.getType();
                    if (fieldType != null) {
                        dependencies.add(fieldType.getQualifiedName());
                    }
                }
            }
        }
        
        return dependencies;
    }
    
    private static boolean detectJNDIUsage(CtType<?> type) {
        try {
            List<CtInvocation<?>> invocations = type.getElements(new TypeFilter<>(CtInvocation.class));
            
            for (CtInvocation<?> invocation : invocations) {
                String methodName = invocation.getExecutable().getSimpleName();
                
                if (methodName.equals("lookup") || 
                    methodName.equals("InitialContext") ||
                    invocation.toString().contains("java:comp/env") ||
                    invocation.toString().contains("ejb/")) {
                    return true;
                }
            }
        } catch (Exception e) {
        }
        
        return false;
    }
    
    public static boolean isEJBComponent(CtType<?> type) {
        return getEJBType(type) != null;
    }
    
    public static String getEJBAnnotation(CtType<?> type) {
        for (CtAnnotation<?> annotation : type.getAnnotations()) {
            String annotationType = annotation.getAnnotationType().getQualifiedName();
            
            if (EJB_SESSION_ANNOTATIONS.contains(annotationType) ||
                EJB_MESSAGE_ANNOTATIONS.contains(annotationType)) {
                return annotationType;
            }
        }
        return null;
    }
}
