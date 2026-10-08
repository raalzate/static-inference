/* eslint-disable */
// GENERATED FILE — DO NOT EDIT.
// Generated from @static-inference/schema contract 1.0.0 (schema/1.0.0/*.schema.json)
// by schema/gen/generate.mjs. Regenerate with: npm --prefix schema run generate

/**
 * Semantic version of the output contract. Contract 1.0.0 only accepts "1.0.0".
 */
export type SchemaVersion = "1.0.0";

/**
 * Every document and MCP response of contract 1.0.0.
 */
export interface StaticInferenceContract {
  DependencyGraph: DependencyGraph;
  ConsolidatedArchitecture: ConsolidatedArchitecture;
  EntrypointsDocument: EntrypointsDocument;
  AnalyzeProjectResponse: AnalyzeProjectResponse;
}
/**
 * Dependency graph (output.json; MCP get_dependency_graph). api_contracts.meta is optional (the raw .NET graph does not emit it).
 */
export interface DependencyGraph {
  components: Component[];
  edges: Edge[];
  api_contracts: ApiContracts;
  meta: GraphMeta;
}
/**
 * Unit of analysis (class/interface/enum or .NET type).
 */
export interface Component {
  /**
   * Fully qualified name.
   */
  id: string;
  /**
   * Source files of the component.
   */
  files: string[];
  /**
   * Lines of code.
   */
  loc: number;
  tables_used: string[];
  sensitive_data: boolean;
  secrets_references: string[];
  /**
   * Architectural layer. "Datos" is the historical alias of "Persistencia".
   */
  layer: "Controlador" | "Negocio" | "Datos" | "Persistencia" | "Dominio" | "Transferencia" | "Web" | "Compartida";
  calls_out: string[];
  calls_in: string[];
  annotations: string[];
  implements: string[];
  external_dependencies: string[];
  package_dependencies: PackageGroup[];
  code_issues: CodeIssue[];
  uses_jndi: boolean;
  is_interface: boolean;
  /**
   * @deprecated
   * Deprecated duplicate of is_interface (emitted by the Java engine only).
   */
  interface?: boolean;
  domain?: string | null;
  ejb_type?: string | null;
  extends?: string | null;
  /**
   * Open set of values (jms, MassTransit, ...).
   */
  messaging_type?: string | null;
  /**
   * Open set of values.
   */
  messaging_role?: string | null;
  web_type?: string | null;
  web_role?: string | null;
  /**
   * Coupling Between Objects.
   */
  cbo?: number | null;
  /**
   * Lack of Cohesion in Methods (LCOM-HS, 0 = cohesive).
   */
  lcom?: number | null;
  /**
   * Average cyclomatic complexity.
   */
  complexity_avg?: number | null;
  /**
   * Maximum cyclomatic complexity.
   */
  complexity_max?: number | null;
}
/**
 * Internal package a component depends on.
 */
export interface PackageGroup {
  package_name: string;
  components: string[];
  count: number;
}
/**
 * Static code issue detected in a component.
 */
export interface CodeIssue {
  type: "BUG_PATTERN" | "CODE_STYLE" | "SECURITY" | "PERFORMANCE";
  severity: "INFO" | "WARNING" | "ERROR" | "CRITICAL";
  message: string;
  /**
   * Line number (0 when unknown).
   */
  line: number;
  /**
   * Rule identifier (open set of values).
   */
  pattern: string;
}
/**
 * Dependency between two components.
 */
export interface Edge {
  from: string;
  to: string;
  weight: number;
  /**
   * Edge kind; open set, composite values are comma separated (e.g. call,use,injection-constructor).
   */
  type: string;
}
/**
 * Endpoints and data schemas (output_entrypoints.json, embedded as api_contracts in the graph).
 */
export interface ApiContracts {
  meta?: DocumentMeta;
  endpoints: ApiEndpoint[];
  /**
   * Dynamic map: schema name -> schema.
   */
  schemas: {
    [k: string]: ApiSchema;
  };
}
/**
 * Contract metadata of the consolidated architecture and entrypoints documents.
 */
export interface DocumentMeta {
  schema_version: SchemaVersion;
}
/**
 * Entry point exposed by a component.
 */
export interface ApiEndpoint {
  id: string;
  path: string;
  /**
   * HTTP verb or non-HTTP kind (STRUTS_ACTION, SOAP, LISTENER, ...); open set.
   */
  method: string;
  description?: string | null;
  parameters: Parameter[];
  request_body_schema?: string | null;
  /**
   * Name of the response schema; omitted by the .NET analyzer when null.
   */
  response_schema?: string | null;
  component_id: string;
}
/**
 * Endpoint parameter.
 */
export interface Parameter {
  name: string;
  /**
   * path, query, header, body (open set).
   */
  in: string;
  type: string;
  required: boolean;
}
/**
 * Data schema referenced by endpoints.
 */
export interface ApiSchema {
  type: string;
  /**
   * Dynamic map: property name -> property.
   */
  properties: {
    [k: string]: ApiSchemaProperty;
  };
}
/**
 * Property of a data schema.
 */
