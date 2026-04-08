package com.extractor.inference;

import com.extractor.model.Component;
import java.util.List;

/**
 * Generates human-readable explanations for cluster evaluations.
 */
public class ExplanationGenerator {
    
    /**
     * Generates explanation for a cluster based on its metrics and fired rules.
     */
    public ClusterExplanation generateExplanation(Cluster cluster, List<Component> allComponents) {
        ClusterExplanation explanation = new ClusterExplanation(cluster.getClusterId());
        
        List<String> rulesFired = cluster.getRulesFired();
        ClusterMetrics metrics = cluster.getMetrics();
        
        if (rulesFired.contains("Alta Cohesión Interna") && rulesFired.contains("Bajo Acoplamiento Externo")) {
            // High quality cluster
            String cohesionExplanation = String.format(
                "Alta Cohesión (%.0f%%): Las clases de este clúster se llaman mucho entre sí. " +
                "Bajo Acoplamiento (%.0f%%): El grupo tiene pocas dependencias externas, facilitando su aislamiento.",
                metrics.getCohesion() * 100, metrics.getCoupling() * 100
            );
            explanation.addReasoning(cohesionExplanation);
            
            if (rulesFired.contains("Consistencia de Datos (Tablas Compartidas)")) {
                List<String> tables = metrics.getTablesShared();
                if (!tables.isEmpty()) {
                    String dataExplanation = String.format(
                        "Regla 'Datos Comunes': Los miembros comparten %d tablas (ej. '%s'). " +
                        "Agruparlos mantiene la consistencia de datos.",
                        tables.size(), tables.get(0)
                    );
                    explanation.addReasoning(dataExplanation);
                }
            }
        } else {
            // Lower quality cluster
            String basicExplanation = String.format(
                "Métricas de estructura: Cohesión interna del %.0f%% y Acoplamiento externo del %.0f%%.",
                metrics.getCohesion() * 100, metrics.getCoupling() * 100
            );
            explanation.addReasoning(basicExplanation);
        }
        
        return explanation;
    }
}