package com.extractor.inference;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class ConsolidatedArchitecture {
    @JsonProperty("project_metadata")
    private ProjectMetadata projectMetadata;
    
    @JsonProperty("proposals")
    private List<MicroserviceProposal> proposals;
    
    @JsonProperty("support_libraries")
    private List<SupportLibrary> supportLibraries;
    
    @JsonProperty("summary")
    private String summary;

    public ConsolidatedArchitecture(ProjectMetadata projectMetadata,
                                   List<MicroserviceProposal> proposals, 
                                   List<SupportLibrary> supportLibraries,
                                   String summary) {
        this.projectMetadata = projectMetadata;
        this.proposals = proposals;
        this.supportLibraries = supportLibraries;
        this.summary = summary;
    }

    public ProjectMetadata getProjectMetadata() { return projectMetadata; }
    public List<MicroserviceProposal> getProposals() { return proposals; }
    public List<SupportLibrary> getSupportLibraries() { return supportLibraries; }
    public String getSummary() { return summary; }
    
    public static class ProjectMetadata {
        @JsonProperty("external_dependencies")
        private Map<String, String> externalDependencies;
        
        @JsonProperty("package_dependencies")
        private Map<String, PackageDependencyInfo> packageDependencies;
        
        @JsonProperty("total_components")
        private int totalComponents;
        
        @JsonProperty("total_loc")
        private int totalLoc;
        
        @JsonProperty("components_with_secrets")
        private int componentsWithSecrets;
        
        @JsonProperty("shared_domain")
        private String sharedDomain;
        
        public ProjectMetadata(Map<String, String> externalDependencies,
                             Map<String, PackageDependencyInfo> packageDependencies,
                             int totalComponents,
                             int totalLoc,
                             int componentsWithSecrets,
                             String sharedDomain) {
            this.externalDependencies = externalDependencies;
            this.packageDependencies = packageDependencies;
            this.totalComponents = totalComponents;
            this.totalLoc = totalLoc;
            this.componentsWithSecrets = componentsWithSecrets;
            this.sharedDomain = sharedDomain;
        }
        
        public Map<String, String> getExternalDependencies() { return externalDependencies; }
        public Map<String, PackageDependencyInfo> getPackageDependencies() { return packageDependencies; }
        public int getTotalComponents() { return totalComponents; }
        public int getTotalLoc() { return totalLoc; }
        public int getComponentsWithSecrets() { return componentsWithSecrets; }
        public String getSharedDomain() { return sharedDomain; }
    }
    
    public static class PackageDependencyInfo {
        @JsonProperty("components_count")
        private int componentsCount;
        
        @JsonProperty("total_dependencies_out")
        private int totalDependenciesOut;
        
        @JsonProperty("depends_on_packages")
        private List<String> dependsOnPackages;
        
        public PackageDependencyInfo(int componentsCount, int totalDependenciesOut, List<String> dependsOnPackages) {
            this.componentsCount = componentsCount;
            this.totalDependenciesOut = totalDependenciesOut;
            this.dependsOnPackages = dependsOnPackages;
        }
        
        public int getComponentsCount() { return componentsCount; }
        public int getTotalDependenciesOut() { return totalDependenciesOut; }
        public List<String> getDependsOnPackages() { return dependsOnPackages; }
    }

    public static class SupportLibrary {
        @JsonProperty("id")
        private int id;
        
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("clusters")
        private List<Integer> clusterIds;
        
        @JsonProperty("components")
        private List<String> componentNames;

        public SupportLibrary(int id, String name, List<Integer> clusterIds, List<String> componentNames) {
            this.id = id;
            this.name = name;
            this.clusterIds = clusterIds;
            this.componentNames = componentNames;
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public List<Integer> getClusterIds() { return clusterIds; }
        public List<String> getComponentNames() { return componentNames; }
    }
}
