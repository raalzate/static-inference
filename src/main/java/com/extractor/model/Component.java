package com.extractor.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents a component (class/interface/enum) in the dependency graph.
 * This matches the Sofka schema exactly - simple lists for calls_out/calls_in,
 * no dependencies field.
 */
public class Component {

    @JsonProperty("id")
    private String id; // Fully qualified class name

    @JsonProperty("files")
    private List<String> files;

    @JsonProperty("loc")
    private int loc; // Lines of code

    @JsonProperty("tables_used")
    private List<String> tablesUsed;

    @JsonProperty("sensitive_data")
    private boolean sensitiveData;

    @JsonProperty("domain")
    private String domain;

    @JsonProperty("layer")
    private String layer;

    @JsonProperty("calls_out")
    private List<String> callsOut;

    @JsonProperty("calls_in")
    private List<String> callsIn;

    @JsonProperty("ejb_type")
    private String ejbType;

    @JsonProperty("uses_jndi")
    private boolean usesJNDI;

    @JsonProperty("annotations")
    private List<String> annotations;

    @JsonProperty("is_interface")
    private boolean isInterface;

    @JsonProperty("secrets_references")
    private List<String> secretsReferences;

    @JsonProperty("external_dependencies")
    private List<String> externalDependencies;

    @JsonProperty("package_dependencies")
    private List<PackageGroup> packageDependencies;

    @JsonProperty("code_issues")
    private List<CodeIssue> codeIssues;

    @JsonProperty("extends")
    private String extendsClass;

    @JsonProperty("implements")
    private List<String> implementsInterfaces;

    @JsonProperty("messaging_type")
    private String messagingType;

    @JsonProperty("messaging_role")
    private String messagingRole;

    @JsonProperty("web_type")
    private String webType;

    @JsonProperty("web_role")
    private String webRole;

    @JsonProperty("cbo")
    private Integer cbo; // Coupling Between Objects

    @JsonProperty("lcom")
    private Double lcom; // Lack of Cohesion in Methods (LCOM-HS: 0=high cohesion, 1=low cohesion)

    public Component() {
        this.files = new ArrayList<>();
        this.tablesUsed = new ArrayList<>();
        this.callsOut = new ArrayList<>();
        this.callsIn = new ArrayList<>();
        this.annotations = new ArrayList<>();
        this.secretsReferences = new ArrayList<>();
        this.externalDependencies = new ArrayList<>();
        this.packageDependencies = new ArrayList<>();
        this.implementsInterfaces = new ArrayList<>();
        this.codeIssues = new ArrayList<>();
        this.sensitiveData = false;
        this.usesJNDI = false;
        this.isInterface = false;
    }

