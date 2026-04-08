package com.extractor.analyzer;

public class TableNameExtractor {
    
    public String extractTableNameFromClass(String className) {
        if (className == null || !className.contains(".")) return null;
        
        String simpleName = className.substring(className.lastIndexOf(".") + 1);
        
        if (simpleName.endsWith("Entity")) {
            String tableName = simpleName.substring(0, simpleName.length() - 6);
            return tableName.toLowerCase();
        }
        
        if (simpleName.endsWith("Model")) {
            String tableName = simpleName.substring(0, simpleName.length() - 5);
            return tableName.toLowerCase();
        }
        
        return null;
    }
}
