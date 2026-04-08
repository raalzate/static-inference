package com.extractor.inference.rules;

import com.extractor.inference.InferenceRule;
import com.extractor.inference.Cluster;
import com.extractor.model.Component;
import java.util.List;

/**
 * Rule that fires when cluster members share database tables.
 * Shared data indicates components that should stay together for data consistency.
 */
public class SharedDataRule implements InferenceRule {
    
    @Override
    public boolean evaluate(Cluster cluster, List<Component> allComponents) {
        return !cluster.getMetrics().getTablesShared().isEmpty();
    }
    
    @Override
    public String getRuleName() {
        return "Consistencia de Datos (Tablas Compartidas)";
    }
    
    @Override
    public double getScoreContribution() {
        return 0.2; // 20% of total score
    }
    
    @Override
    public String getExplanation(Cluster cluster, List<Component> allComponents) {
        List<String> tables = cluster.getMetrics().getTablesShared();
        if (tables.isEmpty()) {
            return "";
        }
        
        String exampleTable = tables.get(0);
        return String.format("Regla 'Datos Comunes': Los miembros comparten %d tablas (ej. '%s'). Agruparlos mantiene la consistencia de datos.", 
                           tables.size(), exampleTable);
    }
}