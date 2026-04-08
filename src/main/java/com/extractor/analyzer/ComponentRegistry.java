package com.extractor.analyzer;

import com.extractor.model.Component;
import com.extractor.model.DependencyGraph;
import com.extractor.inference.LayerClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ComponentRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ComponentRegistry.class);
    private final Map<String, Component> components = new HashMap<>();
    private final LayerClassifier layerClassifier = new LayerClassifier();
    private final DependencyGraph.ApiContracts apiContracts = new DependencyGraph.ApiContracts();

    public DependencyGraph.ApiContracts getApiContracts() {
        return apiContracts;
    }

    public void registerComponent(Component component) {
        components.put(component.getId(), component);
    }

    public Component getComponent(String id) {
        return components.get(id);
    }

    public Component getOrCreateComponent(String id) {
        return components.computeIfAbsent(id, Component::new);
    }

    public boolean hasComponent(String id) {
        return components.containsKey(id);
    }

    public Collection<Component> getAllComponents() {
        return components.values();
    }

    public int size() {
        return components.size();
    }

    public void clear() {
        components.clear();
    }

    public void normalizeAll() {
        components.values().forEach(Component::normalize);
    }

    public List<Component> getSortedComponents() {
        return components.values().stream()
                .sorted(Comparator.comparing(Component::getId))
                .collect(Collectors.toList());
    }

    public void classifyAllLayers() {
        logger.info("Classifying {} components into architectural layers...", components.size());
        int controllerCount = 0;
        int businessCount = 0;
        int dataCount = 0;
        int sharedCount = 0;

        for (Component component : components.values()) {
            LayerClassifier.Layer layer = layerClassifier.classifyComponent(component);
            component.setLayer(layer.getDisplayName());

            switch (layer) {
                case CONTROLLER:
                    controllerCount++;
                    break;
                case BUSINESS:
                    businessCount++;
                    break;
                case DATA:
                case PERSISTENCE:
                case TRANSFER:
                case DOMAIN:
                    dataCount++;
                    break;
                case SHARED:
                    sharedCount++;
                    break;
                default:
                    sharedCount++;
                    break;
            }
        }

        logger.info("Layer classification complete:");
        logger.info("  - Controlador: {} components", controllerCount);
        logger.info("  - Negocio: {} components", businessCount);
        logger.info("  - Datos: {} components", dataCount);
        logger.info("  - Compartida: {} components", sharedCount);
    }
}
