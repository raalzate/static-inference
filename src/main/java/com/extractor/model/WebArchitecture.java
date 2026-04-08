package com.extractor.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the web/visual layer architecture analysis.
 * Contains servlets, filters, listeners, and other web components.
 */
public class WebArchitecture {
    
    @JsonProperty("total_web_components")
    private int totalWebComponents;
    
    @JsonProperty("servlets")
    private List<WebComponent> servlets;
    
    @JsonProperty("filters")
    private List<WebComponent> filters;
    
    @JsonProperty("listeners")
    private List<WebComponent> listeners;
    
    @JsonProperty("struts_actions")
    private List<WebComponent> strutsActions;
    
    @JsonProperty("struts_forms")
    private List<WebComponent> strutsForms;
    
    @JsonProperty("struts_processors")
    private List<WebComponent> strutsProcessors;
    
    @JsonProperty("web_utilities")
    private List<WebComponent> webUtilities;
    
    @JsonProperty("summary")
    private String summary;
    
    public WebArchitecture() {
        this.servlets = new ArrayList<>();
        this.filters = new ArrayList<>();
        this.listeners = new ArrayList<>();
        this.strutsActions = new ArrayList<>();
        this.strutsForms = new ArrayList<>();
        this.strutsProcessors = new ArrayList<>();
        this.webUtilities = new ArrayList<>();
    }
    
    public int getTotalWebComponents() {
        return totalWebComponents;
    }
    
    public void setTotalWebComponents(int totalWebComponents) {
        this.totalWebComponents = totalWebComponents;
    }
    
    public List<WebComponent> getServlets() {
        return servlets;
    }
    
    public void setServlets(List<WebComponent> servlets) {
        this.servlets = servlets;
    }
    
    public List<WebComponent> getFilters() {
        return filters;
    }
    
    public void setFilters(List<WebComponent> filters) {
        this.filters = filters;
    }
    
    public List<WebComponent> getListeners() {
        return listeners;
    }
    
    public void setListeners(List<WebComponent> listeners) {
        this.listeners = listeners;
    }
    
    public List<WebComponent> getStrutsActions() {
        return strutsActions;
    }
    
    public void setStrutsActions(List<WebComponent> strutsActions) {
        this.strutsActions = strutsActions;
    }
    
    public List<WebComponent> getStrutsForms() {
        return strutsForms;
    }
    
    public void setStrutsForms(List<WebComponent> strutsForms) {
        this.strutsForms = strutsForms;
    }
    
    public List<WebComponent> getStrutsProcessors() {
        return strutsProcessors;
    }
    
    public void setStrutsProcessors(List<WebComponent> strutsProcessors) {
        this.strutsProcessors = strutsProcessors;
    }
    
    public List<WebComponent> getWebUtilities() {
        return webUtilities;
    }
    
    public void setWebUtilities(List<WebComponent> webUtilities) {
        this.webUtilities = webUtilities;
    }
    
    public String getSummary() {
        return summary;
    }
    
    public void setSummary(String summary) {
        this.summary = summary;
    }
    
    /**
     * Represents a web component with its dependencies.
     */
    public static class WebComponent {
        @JsonProperty("id")
        private String id;
        
        @JsonProperty("web_type")
        private String webType;
        
        @JsonProperty("web_role")
        private String webRole;
        
        @JsonProperty("files")
        private List<String> files;
        
        @JsonProperty("loc")
        private int loc;
        
        @JsonProperty("calls_business_layer")
        private List<String> callsBusinessLayer;
        
        @JsonProperty("calls_data_layer")
        private List<String> callsDataLayer;
        
        @JsonProperty("extends")
        private String extendsClass;
        
        @JsonProperty("implements")
        private List<String> implementsInterfaces;
        
        @JsonProperty("annotations")
        private List<String> annotations;
        
        @JsonProperty("external_dependencies")
        private List<String> externalDependencies;
        
        public WebComponent() {
            this.files = new ArrayList<>();
            this.callsBusinessLayer = new ArrayList<>();
            this.callsDataLayer = new ArrayList<>();
            this.implementsInterfaces = new ArrayList<>();
            this.annotations = new ArrayList<>();
            this.externalDependencies = new ArrayList<>();
        }
        
        public String getId() {
            return id;
        }
        
        public void setId(String id) {
            this.id = id;
        }
        
        public String getWebType() {
            return webType;
        }
        
        public void setWebType(String webType) {
            this.webType = webType;
        }
        
        public String getWebRole() {
            return webRole;
        }
        
        public void setWebRole(String webRole) {
            this.webRole = webRole;
        }
        
        public List<String> getFiles() {
            return files;
        }
        
        public void setFiles(List<String> files) {
            this.files = files;
        }
        
        public int getLoc() {
            return loc;
        }
        
        public void setLoc(int loc) {
            this.loc = loc;
        }
        
        public List<String> getCallsBusinessLayer() {
            return callsBusinessLayer;
        }
        
        public void setCallsBusinessLayer(List<String> callsBusinessLayer) {
            this.callsBusinessLayer = callsBusinessLayer;
        }
        
        public List<String> getCallsDataLayer() {
            return callsDataLayer;
        }
        
        public void setCallsDataLayer(List<String> callsDataLayer) {
            this.callsDataLayer = callsDataLayer;
        }
        
        public String getExtendsClass() {
            return extendsClass;
        }
        
        public void setExtendsClass(String extendsClass) {
            this.extendsClass = extendsClass;
        }
        
        public List<String> getImplementsInterfaces() {
            return implementsInterfaces;
        }
        
        public void setImplementsInterfaces(List<String> implementsInterfaces) {
            this.implementsInterfaces = implementsInterfaces;
        }
        
        public List<String> getAnnotations() {
            return annotations;
        }
        
        public void setAnnotations(List<String> annotations) {
            this.annotations = annotations;
        }
        
        public List<String> getExternalDependencies() {
            return externalDependencies;
        }
        
        public void setExternalDependencies(List<String> externalDependencies) {
            this.externalDependencies = externalDependencies;
        }
    }
}
