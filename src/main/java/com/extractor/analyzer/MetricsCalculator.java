package com.extractor.analyzer;

import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.code.*;
import spoon.support.reflect.code.*;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Calculates code quality metrics for Java classes.
 * - CBO (Coupling Between Objects): Number of classes this class is coupled to
 * - LCOM (Lack of Cohesion in Methods): Using LCOM-HS formula (Henderson-Sellers)
 */
public class MetricsCalculator {
    
    /**
     * Calculate CBO (Coupling Between Objects) for a type.
     * CBO counts the number of other classes this class is coupled to through:
     * - Method calls and constructor calls
     * - Field types
     * - Method parameter types
     * - Method return types
     * - Inheritance
     * - Interfaces implemented
     * 
     * @param type The type to analyze
     * @return The CBO metric (higher = more coupling)
     */
    public static int calculateCBO(CtType<?> type) {
        Set<String> coupledClasses = new HashSet<>();
        
        // 1. Superclass coupling
        if (type instanceof CtClass<?>) {
            CtClass<?> ctClass = (CtClass<?>) type;
            CtTypeReference<?> superClass = ctClass.getSuperclass();
            if (superClass != null && !isJdkClass(superClass.getQualifiedName())) {
                coupledClasses.add(superClass.getQualifiedName());
            }
        }
        
        // 2. Interface coupling
        Set<CtTypeReference<?>> interfaces = type.getSuperInterfaces();
        if (interfaces != null) {
            for (CtTypeReference<?> interfaceRef : interfaces) {
                if (!isJdkClass(interfaceRef.getQualifiedName())) {
                    coupledClasses.add(interfaceRef.getQualifiedName());
                }
            }
        }
        
        // 3. Field type coupling
        for (CtField<?> field : type.getFields()) {
            CtTypeReference<?> fieldType = field.getType();
            if (fieldType != null && !isJdkClass(fieldType.getQualifiedName()) 
                && !isPrimitive(fieldType)) {
                coupledClasses.add(fieldType.getQualifiedName());
            }
        }
        
        // 4. Method parameter and return type coupling
        Collection<CtMethod<?>> methods = type.getMethods();
        for (CtMethod<?> method : methods) {
            // Return type
            CtTypeReference<?> returnType = method.getType();
            if (returnType != null && !isJdkClass(returnType.getQualifiedName()) 
                && !isPrimitive(returnType)) {
                coupledClasses.add(returnType.getQualifiedName());
            }
            
            // Parameters
            for (CtParameter<?> param : method.getParameters()) {
                CtTypeReference<?> paramType = param.getType();
                if (paramType != null && !isJdkClass(paramType.getQualifiedName()) 
                    && !isPrimitive(paramType)) {
                    coupledClasses.add(paramType.getQualifiedName());
                }
            }
        }
        
        // 5. Method invocation coupling (CRITICAL: calls to other classes)
        List<CtInvocation<?>> invocations = type.getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable != null && executable.getDeclaringType() != null) {
                // Guard against null qualified name (anonymous/local classes)
                String targetClass = executable.getDeclaringType().getQualifiedName();
                if (targetClass != null && !isJdkClass(targetClass) && !targetClass.equals(type.getQualifiedName())) {
                    coupledClasses.add(targetClass);
                }
            }
        }
        
        // 6. Constructor call coupling
        List<CtConstructorCall<?>> constructorCalls = type.getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall<?> call : constructorCalls) {
            CtTypeReference<?> targetType = call.getType();
            if (targetType != null) {
                // Guard against null qualified name (anonymous/local classes)
                String targetClass = targetType.getQualifiedName();
                if (targetClass != null && !isJdkClass(targetClass) && !targetClass.equals(type.getQualifiedName())) {
                    coupledClasses.add(targetClass);
                }
            }
        }
        
        // Remove self-reference (in case of internal calls)
        coupledClasses.remove(type.getQualifiedName());
        
        return coupledClasses.size();
    }
    
    /**
     * Calculate LCOM-HS (Lack of Cohesion in Methods - Henderson-Sellers version).
     * LCOM-HS measures how related the methods of a class are based on field usage.
     * 
     * Formula: LCOM-HS = (M - sum(MF)/F) / (M - 1)
     * Where:
     * - M = number of methods
     * - F = number of fields (instance variables)
     * - MF = number of methods that access each field
     * 
     * @param type The type to analyze
     * @return LCOM-HS value (0 = high cohesion, 1 = low cohesion, null if not applicable)
     */
    public static Double calculateLCOM(CtType<?> type) {
        // Only calculate LCOM for classes (not interfaces or enums without methods)
        if (!(type instanceof CtClass<?>)) {
            return null;
        }
        
        CtClass<?> ctClass = (CtClass<?>) type;
        
        // Get all instance fields (exclude static fields)
        List<CtField<?>> instanceFields = ctClass.getFields().stream()
            .filter(f -> !f.isStatic())
            .collect(Collectors.toList());
        
        int F = instanceFields.size();
        
        // Get all instance methods (exclude static methods, constructors, getters/setters)
        List<CtMethod<?>> instanceMethods = ctClass.getMethods().stream()
            .filter(m -> !m.isStatic())
            .filter(m -> !isGetterOrSetter(m))
            .collect(Collectors.toList());
        
        int M = instanceMethods.size();
        
        // Need at least 2 methods and 1 field to calculate LCOM
        if (M < 2 || F < 1) {
            return null;
        }
        
        // For each field, count how many methods access it
        int sumMF = 0;
        for (CtField<?> field : instanceFields) {
            int methodsAccessingField = 0;
            for (CtMethod<?> method : instanceMethods) {
                if (methodAccessesField(method, field)) {
                    methodsAccessingField++;
                }
            }
            sumMF += methodsAccessingField;
        }
        
        // Calculate LCOM-HS
        double lcom = (M - (double) sumMF / F) / (M - 1);
        
        // Clamp to [0, 1] range
        return Math.max(0.0, Math.min(1.0, lcom));
    }
    
    /**
     * Check if a method accesses a field using AST analysis (not string matching).
     * Detects both field reads and writes.
     */
    private static boolean methodAccessesField(CtMethod<?> method, CtField<?> field) {
        if (method.getBody() == null) {
            return false;
        }
        
        String fieldName = field.getSimpleName();
        
        // Look for field reads (CtFieldRead)
        List<CtFieldRead<?>> fieldReads = method.getBody().getElements(new TypeFilter<>(CtFieldRead.class));
        for (CtFieldRead<?> read : fieldReads) {
            if (read.getVariable() != null && fieldName.equals(read.getVariable().getSimpleName())) {
                return true;
            }
        }
        
        // Look for field writes (CtFieldWrite)
        List<CtFieldWrite<?>> fieldWrites = method.getBody().getElements(new TypeFilter<>(CtFieldWrite.class));
        for (CtFieldWrite<?> write : fieldWrites) {
            if (write.getVariable() != null && fieldName.equals(write.getVariable().getSimpleName())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if a method is a simple getter or setter.
     * Improved heuristic: checks not just name but also body simplicity.
     */
    private static boolean isGetterOrSetter(CtMethod<?> method) {
        String name = method.getSimpleName();
        int paramCount = method.getParameters().size();
        
        // Check body exists and is simple (1-2 statements)
        if (method.getBody() == null) {
            return false;
        }
        
        int statementCount = method.getBody().getStatements().size();
        
        // Getter: starts with "get" or "is", has no parameters, simple body
        if ((name.startsWith("get") || name.startsWith("is")) && paramCount == 0 && statementCount <= 2) {
            return true;
        }
        
        // Setter: starts with "set", has exactly 1 parameter, simple body
        if (name.startsWith("set") && paramCount == 1 && statementCount <= 2) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if a class is from the JDK.
     */
    private static boolean isJdkClass(String className) {
        if (className == null) return false;
        return className.startsWith("java.") || 
               className.startsWith("javax.") ||
               className.startsWith("jdk.");
    }
    
    /**
     * Check if a type is a primitive type.
     */
    private static boolean isPrimitive(CtTypeReference<?> type) {
        if (type == null) return false;
        return type.isPrimitive() || type.getSimpleName().equals("void");
    }
}
