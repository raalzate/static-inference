package com.extractor.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects database access patterns in Java code.
 */
public class DatabaseDetector {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseDetector.class);
    
    // JDBC method patterns
    private static final Set<String> JDBC_METHODS = Set.of(
        "prepareStatement", "executeQuery", "executeUpdate", "execute",
        "createStatement", "getConnection", "prepareCall", "executeBatch", "setString", 
        "setInt", "setLong", "getResultSet", "next"
    );
    
    // JPA/Hibernate method patterns
    private static final Set<String> JPA_METHODS = Set.of(
        "createQuery", "createNamedQuery", "createNativeQuery",
        "find", "persist", "merge", "remove", "flush", "refresh"
    );
    
    // iBatis/MyBatis method patterns
    private static final Set<String> MYBATIS_METHODS = Set.of(
        "selectOne", "selectList", "selectMap", "insert", "update", "delete",
        "commit", "rollback", "openSession", "getSqlSession"
    );
    
    // JPA/Hibernate annotations
    private static final Set<String> JPA_ANNOTATIONS = Set.of(
        "Entity", "Table", "Repository", "Query", 
        "NamedQuery", "NamedQueries", "SqlResultSetMapping"
    );
    
    // Spring Data repository interfaces
    private static final Set<String> SPRING_DATA_REPOSITORIES = Set.of(
        "CrudRepository", "JpaRepository", "PagingAndSortingRepository",
        "MongoRepository", "ReactiveCrudRepository"
    );
    
    // Patterns for extracting table names from SQL and annotations
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile(
        "(?:FROM|INTO|UPDATE|JOIN)\\s+([a-zA-Z_][a-zA-Z0-9_]*)", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern ANNOTATION_TABLE_PATTERN = Pattern.compile(
        "@Table\\s*\\(\\s*name\\s*=\\s*[\"']([^\"']+)[\"']", 
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * Find tables used by a type.
     */
    public List<String> findTablesUsed(CtType<?> type) {
        Set<String> tables = new HashSet<>();
        
        // Check for JPA @Table annotations
        tables.addAll(findTablesFromAnnotations(type));
        
        // Check for SQL queries in string literals
        tables.addAll(findTablesFromSqlQueries(type));
        
        // Check for repository method names (Spring Data)
        tables.addAll(findTablesFromRepositoryMethods(type));
        
        // Infer table name from entity class name
        if (isEntityClass(type)) {
            String tableName = inferTableNameFromClassName(type.getSimpleName());
            tables.add(tableName);
        }
        
        return new ArrayList<>(tables);
    }
    
    /**
     * Check if a class name or invocation indicates a database call.
     */
    public boolean isDatabaseCall(String className, String invocationString) {
        if (className == null) return false;
        
        // Check for JDBC classes - must be exact package matches
        if (className.startsWith("java.sql.") || 
            className.startsWith("javax.sql.") ||
            className.equals("java.sql.Connection") ||
            className.equals("java.sql.Statement") ||
            className.equals("java.sql.PreparedStatement") ||
            className.equals("java.sql.CallableStatement") ||
            className.equals("java.sql.ResultSet")) {
            return true;
        }
        
        // Check for JPA/Hibernate classes - must be exact package matches
        if (className.startsWith("javax.persistence.") ||
            className.startsWith("jakarta.persistence.") ||
            className.startsWith("org.hibernate.") ||
            className.equals("javax.persistence.EntityManager") ||
            className.equals("jakarta.persistence.EntityManager") ||
            className.equals("org.hibernate.Session")) {
            return true;
        }
        
        // Check for iBatis/MyBatis classes - must be exact package matches
        if (className.startsWith("org.apache.ibatis.") ||
            className.startsWith("com.ibatis.") ||
            className.equals("org.apache.ibatis.session.SqlSession") ||
            className.equals("org.apache.ibatis.session.SqlSessionFactory") ||
            className.equals("com.ibatis.sqlmap.client.SqlMapClient")) {
            return true;
        }
        
        // Check for Spring Data repositories - must be exact interface matches
        if (className.startsWith("org.springframework.data.")) {
            for (String repoType : SPRING_DATA_REPOSITORIES) {
                if (className.endsWith("." + repoType)) {
                    return true;
                }
            }
        }
        
        // Check method names in invocation - be more specific
        if (invocationString != null) {
            // JDBC methods
            for (String method : JDBC_METHODS) {
                if (invocationString.matches(".*\\b" + method + "\\s*\\(.*")) {
                    // Only if the class is also database-related
                    if (className.contains("sql") || className.contains("jdbc") || 
                        className.contains("Connection") || className.contains("Statement")) {
                        return true;
                    }
                }
            }
            
            // MyBatis/iBatis methods
            for (String method : MYBATIS_METHODS) {
                if (invocationString.matches(".*\\b" + method + "\\s*\\(.*")) {
                    if (className.contains("ibatis") || className.contains("SqlSession") ||
                        className.contains("SqlMap")) {
                        return true;
                    }
                }
            }
            
            for (String method : JPA_METHODS) {
                if (invocationString.matches(".*\\b" + method + "\\s*\\(.*")) {
                    // Only if the class is also JPA-related
                    if (className.contains("persistence") || className.contains("hibernate") ||
                        className.contains("EntityManager") || className.contains("Query")) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Find tables from JPA annotations.
     */
    private Set<String> findTablesFromAnnotations(CtType<?> type) {
        Set<String> tables = new HashSet<>();
        
        // Check class-level annotations
        for (CtAnnotation<?> annotation : type.getAnnotations()) {
            String annotationName = annotation.getAnnotationType().getSimpleName();
            
            if ("Table".equals(annotationName)) {
                String tableName = extractTableNameFromAnnotation(annotation.toString());
                if (tableName != null) {
                    tables.add(tableName);
                }
            }
        }
        
        // Check method-level annotations (for @Query, @NamedQuery, etc.)
        List<CtMethod<?>> methods = type.getElements(new TypeFilter<>(CtMethod.class));
        for (CtMethod<?> method : methods) {
            for (CtAnnotation<?> annotation : method.getAnnotations()) {
                String annotationName = annotation.getAnnotationType().getSimpleName();
                
                if ("Query".equals(annotationName) || "NamedQuery".equals(annotationName)) {
                    String sql = extractQueryFromAnnotation(annotation.toString());
                    if (sql != null) {
                        tables.addAll(extractTableNamesFromSql(sql));
                    }
                }
            }
        }
        
        return tables;
    }
    
    /**
     * Find tables from SQL queries in string literals.
     */
    private Set<String> findTablesFromSqlQueries(CtType<?> type) {
        Set<String> tables = new HashSet<>();
        
        String sourceCode = type.toString();
        
        // Find actual SQL string literals (enclosed in quotes)
        Pattern sqlStringPattern = Pattern.compile("[\"'](.*(?:SELECT|INSERT|UPDATE|DELETE|FROM|JOIN).*)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher matcher = sqlStringPattern.matcher(sourceCode);
        
        while (matcher.find()) {
            String sqlQuery = matcher.group(1);
            if (sqlQuery != null && sqlQuery.length() > 10) { // Filter out short non-SQL strings
                tables.addAll(extractTableNamesFromSql(sqlQuery));
            }
        }
        
        return tables;
    }
    
    /**
     * Find tables from Spring Data repository method names.
     */
    private Set<String> findTablesFromRepositoryMethods(CtType<?> type) {
        Set<String> tables = new HashSet<>();
        
        // Check if this type extends a Spring Data repository
        if (isSpringDataRepository(type)) {
            // Extract entity type from repository declaration
            String entityType = extractEntityTypeFromRepository(type);
            if (entityType != null) {
                String tableName = inferTableNameFromClassName(entityType);
                tables.add(tableName);
            }
        }
        
        return tables;
    }
    
    /**
     * Check if a type is a JPA entity.
     */
    private boolean isEntityClass(CtType<?> type) {
        return type.getAnnotations().stream()
            .anyMatch(annotation -> "Entity".equals(annotation.getAnnotationType().getSimpleName()));
    }
    
    /**
     * Check if a type is a Spring Data repository.
     */
    private boolean isSpringDataRepository(CtType<?> type) {
        return type.getSuperInterfaces().stream()
            .anyMatch(iface -> SPRING_DATA_REPOSITORIES.contains(iface.getSimpleName()));
    }
    
    /**
     * Extract table name from @Table annotation.
     */
    private String extractTableNameFromAnnotation(String annotationString) {
        Matcher matcher = ANNOTATION_TABLE_PATTERN.matcher(annotationString);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    /**
     * Extract SQL query from @Query annotation.
     */
    private String extractQueryFromAnnotation(String annotationString) {
        // Look for value = "..." or just "..." after @Query
        Pattern pattern = Pattern.compile("(?:value\\s*=\\s*)?[\"']([^\"']*(?:SELECT|INSERT|UPDATE|DELETE)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(annotationString);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    /**
     * Extract table names from SQL query string.
     */
    private Set<String> extractTableNamesFromSql(String sql) {
        Set<String> tables = new HashSet<>();
        
        Matcher matcher = TABLE_NAME_PATTERN.matcher(sql);
        while (matcher.find()) {
            String tableName = matcher.group(1);
            if (tableName != null && !tableName.isEmpty() && isValidTableName(tableName)) {
                tables.add(tableName.toLowerCase());
            }
        }
        
        return tables;
    }
    
    /**
     * Check if a string is a valid table name (not a SQL keyword or common non-table word).
     */
    private boolean isValidTableName(String name) {
        if (name == null || name.length() < 2) return false;
        
        // Exclude SQL keywords and common non-table words
        String lowerName = name.toLowerCase();
        Set<String> sqlKeywords = Set.of(
            "select", "from", "where", "and", "or", "not", "in", "like", "as", "on",
            "inner", "outer", "left", "right", "join", "order", "by", "group",
            "having", "union", "distinct", "count", "sum", "max", "min", "avg",
            "values", "insert", "update", "delete", "create", "drop", "alter",
            "table", "index", "database", "schema", "primary", "foreign", "key",
            "constraint", "null", "auto_increment", "varchar", "int",
            "class", "entity", "repository", "spring", "jpa", "sql", "java"
        );
        
        return !sqlKeywords.contains(lowerName) && name.matches("[a-zA-Z_][a-zA-Z0-9_]*");
    }
    
    /**
     * Extract entity type from repository interface.
     */
    private String extractEntityTypeFromRepository(CtType<?> type) {
        // This is a simplified extraction - in practice, you'd need to parse
        // the generic type parameters more carefully
        String typeName = type.toString();
        
        // Look for patterns like "extends JpaRepository<Entity, Long>"
        Pattern pattern = Pattern.compile("extends\\s+\\w+Repository<([^,>]+)");
        Matcher matcher = pattern.matcher(typeName);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        return null;
    }
    
    /**
     * Infer table name from class name (convert CamelCase to snake_case).
     */
    private String inferTableNameFromClassName(String className) {
        if (className == null) return null;
        
        // Remove common suffixes
        className = className.replaceAll("(Entity|Model|Domain)$", "");
        
        // Convert CamelCase to snake_case
        return className.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}