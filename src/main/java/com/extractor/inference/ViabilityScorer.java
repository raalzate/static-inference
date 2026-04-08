package com.extractor.inference;

import com.extractor.model.Component;
import java.util.*;
import java.util.stream.Collectors;

public class ViabilityScorer {
    private final List<Cluster> allClusters;
    private final Map<String, Component> componentMap;

    public ViabilityScorer(List<Cluster> allClusters, List<Component> allComponents) {
        this.allClusters = allClusters;
        this.componentMap = allComponents.stream()
            .collect(Collectors.toMap(Component::getId, c -> c));
    }

    public ViabilityResult calculateViability(Set<Integer> clusterIds) {
        List<Cluster> clusters = clusterIds.stream()
            .map(id -> allClusters.stream()
                .filter(c -> c.getClusterId() == id)
                .findFirst()
                .orElse(null))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        if (clusters.isEmpty()) {
            return new ViabilityResult("Baja", 0.0, Arrays.asList("No se encontraron clusters válidos"));
        }
        
        double cohesionAdj = calculateAdjustedCohesion(clusters);
        double externalCoupling = calculateExternalCoupling(clusters, clusterIds);
        double dataCohesion = calculateDataCohesion(clusters);
        
        int totalSize = clusters.stream()
            .mapToInt(c -> c.getMembers().size())
            .sum();
        
        // Calculate CBO and LCOM metrics
        CodeQualityMetrics qualityMetrics = calculateCodeQualityMetrics(clusters);
        
        double score = 0.5 * cohesionAdj + 0.35 * (1 - externalCoupling) + 0.15 * dataCohesion;
        
        if (totalSize < 3) {
            score *= 0.6;
        } else if (totalSize > 50) {
            double internalDensity = calculateInternalEdgeDensity(clusters);
            if (internalDensity < 0.5) {
                score *= 0.7;
            }
        }
        
        String viability = score >= 0.7 ? "Alta" : (score >= 0.5 ? "Media" : "Baja");
        List<String> rationale = generateRationale(cohesionAdj, externalCoupling, dataCohesion, totalSize, qualityMetrics, viability);
        
        return new ViabilityResult(viability, score, rationale);
    }

    private double calculateAdjustedCohesion(List<Cluster> clusters) {
        if (clusters.isEmpty()) return 0.0;
        
        double weightedSum = 0.0;
        int totalSize = 0;
        
        for (Cluster cluster : clusters) {
            int size = cluster.getMembers().size();
            double cohesion = cluster.getMetrics().getCohesion();
            weightedSum += cohesion * size;
            totalSize += size;
        }
        
        double avgCohesion = totalSize > 0 ? weightedSum / totalSize : 0.0;
        
        double internalDensity = calculateInternalEdgeDensity(clusters);
        
        return 0.7 * avgCohesion + 0.3 * internalDensity;
    }

    private double calculateInternalEdgeDensity(List<Cluster> clusters) {
        Set<String> allMembers = clusters.stream()
            .flatMap(c -> c.getMembers().stream())
            .collect(Collectors.toSet());
        
        int internalEdges = 0;
        for (String member : allMembers) {
            Component comp = componentMap.get(member);
            if (comp != null && comp.getCallsOut() != null) {
                for (String called : comp.getCallsOut()) {
                    if (allMembers.contains(called)) {
                        internalEdges++;
                    }
                }
            }
        }
        
        int possibleEdges = allMembers.size() * (allMembers.size() - 1);
        return possibleEdges > 0 ? (double) internalEdges / possibleEdges : 0.0;
    }

    private double calculateExternalCoupling(List<Cluster> clusters, Set<Integer> clusterIds) {
        Set<String> members = clusters.stream()
            .flatMap(c -> c.getMembers().stream())
            .collect(Collectors.toSet());
        
        int internalCalls = 0;
        int externalCalls = 0;
        
        for (String member : members) {
            Component comp = componentMap.get(member);
            if (comp != null && comp.getCallsOut() != null) {
                for (String called : comp.getCallsOut()) {
                    if (members.contains(called)) {
                        internalCalls++;
                    } else {
                        externalCalls++;
                    }
                }
            }
        }
        
        int totalCalls = internalCalls + externalCalls;
        return totalCalls > 0 ? (double) externalCalls / totalCalls : 0.0;
    }

    private double calculateDataCohesion(List<Cluster> clusters) {
        Set<String> allTables = clusters.stream()
            .flatMap(c -> c.getMetrics().getTablesShared().stream())
            .collect(Collectors.toSet());
        
        if (allTables.isEmpty()) return 0.5;
        
        Map<String, Long> tableCounts = new HashMap<>();
        for (Cluster cluster : clusters) {
            for (String table : cluster.getMetrics().getTablesShared()) {
                tableCounts.merge(table, 1L, Long::sum);
            }
        }
        
        long sharedTables = tableCounts.values().stream()
            .filter(count -> count > 1)
            .count();
        
        return allTables.size() > 0 ? (double) sharedTables / allTables.size() : 0.5;
    }
    