    public Component(String id) {
        this();
        this.id = id;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void addFile(String file) {
        if (!this.files.contains(file)) {
            this.files.add(file);
        }
    }

    public List<String> getFiles() {
        return files;
    }

    public List<CodeIssue> getCodeIssues() {
        return codeIssues;
    }

    public void setCodeIssues(List<CodeIssue> codeIssues) {
        this.codeIssues = codeIssues;
    }

    public void addCodeIssue(CodeIssue issue) {
        if (this.codeIssues == null) {
            this.codeIssues = new ArrayList<>();
        }
        this.codeIssues.add(issue);
    }

    public int getLoc() {
        return loc;
    }

    public void setLoc(int loc) {
        this.loc = loc;
    }

    public List<String> getTablesUsed() {
        return tablesUsed;
    }

    public void setTablesUsed(List<String> tablesUsed) {
        this.tablesUsed = tablesUsed;
    }

    public void addTableUsed(String tableName) {
        if (!this.tablesUsed.contains(tableName)) {
            this.tablesUsed.add(tableName);
        }
    }

    public boolean isSensitiveData() {
        return sensitiveData;
    }

    public void setSensitiveData(boolean sensitiveData) {
        this.sensitiveData = sensitiveData;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getLayer() {
        return layer;
    }

    public void setLayer(String layer) {
        this.layer = layer;
    }

    public List<String> getCallsOut() {
        return callsOut;
    }

    public void setCallsOut(List<String> callsOut) {
        this.callsOut = callsOut;
    }

    public void addCallOut(String to) {
        if (!callsOut.contains(to)) {
            callsOut.add(to);
        }
    }

    public List<String> getCallsIn() {
        return callsIn;
    }

    public void setCallsIn(List<String> callsIn) {
        this.callsIn = callsIn;
    }

    public void addCallIn(String from) {
        if (!callsIn.contains(from)) {
            callsIn.add(from);
        }
    }

    public String getEjbType() {
        return ejbType;
    }

    public void setEjbType(String ejbType) {
        this.ejbType = ejbType;
    }

    public boolean isUsesJNDI() {
        return usesJNDI;
    }

    public void setUsesJNDI(boolean usesJNDI) {
        this.usesJNDI = usesJNDI;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(List<String> annotations) {
        this.annotations = annotations;
    }

    public void addAnnotation(String annotation) {
        if (!this.annotations.contains(annotation)) {
            this.annotations.add(annotation);
        }
    }

    public boolean isInterface() {
        return isInterface;
    }

    public void setInterface(boolean isInterface) {
        this.isInterface = isInterface;
    }

    public List<String> getSecretsReferences() {
        return secretsReferences;
    }

    public void setSecretsReferences(List<String> secretsReferences) {
        this.secretsReferences = secretsReferences;
    }

    public List<String> getExternalDependencies() {
        return externalDependencies;
    }

    public void setExternalDependencies(List<String> externalDependencies) {
        this.externalDependencies = externalDependencies;
    }

    public void addExternalDependency(String dependency) {
        if (!this.externalDependencies.contains(dependency)) {
            this.externalDependencies.add(dependency);
        }
    }

    public List<PackageGroup> getPackageDependencies() {
        return packageDependencies;
    }

    public void setPackageDependencies(List<PackageGroup> packageDependencies) {
        this.packageDependencies = packageDependencies;
    }

    public void addPackageDependency(PackageGroup packageGroup) {
        if (packageGroup != null && !this.packageDependencies.contains(packageGroup)) {
            this.packageDependencies.add(packageGroup);
        }
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

    public void addImplementsInterface(String interfaceName) {
        if (!this.implementsInterfaces.contains(interfaceName)) {
            this.implementsInterfaces.add(interfaceName);
        }
    }

    public String getMessagingType() {
        return messagingType;
    }

    public void setMessagingType(String messagingType) {
        this.messagingType = messagingType;
    }

    public String getMessagingRole() {
        return messagingRole;
    }

    public void setMessagingRole(String messagingRole) {
        this.messagingRole = messagingRole;
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

    public Integer getCbo() {
        return cbo;
    }

    public void setCbo(Integer cbo) {
        this.cbo = cbo;
    }

    public Double getLcom() {
        return lcom;
    }

    public void setLcom(Double lcom) {
        this.lcom = lcom;
    }

    /**
     * Normalize the component data by sorting and deduplicating collections.
     */
    public void normalize() {
        // Sort and deduplicate tables_used
        tablesUsed = tablesUsed.stream().distinct().sorted().collect(Collectors.toList());

        // Sort and deduplicate calls_out and calls_in
        callsOut = callsOut.stream().distinct().sorted().collect(Collectors.toList());
        callsIn = callsIn.stream().distinct().sorted().collect(Collectors.toList());

        // Sort and deduplicate annotations
        annotations = annotations.stream().distinct().sorted().collect(Collectors.toList());

        // Sort and deduplicate secrets references
        secretsReferences = secretsReferences.stream().distinct().sorted().collect(Collectors.toList());

        // Sort and deduplicate external dependencies
        externalDependencies = externalDependencies.stream().distinct().sorted().collect(Collectors.toList());

        // Sort and deduplicate implements interfaces
        implementsInterfaces = implementsInterfaces.stream().distinct().sorted().collect(Collectors.toList());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Component component = (Component) o;
        return Objects.equals(id, component.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Component{" +
                "id='" + id + '\'' +
                ", files=" + files +
                ", loc=" + loc +
                ", tablesUsed=" + tablesUsed +
                ", sensitiveData=" + sensitiveData +
                '}';
    }
}