export interface ApiSchemaProperty {
  type: string;
}
/**
 * Metadata of the dependency graph.
 */
export interface GraphMeta {
  /**
   * Producer of the graph: Java (Spoon) or .NET (Roslyn, or Roslyn adhoc fallback with reduced fidelity).
   */
  source: "spoon" | "roslyn" | "roslyn-fallback";
  schema_version: SchemaVersion;
  /**
   * Collection timestamp (ISO 8601), only when STATIC_INFERENCE_COLLECTED_AT is enabled.
   */
  collected_at?: string;
  /**
   * Dynamic map: component id -> candidate cluster name.
   */
  microservice_candidates: {
    [k: string]: string;
  };
  /**
   * Optional accuracy report (reserved).
   */
  dependency_accuracy?: {} | null;
  /**
   * Optional accuracy report (reserved).
   */
  decomposition_accuracy?: {} | null;
}
/**
 * Consolidated architecture proposal (output_architecture.json; MCP get_architecture_proposal).
 */
export interface ConsolidatedArchitecture {
  meta: DocumentMeta;
  project_metadata: ProjectMetadata;
  proposals: Proposal[];
  support_libraries: SupportLibrary[];
  summary: string;
}
/**
 * Project-level metadata (null members are omitted).
 */
export interface ProjectMetadata {
  /**
   * Dynamic map: dependency key -> coordinates.
   */
  external_dependencies?: {
    [k: string]: string;
  };
  /**
   * Dynamic map: package -> aggregated dependencies.
   */
  package_dependencies?: {
    [k: string]: PackageDependencyInfo;
  };
  total_components?: number;
  total_loc?: number;
  /**
   * Number of components referencing secrets.
   */
  components_with_secrets?: number;
  shared_domain?: string;
  secrets_locations?: SecretLocation[];
}
/**
 * Aggregated dependencies of a package.
 */
export interface PackageDependencyInfo {
  components_count: number;
  total_dependencies_out: number;
  depends_on_packages: string[];
}
/**
 * Location of a (redacted) secret.
 */
export interface SecretLocation {
  path: string;
  line: number;
  /**
   * Open set (API_KEY, JDBC_PASSWORD, ...).
   */
  kind: string;
  redacted: boolean;
}
/**
 * Microservice extraction proposal.
 */
export interface Proposal {
  id: number;
  name: string;
  viability: "Alta" | "Media" | "Baja";
  clusters: number[];
  components: string[];
  metrics: ProposalMetrics;
  signals: ProposalSignals;
  rationale: string[];
  recommended_actions: string[];
  tables?: ProposalTable[];
  legacy_entrypoint?: LegacyEntrypoint | null;
}
/**
 * Metrics of a microservice proposal.
 */
export interface ProposalMetrics {
  size: number;
  cohesion_avg: number;
  external_coupling: number;
  internal_edge_density: number;
  data_jaccard: number;
  tables: string[];
  sensitive: boolean;
  /**
   * Open set (jpa, orm, sql-parse, mixed, none).
   */
  tables_source?: string;
}
/**
 * Clustering signals of a proposal.
 */
export interface ProposalSignals {
  avg_cluster_size: number;
  cluster_count: number;
  total_components: number;
}
/**
 * Table used by a proposal.
 */
export interface ProposalTable {
  name: string;
  component: string;
  source: string;
}
/**
 * How the legacy system enters the proposal (null members are omitted).
 */
export interface LegacyEntrypoint {
  type: "rest" | "messaging" | "service" | "internal";
  primary_entry_class?: string;
  exposed_operations?: string[];
  messaging_channels?: string[];
  endpoints?: ApiEndpoint[];
  description?: string;
}
/**
 * Shared library candidate.
 */
export interface SupportLibrary {
  id: number;
  name: string;
  clusters: number[];
  components: string[];
}
/**
 * Entrypoints / API contracts document (output_entrypoints.json; MCP get_api_contracts): ApiContracts with meta required.
 */
export interface EntrypointsDocument {
  meta: DocumentMeta;
  endpoints: ApiEndpoint[];
  /**
   * Dynamic map: schema name -> schema.
   */
  schemas: {
    [k: string]: ApiSchema;
  };
}
/**
 * Successful analyze_project response: analysis summary (camelCase, unchanged shape, no meta).
 */
export interface AnalyzeProjectResponse {
  componentCount: number;
  edgeCount: number;
  proposalCount: number;
  endpointCount: number;
  /**
   * Paths of output.json, output_architecture.json and output_entrypoints.json.
   *
   * @minItems 3
   * @maxItems 3
   */
  filesGenerated: string[];
  summary: string;
}

/**
 * Successful get_dependency_graph response: the dependency graph document.
 */
export type GetDependencyGraphResponse = DependencyGraph;

/**
 * Successful get_architecture_proposal response (with or without minViability): the consolidated architecture document.
 */
export type GetArchitectureProposalResponse = ConsolidatedArchitecture;

/**
 * Successful get_api_contracts response: the entrypoints document.
 */
export type GetApiContractsResponse = EntrypointsDocument;

/**
 * Successful get_component_metrics response: a single Component (no meta).
 */
export type GetComponentMetricsResponse = Component;