    private CodeQualityMetrics calculateCodeQualityMetrics(List<Cluster> clusters) {
        List<Component> components = clusters.stream()
            .flatMap(c -> c.getMembers().stream())
            .distinct()
            .map(componentMap::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        if (components.isEmpty()) {
            return new CodeQualityMetrics(0.0, 0.0, 0, 0);
        }
        
        // Calculate average CBO
        List<Integer> cboValues = components.stream()
            .map(Component::getCbo)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        double avgCbo = cboValues.isEmpty() ? 0.0 : 
            cboValues.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        
        // Calculate average LCOM
        List<Double> lcomValues = components.stream()
            .map(Component::getLcom)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        double avgLcom = lcomValues.isEmpty() ? 0.0 :
            lcomValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        int componentsWithCbo = cboValues.size();
        int componentsWithLcom = lcomValues.size();
        
        return new CodeQualityMetrics(avgCbo, avgLcom, componentsWithCbo, componentsWithLcom);
    }
    
    private static class CodeQualityMetrics {
        final double avgCbo;
        final double avgLcom;
        final int componentsWithCbo;
        final int componentsWithLcom;
        
        CodeQualityMetrics(double avgCbo, double avgLcom, int componentsWithCbo, int componentsWithLcom) {
            this.avgCbo = avgCbo;
            this.avgLcom = avgLcom;
            this.componentsWithCbo = componentsWithCbo;
            this.componentsWithLcom = componentsWithLcom;
        }
    }

    private List<String> generateRationale(double cohesionAdj, double externalCoupling, 
                                          double dataCohesion, int totalSize, 
                                          CodeQualityMetrics qualityMetrics, String viability) {
        List<String> rationale = new ArrayList<>();
        
        // Add code quality metrics summary first
        if (qualityMetrics.componentsWithCbo > 0) {
            rationale.add(String.format("📊 Métricas de Calidad: CBO promedio %.1f (acoplamiento entre objetos), " +
                                       "LCOM promedio %.2f (cohesión de métodos: 0=alta, 1=baja)",
                                       qualityMetrics.avgCbo, qualityMetrics.avgLcom));
        }
        
        // Cohesion analysis with detailed explanations
        double cohesionPercent = Math.round(cohesionAdj * 100);
        if (cohesionAdj >= 0.7) {
            rationale.add("✅ Alta cohesión interna (" + String.format("%.0f%%", cohesionPercent) + 
                         ") - componentes bien relacionados que trabajan juntos hacia un objetivo común");
        } else if (cohesionAdj >= 0.5) {
            rationale.add("⚠️ Cohesión moderada (" + String.format("%.0f%%", cohesionPercent) + 
                         ") - componentes parcialmente relacionados; considerar refactorización para agrupar responsabilidades más claramente");
        } else {
            rationale.add("❌ Baja cohesión (" + String.format("%.0f%%", cohesionPercent) + 
                         ") - componentes poco relacionados que no comparten un propósito claro");
        }
        
        // Coupling analysis with detailed explanations
        double couplingPercent = Math.round(externalCoupling * 100);
        if (externalCoupling < 0.3) {
            rationale.add("✅ Bajo acoplamiento externo (" + String.format("%.0f%%", couplingPercent) + 
                         ") - buena independencia y facilidad de mantenimiento");
        } else if (externalCoupling < 0.5) {
            rationale.add("⚠️ Acoplamiento moderado (" + String.format("%.0f%%", couplingPercent) + 
                         ") - algunas dependencias externas; considerar aplicar patrones como facades o abstracciones para reducir acoplamiento");
        } else {
            rationale.add("❌ Alto acoplamiento externo (" + String.format("%.0f%%", couplingPercent) + 
                         ") - fuertemente acoplado a otros módulos, dificultando la extracción independiente");
        }
        
        // CBO-specific analysis
        if (qualityMetrics.componentsWithCbo > 0) {
            if (qualityMetrics.avgCbo <= 5) {
                rationale.add("✅ CBO bajo (" + String.format("%.1f", qualityMetrics.avgCbo) + 
                             ") - acoplamiento entre clases controlado, fácil de mantener");
            } else if (qualityMetrics.avgCbo <= 10) {
                rationale.add("⚠️ CBO moderado (" + String.format("%.1f", qualityMetrics.avgCbo) + 
                             ") - acoplamiento moderado; revisar dependencias innecesarias entre clases");
            } else {
                rationale.add("❌ CBO alto (" + String.format("%.1f", qualityMetrics.avgCbo) + 
                             ") - acoplamiento excesivo entre clases, dificulta mantenimiento y testing");
            }
        }
        
        // LCOM-specific analysis
        if (qualityMetrics.componentsWithLcom > 0) {
            if (qualityMetrics.avgLcom <= 0.3) {
                rationale.add("✅ LCOM bajo (" + String.format("%.2f", qualityMetrics.avgLcom) + 
                             ") - alta cohesión de métodos, clases con responsabilidad única bien definida");
            } else if (qualityMetrics.avgLcom <= 0.6) {
                rationale.add("⚠️ LCOM moderado (" + String.format("%.2f", qualityMetrics.avgLcom) + 
                             ") - cohesión de métodos moderada; algunas clases podrían dividirse en clases más pequeñas");
            } else {
                rationale.add("❌ LCOM alto (" + String.format("%.2f", qualityMetrics.avgLcom) + 
                             ") - baja cohesión de métodos, clases con múltiples responsabilidades que deberían dividirse");
            }
        }
        
        // Data cohesion
        if (dataCohesion >= 0.6) {
            rationale.add("✅ Datos cohesivos - tablas de base de datos bien agrupadas por dominio");
        } else if (dataCohesion >= 0.3) {
            rationale.add("⚠️ Datos parcialmente cohesivos - revisar si las tablas compartidas realmente pertenecen al mismo dominio");
        }
        
        // Size analysis
        if (totalSize < 3) {
            rationale.add("⚠️ Tamaño muy pequeño (" + totalSize + " componentes) - considerar fusionar con otro módulo relacionado para evitar sobrefragmentación");
        } else if (totalSize > 50) {
            rationale.add("⚠️ Tamaño muy grande (" + totalSize + " componentes) - considerar dividir en submódulos más manejables");
        } else {
            rationale.add("✅ Tamaño adecuado (" + totalSize + " componentes) - módulo de tamaño manejable");
        }
        
        // Add detailed explanation for LOW viability cases
        if ("Baja".equals(viability)) {
            rationale.add("");
            rationale.add("⛔ RAZONES POR LAS QUE ESTA DESCOMPOSICIÓN NO ES VIABLE:");
            
            List<String> reasons = new ArrayList<>();
            
            if (cohesionAdj < 0.5) {
                reasons.add("• Los componentes no comparten suficiente funcionalidad ni datos como para formar un módulo coherente. " +
                           "Extraerlos juntos crearía un módulo artificial sin un propósito de negocio claro.");
            }
            
            if (externalCoupling >= 0.5) {
                reasons.add("• El alto acoplamiento externo (" + String.format("%.0f%%", couplingPercent) + ") significa que este módulo " +
                           "depende fuertemente de otros componentes del sistema. Extraerlo como módulo independiente requeriría " +
                           "replicar o exponer demasiada funcionalidad de otros módulos, creando interfaces complejas y frágiles.");
            }
            
            if (qualityMetrics.avgCbo > 10) {
                reasons.add("• CBO promedio alto (" + String.format("%.1f", qualityMetrics.avgCbo) + ") indica que las clases están " +
                           "acopladas a muchas otras clases del sistema. Esto dificulta definir límites claros del módulo y " +
                           "aumenta el riesgo de cambios en cascada.");
            }
            
            if (qualityMetrics.avgLcom > 0.6) {
                reasons.add("• LCOM promedio alto (" + String.format("%.2f", qualityMetrics.avgLcom) + ") sugiere que las clases tienen " +
                           "múltiples responsabilidades no relacionadas. Antes de extraer como módulo, se debería refactorizar " +
                           "para separar estas responsabilidades.");
            }
            
            if (totalSize < 3) {
                reasons.add("• Con solo " + totalSize + " componente(s), no justifica crear un módulo separado. " +
                           "El overhead de gestionar un módulo adicional (interfaces, versionado, deployment) superaría los beneficios.");
            }
            
            if (dataCohesion < 0.3 && dataCohesion > 0) {
                reasons.add("• La baja cohesión de datos indica que los componentes acceden a tablas diferentes sin un patrón claro. " +
                           "Esto sugiere que pertenecen a dominios de negocio distintos y deberían agruparse de otra manera.");
            }
            
            if (reasons.isEmpty()) {
                reasons.add("• La combinación de métricas sugiere que estos componentes no forman una unidad funcional coherente " +
                           "que justifique su extracción como módulo independiente.");
            }
            
            rationale.addAll(reasons);
            
            rationale.add("");
            rationale.add("💡 RECOMENDACIÓN: Mantener estos componentes en el monolito actual o reagrupar con otros componentes " +
                         "con los que compartan más funcionalidad y datos. Enfocarse primero en extraer módulos con viabilidad Alta o Media.");
        }
        
        return rationale;
    }

    public static class ViabilityResult {
        private final String viability;
        private final double score;
        private final List<String> rationale;

        public ViabilityResult(String viability, double score, List<String> rationale) {
            this.viability = viability;
            this.score = score;
            this.rationale = rationale;
        }

        public String getViability() { return viability; }
        public double getScore() { return score; }
        public List<String> getRationale() { return rationale; }
    }
}
