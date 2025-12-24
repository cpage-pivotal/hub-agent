# Tanzu Platform Natural Language Interface - Implementation Plan

## Project Overview

**Goal**: Create a natural language interface to the Tanzu Platform GraphQL API using both an MCP server (for API interaction) and a Skill (for domain knowledge).

**Environment Variables Required**:
- `TANZU_PLATFORM_URL`: https://tanzu-hub.kuhn-labs.com
- `TOKEN`: Bearer token for authentication

**API Details**:
- GraphQL endpoint: `/hub/graphql`
- Schema size: 1,382 types (917 objects, 202 inputs, 173 enums, 74 interfaces, 16 scalars)
- Major domains: TAS, Spring, Observability, Security, Capacity, Insights

---

## Spring Boot MCP Server Advantages

**Why Spring Boot + Spring AI?**

1. **Enterprise-Grade Foundation**
   - Production-ready features out of the box
   - Comprehensive monitoring and health checks via Actuator
   - Built-in metrics and observability
   - Configuration management for multiple environments

2. **Streamable HTTP Transport**
   - Better for web deployments and cloud environments
   - No stdio limitations
   - Easier load balancing and scaling
   - Natural fit for containerized deployments

3. **Technology Alignment**
   - Consistent with Tanzu/VMware ecosystem
   - Same stack as many Tanzu applications
   - Easier integration with existing Spring applications
   - Familiar to teams already using Spring

4. **Robust Infrastructure**
   - Type safety and compile-time checking
   - Dependency injection for testability
   - Mature caching solutions (Caffeine, Redis)
   - Excellent WebClient for reactive GraphQL queries

5. **Operational Excellence**
   - Native Kubernetes support
   - Easy deployment to Tanzu Application Service
   - Built-in security features
   - Comprehensive logging and tracing

6. **Development Productivity**
   - Rich IDE support (IntelliJ, VS Code)
   - Extensive testing frameworks
   - Spring Boot DevTools for hot reload
   - Strong community and documentation

---

## Architectural Refinements

Based on analysis of the 1,382-type schema, the following refinements ensure optimal natural language interaction:

### Key Insight: Dual-Component Necessity

Neither MCP server nor skill alone can handle this schema's complexity:
- **MCP Server**: Handles mechanical API interaction but can't embed domain knowledge for 1,382 types in tool descriptions
- **Skill Component**: Provides domain expertise but can't execute queries without tooling

Both components require equal investment.

### Refinement 1: Query Validation Layer

Add client-side query validation before API calls:

```
NL Request → Skill (domain guidance) → Query Builder → Schema Validator → API Call
                                                    ↓
                                            Error Feedback Loop (self-correction)
```

**Benefits**:
- Reduces failed API calls by catching syntax/field errors early
- Provides meaningful error messages for LLM self-correction
- Faster iteration cycles

### Refinement 2: Domain-Partitioned Skill Structure

The skill must be structured by domain to be navigable:

```
skill/
├── SKILL.md                    # Overview, routing, tool reference
├── domains/
│   ├── TAS.md                 # TAS-specific entities, relationships, patterns
│   ├── Spring.md              # Spring application monitoring
│   ├── Observability.md       # Metrics, logs, traces, alerts
│   ├── Security.md            # CVEs, vulnerabilities, compliance
│   └── Capacity.md            # Resource management, recommendations
├── patterns/
│   ├── common-queries.md      # 20+ frequent query templates
│   ├── navigation.md          # Entity relationship traversal
│   ├── pagination.md          # Cursor handling patterns
│   └── filtering.md           # Filter syntax by entity type
└── troubleshooting/
    ├── error-recovery.md      # Common errors and fixes
    └── anti-patterns.md       # What NOT to do
```

### Refinement 3: Enhanced Schema Exploration

With 1,382 types, returning full schema dumps isn't practical. Schema exploration needs:
- **Domain filtering**: Filter by prefix (`Entity_Tanzu_TAS_*`, `Entity_Tanzu_Spring_*`)
- **Relationship-focused exploration**: "How to get from Foundation to Application"
- **Concept search**: Natural language search over type descriptions
- **Field relevance scoring**: Highlight commonly-needed fields

### Refinement 4: Expanded Common Queries

The `tanzu_common_queries` tool should cover the ~20 most frequent operations discovered through usage patterns. Initial coverage expanded from 5 to 20+ patterns.

### Refinement 5: Authentication Resilience

Production-grade token management:
- Token refresh mechanisms
- Graceful 401/403 handling with user-friendly messages
- Token expiration detection and proactive refresh

---

## Phase 1: MCP Server Implementation

### 1.1 Project Setup ✅ IMPLEMENTED

**Technology Stack**: Spring Boot 3.5.9 + Spring AI 1.1.2 MCP Server WebFlux

**Location**: `hub-mcp/`

**Why Spring AI?**
- Native integration with Spring ecosystem
- Streamable-HTTP transport support (better for web deployment)
- Built-in Spring Boot features (security, actuator, configuration)
- Consistent with Tanzu/VMware technology stack
- Easy integration with GraphQL clients

**Implemented Directory Structure**:
```
hub-mcp/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── org/
│   │   │       └── tanzu/
│   │   │           └── hubmcp/
│   │   │               ├── HubMcpApplication.java
│   │   │               ├── config/
│   │   │               │   ├── TanzuPlatformProperties.java  # Type-safe config record
│   │   │               │   ├── CacheConfig.java              # Caffeine cache setup
│   │   │               │   └── GraphQLClientConfig.java      # WebClient configuration
│   │   │               ├── service/
│   │   │               │   └── package-info.java             # Placeholder for services
│   │   │               ├── tools/
│   │   │               │   └── package-info.java             # Placeholder for MCP tools
│   │   │               ├── model/
│   │   │               │   ├── GraphQLRequest.java           # Request record with builder
│   │   │               │   ├── GraphQLResponse.java          # Response record
│   │   │               │   ├── GraphQLError.java             # Error with location
│   │   │               │   ├── SchemaCache.java              # Cached schema container
│   │   │               │   ├── TypeDefinition.java           # GraphQL type definition
│   │   │               │   ├── FieldDefinition.java          # Field within a type
│   │   │               │   ├── TypeReference.java            # Type reference with unwrapping
│   │   │               │   ├── InputValue.java               # Input argument definition
│   │   │               │   ├── EnumValue.java                # Enum value definition
│   │   │               │   └── EntityRelationship.java       # Entity relationship metadata
│   │   │               └── exception/
│   │   │                   ├── GraphQLException.java         # GraphQL operation errors
│   │   │                   ├── SchemaValidationException.java # Validation errors
│   │   │                   └── GlobalExceptionHandler.java   # REST exception handler
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-dev.yml
│   └── test/
│       └── java/
│           └── org/
│               └── tanzu/
│                   └── hubmcp/
│                       └── HubMcpApplicationTests.java
├── pom.xml
├── mvnw
└── mvnw.cmd
```

**Dependencies (pom.xml)** - Implemented:
```xml
<properties>
    <java.version>21</java.version>
    <spring-ai.version>1.1.2</spring-ai.version>
</properties>

<dependencies>
    <!-- Spring Boot Core -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-graphql</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>

    <!-- Spring AI MCP Server -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-server-webflux</artifactId>
    </dependency>

    <!-- Caching -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-cache</artifactId>
    </dependency>
    <dependency>
        <groupId>com.github.ben-manes.caffeine</groupId>
        <artifactId>caffeine</artifactId>
    </dependency>

    <!-- Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- Configuration Properties -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-configuration-processor</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.projectreactor</groupId>
        <artifactId>reactor-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.graphql</groupId>
        <artifactId>spring-graphql-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**Application Configuration (application.yml)** - Implemented:
```yaml
spring:
  application:
    name: hub-mcp

  ai:
    mcp:
      server:
        name: tanzu-platform-mcp
        version: 1.0.0
        sse-message-endpoint: /mcp/messages

tanzu:
  platform:
    url: ${TANZU_PLATFORM_URL:https://tanzu-hub.kuhn-labs.com}
    token: ${TOKEN:}
    graphql:
      endpoint: /hub/graphql
      timeout: 30s
      max-retries: 3

  cache:
    schema:
      ttl: 24h
      max-size: 100

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,caches
  endpoint:
    health:
      show-details: always

logging:
  level:
    org.tanzu.hubmcp: DEBUG
    org.springframework.ai.mcp: DEBUG
```

**Type-Safe Configuration Properties (TanzuPlatformProperties.java)** - Implemented:
```java
@ConfigurationProperties(prefix = "tanzu.platform")
@Validated
public record TanzuPlatformProperties(
        @NotBlank String url,
        @NotBlank String token,
        GraphQLProperties graphql,
        CacheProperties cache
) {
    public record GraphQLProperties(
            String endpoint,
            Duration timeout,
            int maxRetries
    ) {}

    public record CacheProperties(SchemaProperties schema) {}

    public record SchemaProperties(Duration ttl, int maxSize) {}
}
```

**Key Implementation Details**:
- Uses Java 21 with modern record types for all model classes
- Spring AI BOM 1.1.2 for dependency management
- WebClient configured with 16MB buffer for large schema responses
- Caffeine cache with configurable TTL (default 24 hours)
- Global exception handler with structured error responses


### 1.2 Core MCP Tools (Spring AI Implementation)

#### Tool 1: `tanzu_graphql_query`
**Purpose**: Execute read-only GraphQL queries

**Spring AI Tool Implementation**:
```java
@Component
public class TanzuQueryTool {
    
    private final TanzuGraphQLService graphQLService;
    
    @McpTool(
        name = "tanzu_graphql_query",
        description = "Execute read-only GraphQL queries against the Tanzu Platform API"
    )
    public GraphQLResponse executeQuery(
        @McpToolParameter(
            name = "query",
            description = "GraphQL query string",
            required = true
        ) String query,
        
        @McpToolParameter(
            name = "variables",
            description = "Query variables as JSON object"
        ) Map<String, Object> variables,
        
        @McpToolParameter(
            name = "operationName",
            description = "Named operation to execute"
        ) String operationName
    ) {
        GraphQLRequest request = GraphQLRequest.builder()
            .query(query)
            .variables(variables)
            .operationName(operationName)
            .build();
            
        return graphQLService.executeQuery(request);
    }
}
```

**Parameters**:
- `query` (string, required): GraphQL query string
- `variables` (object, optional): Query variables
- `operationName` (string, optional): Named operation

**Returns**:
- JSON response with data or errors
- Query execution metadata (complexity, timing)

**Implementation Notes**:
- Validate query syntax using GraphQL Java parser
- Handle pagination automatically for Connection types
- Cache schema for validation
- Add retry logic with Spring Retry
- Use WebClient for async execution

#### Tool 2: `tanzu_graphql_mutate`
**Purpose**: Execute GraphQL mutations

**Spring AI Tool Implementation**:
```java
@Component
public class TanzuMutateTool {
    
    private final TanzuGraphQLService graphQLService;
    
    @McpTool(
        name = "tanzu_graphql_mutate",
        description = "Execute GraphQL mutations against the Tanzu Platform API"
    )
    public GraphQLResponse executeMutation(
        @McpToolParameter(
            name = "mutation",
            description = "GraphQL mutation string",
            required = true
        ) String mutation,
        
        @McpToolParameter(
            name = "variables",
            description = "Mutation variables as JSON object"
        ) Map<String, Object> variables,
        
        @McpToolParameter(
            name = "operationName",
            description = "Named operation to execute"
        ) String operationName,
        
        @McpToolParameter(
            name = "confirm",
            description = "Safety confirmation for destructive operations",
            required = false
        ) Boolean confirm
    ) {
        if (isDestructive(mutation) && !Boolean.TRUE.equals(confirm)) {
            throw new IllegalArgumentException(
                "This mutation appears to be destructive. Please set confirm=true to proceed."
            );
        }
        
        GraphQLRequest request = GraphQLRequest.builder()
            .query(mutation)
            .variables(variables)
            .operationName(operationName)
            .build();
            
        return graphQLService.executeMutation(request);
    }
    
    private boolean isDestructive(String mutation) {
        return mutation.toLowerCase().contains("delete") ||
               mutation.toLowerCase().contains("remove") ||
               mutation.toLowerCase().contains("destroy");
    }
}
```

**Returns**:
- Mutation result with status
- Any errors or warnings

**Implementation Notes**:
- Require explicit confirmation for DELETE operations
- Log all mutations with Spring AOP for audit trail
- Validate input against schema
- Return detailed error messages
- Support transaction rollback where applicable

#### Tool 3: `tanzu_explore_schema`
**Purpose**: Explore API schema and documentation with intelligent filtering

**Spring AI Tool Implementation**:
```java
@Component
public class TanzuExploreSchemaTool {
    
    private final SchemaIntrospectionService schemaService;
    
    @McpTool(
        name = "tanzu_explore_schema",
        description = """
            Explore the Tanzu Platform GraphQL API schema (1,382 types).
            Use domain filtering to narrow results. Supports concept search.
            """
    )
    public SchemaExplorationResponse exploreSchema(
        @McpToolParameter(
            name = "typeName",
            description = "Specific type to explore (e.g., Entity_Tanzu_TAS_Application)"
        ) String typeName,
        
        @McpToolParameter(
            name = "search",
            description = "Search types/fields by concept (e.g., 'vulnerability', 'application health')"
        ) String search,
        
        @McpToolParameter(
            name = "domain",
            description = "Filter by domain: TAS, Spring, Observability, Security, Capacity, Fleet, Insights"
        ) String domain,
        
        @McpToolParameter(
            name = "category",
            description = "Filter by category: OBJECT, INPUT_OBJECT, ENUM, INTERFACE, SCALAR"
        ) String category,
        
        @McpToolParameter(
            name = "showRelationships",
            description = "Include relationship fields (RelIn/RelOut) for entity types"
        ) Boolean showRelationships,
        
        @McpToolParameter(
            name = "showCommonFields",
            description = "Highlight commonly-used fields based on usage patterns"
        ) Boolean showCommonFields
    ) {
        SchemaExplorationRequest request = SchemaExplorationRequest.builder()
            .typeName(typeName)
            .search(search)
            .domain(domain)
            .category(category)
            .showRelationships(Boolean.TRUE.equals(showRelationships))
            .showCommonFields(Boolean.TRUE.equals(showCommonFields))
            .build();
        
        return schemaService.explore(request);
    }
}
```

**Domain Prefixes** (for filtering):
| Domain | Type Prefix Pattern |
|--------|---------------------|
| TAS | `Entity_Tanzu_TAS_*`, `TAS*` |
| Spring | `Entity_Tanzu_Spring_*`, `Spring*` |
| Observability | `Observability*`, `*Alert*`, `*Metric*` |
| Security | `*Vulnerability*`, `*Security*`, `*CVE*` |
| Capacity | `Capacity*`, `*Recommendation*` |
| Fleet | `FleetManagement*` |
| Insights | `Insight*`, `*Policy*` |

**Returns**:
- Type definitions with fields and descriptions
- Available relationships for entity types (when requested)
- Commonly-used field indicators
- Enum values
- Example query snippets

**Implementation Notes**:
- Cache full schema introspection result using Spring Cache
- Provide formatted, readable output
- Include field deprecation warnings
- Show required vs optional fields
- Parse GraphQL schema definition language
- Rank search results by relevance (description match > name match)
- Limit results to 20 types per request to avoid overwhelming context

#### Tool 4: `tanzu_find_entity_path`
**Purpose**: Find relationship paths between entity types

**Spring AI Tool Implementation**:
```java
@Component
public class TanzuFindEntityPathTool {
    
    private final SchemaIntrospectionService schemaService;
    private final QueryBuilderService queryBuilder;
    
    @McpTool(
        name = "tanzu_find_entity_path",
        description = "Find relationship paths between two entity types in the Tanzu Platform"
    )
    public EntityPathResponse findPath(
        @McpToolParameter(
            name = "fromType",
            description = "Starting entity type (e.g., Entity_Tanzu_TAS_Application)",
            required = true
        ) String fromType,
        
        @McpToolParameter(
            name = "toType",
            description = "Target entity type (e.g., Entity_Tanzu_TAS_Foundation)",
            required = true
        ) String toType,
        
        @McpToolParameter(
            name = "maxDepth",
            description = "Maximum traversal depth (default: 3)"
        ) Integer maxDepth
    ) {
        int depth = maxDepth != null ? maxDepth : 3;
        
        List<EntityPath> paths = schemaService.findRelationshipPaths(
            fromType, toType, depth
        );
        
        List<String> queryTemplates = paths.stream()
            .map(queryBuilder::buildQueryTemplate)
            .collect(Collectors.toList());
            
        return EntityPathResponse.builder()
            .paths(paths)
            .queryTemplates(queryTemplates)
            .build();
    }
}
```

**Returns**:
- Possible relationship paths
- Suggested query structure

**Implementation Notes**:
- Use schema to build entity graph with JGraphT
- Return GraphQL query templates
- Consider common patterns (Foundation â†’ Space â†’ Application)
- Cache path calculations

#### Tool 5: `tanzu_common_queries`
**Purpose**: List and execute common query patterns

**Spring AI Tool Implementation**:
```java
@Component
public class TanzuCommonQueriesTool {
    
    private final TanzuGraphQLService graphQLService;
    private final Map<String, QueryTemplate> templates;
    
    @McpTool(
        name = "tanzu_common_queries",
        description = "Execute pre-built common query patterns"
    )
    public CommonQueryResponse executeCommonQuery(
        @McpToolParameter(
            name = "pattern",
            description = "Query pattern: list_foundations, find_vulnerabilities, get_app_topology, check_capacity, list_alerts",
            required = true
        ) String pattern,
        
        @McpToolParameter(
            name = "parameters",
            description = "Pattern-specific parameters as JSON object"
        ) Map<String, Object> parameters
    ) {
        QueryTemplate template = templates.get(pattern);
        if (template == null) {
            throw new IllegalArgumentException("Unknown pattern: " + pattern);
        }
        
        String query = template.render(parameters);
        GraphQLResponse response = graphQLService.executeQuery(
            GraphQLRequest.builder()
                .query(query)
                .variables(parameters)
                .build()
        );
        
        return CommonQueryResponse.builder()
            .pattern(pattern)
            .queryUsed(query)
            .result(response.getData())
            .build();
    }
    
    @PostConstruct
    public void initializeTemplates() {
        templates.put("list_foundations", new ListFoundationsTemplate());
        templates.put("find_vulnerabilities", new FindVulnerabilitiesTemplate());
        templates.put("get_app_topology", new GetAppTopologyTemplate());
        templates.put("check_capacity", new CheckCapacityTemplate());
        templates.put("list_alerts", new ListAlertsTemplate());
    }
}
```

**Common Patterns**:
- `list_foundations` - List all foundations
- `find_vulnerabilities` - Find vulnerable artifacts
- `get_app_topology` - Get application topology
- `check_capacity` - Check capacity recommendations
- `list_alerts` - List active alerts
- `get_app_health` - Application health summary
- `find_spring_apps` - List Spring Boot applications
- `get_org_summary` - Organization overview with spaces
- `list_deployments` - Recent deployment activity
- `find_cves_by_severity` - CVEs filtered by severity
- `get_foundation_metrics` - Foundation resource utilization
- `list_spaces_by_org` - Spaces within an organization
- `find_stopped_apps` - Applications not running
- `get_artifact_sbom` - Software bill of materials
- `list_notification_targets` - Configured notification endpoints
- `get_policy_violations` - Policy compliance issues
- `find_apps_by_buildpack` - Applications by buildpack type
- `get_service_bindings` - Service instance bindings
- `list_management_endpoints` - Registered management endpoints
- `get_insights_summary` - Platform insights overview

#### Tool 6: `tanzu_validate_query`
**Purpose**: Validate GraphQL query syntax and fields before execution

**Spring AI Tool Implementation**:
```java
@Component
public class TanzuValidateQueryTool {
    
    private final SchemaIntrospectionService schemaService;
    private final GraphQLQueryValidator validator;
    
    @McpTool(
        name = "tanzu_validate_query",
        description = """
            Validate a GraphQL query against the Tanzu Platform schema without executing it.
            Returns syntax errors, unknown field errors, and suggestions for fixes.
            Use this before tanzu_graphql_query to catch errors early.
            """
    )
    public QueryValidationResponse validateQuery(
        @McpToolParameter(
            name = "query",
            description = "GraphQL query string to validate",
            required = true
        ) String query,
        
        @McpToolParameter(
            name = "variables",
            description = "Query variables to validate types"
        ) Map<String, Object> variables,
        
        @McpToolParameter(
            name = "suggestFixes",
            description = "Attempt to suggest corrections for errors (default: true)"
        ) Boolean suggestFixes
    ) {
        boolean suggest = suggestFixes == null || suggestFixes;
        
        ValidationResult result = validator.validate(query, variables, schemaService.getSchema());
        
        if (result.isValid()) {
            return QueryValidationResponse.builder()
                .valid(true)
                .message("Query is valid and ready to execute")
                .estimatedComplexity(result.getEstimatedComplexity())
                .fieldsRequested(result.getFieldCount())
                .build();
        }
        
        QueryValidationResponse.Builder response = QueryValidationResponse.builder()
            .valid(false)
            .errors(result.getErrors());
        
        if (suggest) {
            response.suggestions(generateSuggestions(result.getErrors()));
        }
        
        return response.build();
    }
    
    private List<String> generateSuggestions(List<ValidationError> errors) {
        return errors.stream()
            .map(this::suggestFix)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    private String suggestFix(ValidationError error) {
        if (error.getType() == ErrorType.UNKNOWN_FIELD) {
            String field = error.getFieldName();
            String parentType = error.getParentType();
            List<String> similar = schemaService.findSimilarFields(parentType, field);
            if (!similar.isEmpty()) {
                return String.format("Unknown field '%s' on type '%s'. Did you mean: %s?",
                    field, parentType, String.join(", ", similar));
            }
        }
        if (error.getType() == ErrorType.UNKNOWN_TYPE) {
            String typeName = error.getTypeName();
            List<String> similar = schemaService.findSimilarTypes(typeName);
            if (!similar.isEmpty()) {
                return String.format("Unknown type '%s'. Did you mean: %s?",
                    typeName, String.join(", ", similar));
            }
        }
        return null;
    }
}
```

**Returns**:
```json
{
  "valid": false,
  "errors": [
    {
      "type": "UNKNOWN_FIELD",
      "message": "Field 'nme' not found on type 'Entity_Tanzu_TAS_Application_Properties'",
      "location": { "line": 5, "column": 12 }
    }
  ],
  "suggestions": [
    "Unknown field 'nme' on type 'Entity_Tanzu_TAS_Application_Properties'. Did you mean: name?"
  ],
  "estimatedComplexity": null
}
```

**Implementation Notes**:
- Use graphql-java Parser and Validator
- Cache schema for fast validation
- Use Levenshtein distance for "did you mean" suggestions
- Estimate query complexity before execution
- Validate variable types match expected input types

### 1.3 GraphQL Client Implementation

**Key Service Class**:
```java
@Service
@Slf4j
public class TanzuGraphQLService {
    
    private final WebClient webClient;
    private final SchemaIntrospectionService schemaService;
    private final ObjectMapper objectMapper;
    
    @Value("${tanzu.platform.graphql.timeout:30s}")
    private Duration timeout;
    
    @Value("${tanzu.platform.graphql.max-retries:3}")
    private int maxRetries;
    
    public TanzuGraphQLService(
        WebClient.Builder webClientBuilder,
        @Value("${tanzu.platform.url}") String baseUrl,
        @Value("${tanzu.platform.token}") String token
    ) {
        this.webClient = webClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
    
    public GraphQLResponse executeQuery(GraphQLRequest request) {
        validateQuery(request.getQuery());
        return executeGraphQLRequest(request);
    }
    
    public GraphQLResponse executeMutation(GraphQLRequest request) {
        validateMutation(request.getQuery());
        return executeGraphQLRequest(request);
    }
    
    @Cacheable(value = "graphql-schema", unless = "#result == null")
    public Map<String, Object> introspectSchema() {
        String introspectionQuery = """
            query IntrospectionQuery {
              __schema {
                queryType { name }
                mutationType { name }
                subscriptionType { name }
                types {
                  ...FullType
                }
                directives {
                  name
                  description
                  locations
                  args {
                    ...InputValue
                  }
                }
              }
            }
            
            fragment FullType on __Type {
              kind
              name
              description
              fields(includeDeprecated: true) {
                name
                description
                args {
                  ...InputValue
                }
                type {
                  ...TypeRef
                }
                isDeprecated
                deprecationReason
              }
              inputFields {
                ...InputValue
              }
              interfaces {
                ...TypeRef
              }
              enumValues(includeDeprecated: true) {
                name
                description
                isDeprecated
                deprecationReason
              }
              possibleTypes {
                ...TypeRef
              }
            }
            
            fragment InputValue on __InputValue {
              name
              description
              type { ...TypeRef }
              defaultValue
            }
            
            fragment TypeRef on __Type {
              kind
              name
              ofType {
                kind
                name
                ofType {
                  kind
                  name
                  ofType {
                    kind
                    name
                    ofType {
                      kind
                      name
                      ofType {
                        kind
                        name
                        ofType {
                          kind
                          name
                        }
                      }
                    }
                  }
                }
              }
            }
            """;
        
        GraphQLRequest request = GraphQLRequest.builder()
            .query(introspectionQuery)
            .build();
            
        GraphQLResponse response = executeGraphQLRequest(request);
        return response.getData();
    }
    
    private GraphQLResponse executeGraphQLRequest(GraphQLRequest request) {
        return webClient.post()
            .uri("/hub/graphql")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(GraphQLResponse.class)
            .timeout(timeout)
            .retryWhen(Retry.backoff(maxRetries, Duration.ofSeconds(1))
                .filter(throwable -> throwable instanceof WebClientException)
                .doBeforeRetry(signal -> 
                    log.warn("Retrying GraphQL request, attempt {}", signal.totalRetries() + 1)
                ))
            .doOnError(error -> 
                log.error("GraphQL request failed: {}", error.getMessage(), error)
            )
            .block();
    }
    
    private void validateQuery(String query) {
        try {
            // Use graphql-java parser to validate syntax
            Document document = Parser.parse(query);
            
            // Additional validation against schema
            if (schemaService.isSchemaLoaded()) {
                schemaService.validateAgainstSchema(document);
            }
        } catch (Exception e) {
            throw new GraphQLException("Invalid query syntax: " + e.getMessage(), e);
        }
    }
    
    private void validateMutation(String mutation) {
        if (!mutation.trim().toLowerCase().startsWith("mutation")) {
            throw new GraphQLException("Mutation must start with 'mutation' keyword");
        }
        validateQuery(mutation);
    }
}
```

**Model Classes**:
```java
@Data
@Builder
public class GraphQLRequest {
    private String query;
    private Map<String, Object> variables;
    private String operationName;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphQLResponse {
    private Map<String, Object> data;
    private List<GraphQLError> errors;
    private Map<String, Object> extensions;
    
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
    
    public int getQueryComplexity() {
        if (extensions != null && extensions.containsKey("queryComplexity")) {
            return (int) extensions.get("queryComplexity");
        }
        return 0;
    }
}

@Data
public class GraphQLError {
    private String message;
    private List<Location> locations;
    private List<String> path;
    private Map<String, Object> extensions;
}

@Data
public class Location {
    private int line;
    private int column;
}
```

**Error Handling**:
```java
@ControllerAdvice
public class GraphQLExceptionHandler {
    
    @ExceptionHandler(GraphQLException.class)
    public ResponseEntity<ErrorResponse> handleGraphQLException(GraphQLException ex) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse(
                "GRAPHQL_ERROR",
                ex.getMessage(),
                ex.getDetails()
            ));
    }
    
    @ExceptionHandler(WebClientException.class)
    public ResponseEntity<ErrorResponse> handleWebClientException(WebClientException ex) {
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ErrorResponse(
                "TANZU_API_UNAVAILABLE",
                "Unable to connect to Tanzu Platform API",
                Map.of("cause", ex.getMessage())
            ));
    }
    
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ErrorResponse> handleTimeout(TimeoutException ex) {
        return ResponseEntity
            .status(HttpStatus.GATEWAY_TIMEOUT)
            .body(new ErrorResponse(
                "QUERY_TIMEOUT",
                "GraphQL query exceeded timeout limit",
                Map.of("timeout", "30s")
            ));
    }
}
```

**Configuration**:
```java
@Configuration
public class GraphQLClientConfig {
    
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
            .codecs(configurer -> configurer
                .defaultCodecs()
                .maxInMemorySize(16 * 1024 * 1024) // 16MB
            )
            .filter(ExchangeFilterFunction.ofRequestProcessor(
                request -> {
                    log.debug("GraphQL Request: {} {}", request.method(), request.url());
                    return Mono.just(request);
                }
            ))
            .filter(ExchangeFilterFunction.ofResponseProcessor(
                response -> {
                    log.debug("GraphQL Response: {}", response.statusCode());
                    return Mono.just(response);
                }
            ));
    }
    
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
```

### 1.4 Schema Caching Strategy

**Spring Cache Configuration**:
```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            "graphql-schema",
            "entity-relationships",
            "type-definitions"
        );
        
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(24))
            .maximumSize(100)
            .recordStats()
        );
        
        return cacheManager;
    }
}
```

**Schema Introspection Service**:
```java
@Service
@Slf4j
public class SchemaIntrospectionService {
    
    private final TanzuGraphQLService graphQLService;
    private final CacheManager cacheManager;
    
    @Cacheable(value = "graphql-schema")
    public SchemaCache getSchema() {
        log.info("Loading schema from Tanzu Platform API");
        Map<String, Object> schemaData = graphQLService.introspectSchema();
        return parseSchema(schemaData);
    }
    
    @Cacheable(value = "type-definitions", key = "#typeName")
    public TypeDefinition getTypeDetails(String typeName) {
        SchemaCache schema = getSchema();
        return schema.getType(typeName)
            .orElseThrow(() -> new IllegalArgumentException("Type not found: " + typeName));
    }
    
    @Cacheable(value = "entity-relationships")
    public Map<String, List<EntityRelationship>> getEntityRelationships() {
        SchemaCache schema = getSchema();
        return buildRelationshipGraph(schema);
    }
    
    public void refreshSchema() {
        log.info("Refreshing schema cache");
        cacheManager.getCache("graphql-schema").clear();
        cacheManager.getCache("entity-relationships").clear();
        cacheManager.getCache("type-definitions").clear();
        getSchema(); // Reload
    }
    
    private SchemaCache parseSchema(Map<String, Object> schemaData) {
        // Parse introspection result into structured format
        SchemaCache cache = new SchemaCache();
        cache.setTimestamp(Instant.now());
        
        Map<String, Object> schema = (Map<String, Object>) schemaData.get("__schema");
        List<Map<String, Object>> types = (List<Map<String, Object>>) schema.get("types");
        
        for (Map<String, Object> typeData : types) {
            TypeDefinition type = parseType(typeData);
            cache.addType(type);
        }
        
        return cache;
    }
    
    private Map<String, List<EntityRelationship>> buildRelationshipGraph(SchemaCache schema) {
        Map<String, List<EntityRelationship>> graph = new HashMap<>();
        
        // Find all entity types (prefixed with Entity_Tanzu_)
        List<TypeDefinition> entityTypes = schema.getTypes().stream()
            .filter(t -> t.getName().startsWith("Entity_Tanzu_"))
            .collect(Collectors.toList());
        
        for (TypeDefinition entity : entityTypes) {
            List<EntityRelationship> relationships = new ArrayList<>();
            
            // Find relationship fields (ending with _RelIn or _RelOut)
            for (FieldDefinition field : entity.getFields()) {
                if (field.getName().endsWith("_RelIn") || field.getName().endsWith("_RelOut")) {
                    EntityRelationship rel = parseRelationship(entity, field);
                    relationships.add(rel);
                }
            }
            
            graph.put(entity.getName(), relationships);
        }
        
        return graph;
    }
}
```

**Cache Structure Models**:
```java
@Data
public class SchemaCache {
    private Instant timestamp;
    private Map<String, TypeDefinition> types = new HashMap<>();
    private Map<String, List<EntityRelationship>> relationships = new HashMap<>();
    
    public void addType(TypeDefinition type) {
        types.put(type.getName(), type);
    }
    
    public Optional<TypeDefinition> getType(String name) {
        return Optional.ofNullable(types.get(name));
    }
    
    public List<TypeDefinition> getTypes() {
        return new ArrayList<>(types.values());
    }
    
    public List<TypeDefinition> getTypesByKind(String kind) {
        return types.values().stream()
            .filter(t -> t.getKind().equals(kind))
            .collect(Collectors.toList());
    }
}

@Data
@Builder
public class TypeDefinition {
    private String name;
    private String kind; // OBJECT, INPUT_OBJECT, ENUM, INTERFACE, SCALAR
    private String description;
    private List<FieldDefinition> fields;
    private List<String> interfaces;
    private List<EnumValue> enumValues;
}

@Data
@Builder
public class FieldDefinition {
    private String name;
    private String description;
    private TypeReference type;
    private List<InputValue> args;
    private boolean deprecated;
    private String deprecationReason;
}

@Data
@Builder
public class EntityRelationship {
    private String sourceEntity;
    private String targetEntity;
    private String relationshipType; // IsContainedIn, Contains, IsAssociatedWith, etc.
    private String direction; // IN or OUT
    private String fieldName;
}
```

**Scheduled Cache Refresh**:
```java
@Component
@EnableScheduling
public class SchemaCacheRefreshTask {
    
    private final SchemaIntrospectionService schemaService;
    
    @Scheduled(cron = "${tanzu.cache.schema.refresh-cron:0 0 2 * * ?}") // 2 AM daily
    public void refreshSchemaCache() {
        schemaService.refreshSchema();
    }
}
```

### 1.5 Testing Strategy

**Unit Tests with JUnit 5 and Mockito**:
```java
@ExtendWith(MockitoExtension.class)
class TanzuQueryToolTest {
    
    @Mock
    private TanzuGraphQLService graphQLService;
    
    @InjectMocks
    private TanzuQueryTool queryTool;
    
    @Test
    void shouldExecuteValidQuery() {
        // Given
        String query = "query { entityQuery { Entity_Tanzu_TAS_Foundation { edges { node { id } } } } }";
        GraphQLResponse expectedResponse = GraphQLResponse.builder()
            .data(Map.of("entityQuery", Map.of()))
            .build();
        
        when(graphQLService.executeQuery(any(GraphQLRequest.class)))
            .thenReturn(expectedResponse);
        
        // When
        GraphQLResponse response = queryTool.executeQuery(query, null, null);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.hasErrors()).isFalse();
        verify(graphQLService).executeQuery(any(GraphQLRequest.class));
    }
    
    @Test
    void shouldHandleGraphQLErrors() {
        // Given
        String query = "invalid query";
        
        when(graphQLService.executeQuery(any(GraphQLRequest.class)))
            .thenThrow(new GraphQLException("Invalid syntax"));
        
        // Then
        assertThrows(GraphQLException.class, () -> 
            queryTool.executeQuery(query, null, null)
        );
    }
}
```

**Integration Tests with Spring Boot Test**:
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "tanzu.platform.url=https://tanzu-hub.kuhn-labs.com",
    "tanzu.platform.token=${TEST_TOKEN}"
})
class TanzuMcpServerIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private TanzuGraphQLService graphQLService;
    
    @Test
    void shouldExecuteRealQuery() {
        // Given
        String query = """
            query {
              entityQuery {
                Entity_Tanzu_TAS_Foundation(first: 5) {
                  edges {
                    node {
                      id
                      properties {
                        name
                      }
                    }
                  }
                }
              }
            }
            """;
        
        GraphQLRequest request = GraphQLRequest.builder()
            .query(query)
            .build();
        
        // When
        GraphQLResponse response = graphQLService.executeQuery(request);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.hasErrors()).isFalse();
        assertThat(response.getData()).isNotEmpty();
    }
    
    @Test
    void shouldHandlePagination() {
        // Test pagination handling
    }
    
    @Test
    void shouldCacheSchemaIntrospection() {
        // Test schema caching
    }
}
```

**MCP Tool Integration Tests**:
```java
@SpringBootTest
@Import(McpServerAutoConfiguration.class)
class McpToolsIntegrationTest {
    
    @Autowired
    private TanzuQueryTool queryTool;
    
    @Autowired
    private TanzuExploreSchemaTool exploreTool;
    
    @Test
    void shouldExecuteToolViaSpringAI() {
        // Test MCP tool execution through Spring AI
        GraphQLResponse response = queryTool.executeQuery(
            "query { entityQuery { Entity_Tanzu_TAS_Foundation { edges { node { id } } } } }",
            null,
            null
        );
        
        assertThat(response).isNotNull();
    }
    
    @Test
    void shouldExploreSchema() {
        SchemaExplorationResponse response = exploreTool.exploreSchema(
            "Entity_Tanzu_TAS_Application",
            null,
            null
        );
        
        assertThat(response).isNotNull();
        assertThat(response.getType()).isNotNull();
    }
}
```

**WebClient Mock Tests**:
```java
@WebFluxTest
class TanzuGraphQLServiceTest {
    
    @Autowired
    private WebClient.Builder webClientBuilder;
    
    private MockWebServer mockWebServer;
    private TanzuGraphQLService service;
    
    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        String baseUrl = mockWebServer.url("/").toString();
        service = new TanzuGraphQLService(
            webClientBuilder,
            baseUrl,
            "test-token"
        );
    }
    
    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }
    
    @Test
    void shouldRetryOnFailure() throws InterruptedException {
        // Given: First request fails, second succeeds
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"data\": {\"test\": \"value\"}}")
            .setHeader("Content-Type", "application/json")
        );
        
        // When
        GraphQLResponse response = service.executeQuery(
            GraphQLRequest.builder()
                .query("query { test }")
                .build()
        );
        
        // Then
        assertThat(response.getData()).containsKey("test");
        assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
    }
}
```

**Test Fixtures**:
```java
@TestConfiguration
public class TestDataFixtures {
    
    @Bean
    public GraphQLResponse sampleFoundationResponse() {
        return GraphQLResponse.builder()
            .data(Map.of(
                "entityQuery", Map.of(
                    "Entity_Tanzu_TAS_Foundation", Map.of(
                        "edges", List.of(
                            Map.of("node", Map.of(
                                "id", "foundation-1",
                                "properties", Map.of("name", "prod-foundation")
                            ))
                        )
                    )
                )
            ))
            .build();
    }
    
    @Bean
    public SchemaCache sampleSchemaCache() {
        SchemaCache cache = new SchemaCache();
        cache.setTimestamp(Instant.now());
        
        TypeDefinition foundationType = TypeDefinition.builder()
            .name("Entity_Tanzu_TAS_Foundation")
            .kind("OBJECT")
            .description("TAS Foundation entity")
            .fields(List.of(
                FieldDefinition.builder()
                    .name("id")
                    .type(TypeReference.builder().name("ID").build())
                    .build()
            ))
            .build();
        
        cache.addType(foundationType);
        return cache;
    }
}
```

**Performance Tests**:
```java
@SpringBootTest
class PerformanceTest {
    
    @Autowired
    private TanzuGraphQLService graphQLService;
    
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void shouldCompleteQueryWithin5Seconds() {
        String query = "query { entityQuery { Entity_Tanzu_TAS_Foundation { edges { node { id } } } } }";
        
        GraphQLResponse response = graphQLService.executeQuery(
            GraphQLRequest.builder().query(query).build()
        );
        
        assertThat(response).isNotNull();
    }
    
    @Test
    void shouldHandleMultipleConcurrentRequests() throws InterruptedException {
        int concurrentRequests = 10;
        CountDownLatch latch = new CountDownLatch(concurrentRequests);
        
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        
        for (int i = 0; i < concurrentRequests; i++) {
            executor.submit(() -> {
                try {
                    graphQLService.executeQuery(
                        GraphQLRequest.builder()
                            .query("query { entityQuery { Entity_Tanzu_TAS_Foundation { edges { node { id } } } } }")
                            .build()
                    );
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
    }
}
```

---

## Phase 2: Skill Implementation (Expanded)

> **Critical Note**: The skill component requires investment equal to or greater than the MCP server. It provides the domain knowledge that enables effective natural language interaction with a 1,382-type schema. Underinvesting here will result in poor query construction and frequent errors.

### 2.1 Skill Architecture

**Location**: `/mnt/skills/user/tanzu-platform/`

**Directory Structure**:
```
tanzu-platform/
├── SKILL.md                         # Main entry point, routing, tool reference
├── domains/
│   ├── TAS.md                       # Tanzu Application Service entities
│   ├── Spring.md                    # Spring application monitoring
│   ├── Observability.md             # Metrics, logs, alerts, traces
│   ├── Security.md                  # Vulnerabilities, CVEs, compliance
│   └── Capacity.md                  # Resource management, recommendations
├── patterns/
│   ├── common-queries.md            # 20+ frequent query templates
│   ├── entity-navigation.md         # Relationship traversal patterns
│   ├── pagination.md                # Cursor-based pagination handling
│   ├── filtering.md                 # Filter syntax by entity type
│   └── mutations.md                 # Safe mutation patterns
├── reference/
│   ├── entity-hierarchy.md          # Visual entity tree with relationships
│   ├── type-naming.md               # Understanding type naming conventions
│   └── api-stability.md             # Alpha/Beta/GA API notes
└── troubleshooting/
    ├── error-recovery.md            # Common errors and how to fix
    ├── anti-patterns.md             # Query patterns to avoid
    └── performance.md               # Keeping queries efficient
```

### 2.2 Main Skill Entry Point (SKILL.md)

```markdown
# Tanzu Platform Natural Language Interface Skill

## Purpose
This skill provides domain knowledge for constructing effective GraphQL queries 
against the Tanzu Platform API (1,382 types across 6+ domains).

## When to Use This Skill
Read this skill BEFORE using any Tanzu MCP tools when:
- Constructing queries for Tanzu Platform entities
- Navigating entity relationships
- Finding vulnerabilities or security information
- Managing infrastructure and capacity
- Setting up monitoring and alerts
- Troubleshooting query errors

## Available MCP Tools
| Tool | Purpose | When to Use |
|------|---------|-------------|
| `tanzu_validate_query` | Validate query syntax | Before executing any query |
| `tanzu_graphql_query` | Execute read queries | Fetching data |
| `tanzu_graphql_mutate` | Execute mutations | Creating/updating/deleting |
| `tanzu_explore_schema` | Discover schema | Finding types and fields |
| `tanzu_find_entity_path` | Navigate relationships | Connecting entities |
| `tanzu_common_queries` | Pre-built patterns | Common operations |

## Query Construction Workflow
1. **Identify the domain** → Read relevant `domains/*.md`
2. **Understand the entities** → Use `tanzu_explore_schema` with domain filter
3. **Plan the navigation** → Use `tanzu_find_entity_path` if crossing entities
4. **Construct the query** → Follow patterns in `patterns/*.md`
5. **Validate before executing** → Use `tanzu_validate_query`
6. **Execute and handle errors** → Use `tanzu_graphql_query`

## Domain Quick Reference
| Domain | Key Entities | File |
|--------|--------------|------|
| TAS | Foundation, Organization, Space, Application | `domains/TAS.md` |
| Spring | SpringArtifact, Dependency, Runtime | `domains/Spring.md` |
| Observability | Alert, Metric, Log, NotificationTarget | `domains/Observability.md` |
| Security | Vulnerability, CVE, Insight, Policy | `domains/Security.md` |
| Capacity | CapacityInfo, Recommendation | `domains/Capacity.md` |

## Critical Rules
1. **Always validate queries** before execution using `tanzu_validate_query`
2. **Request only needed fields** to avoid complexity limits
3. **Handle pagination** - use cursor-based patterns from `patterns/pagination.md`
4. **Check relationship direction** - `RelIn` vs `RelOut` matters
5. **Use domain filtering** in `tanzu_explore_schema` to narrow 1,382 types
```

### 2.3 Domain Skill Content

The following files provide domain-specific knowledge:

**Entity Hierarchy**:
```
Platform
â”œâ”€â”€ Foundation Groups
â”‚   â””â”€â”€ Foundations (TAS)
â”‚       â”œâ”€â”€ Organizations
â”‚       â”‚   â””â”€â”€ Spaces
â”‚       â”‚       â””â”€â”€ Applications
â”‚       â”œâ”€â”€ BOSH Directors
â”‚       â””â”€â”€ Ops Managers
â””â”€â”€ Organization Groups
    â””â”€â”€ Space Groups
```

**Key Domains**:
- **TAS (Tanzu Application Service)**: CF applications and infrastructure
- **Spring**: Spring Boot artifacts and dependencies
- **Observability**: Metrics, logs, alerts
- **Security**: Vulnerabilities, assessments, policies
- **Capacity**: Resource management and recommendations

#### Section 3: Entity Model Guide

**Connection Pattern**:
All entity lists use GraphQL Relay-style connections:
```graphql
query {
  entities {
    edges {
      node {
        id
        # entity fields
      }
    }
    pageInfo {
      hasNextPage
      endCursor
    }
  }
}
```

**Common Entity Types**:

1. **Foundation-Level Entities**
   - `Entity_Tanzu_TAS_Foundation`
   - `Entity_Tanzu_TAS_Organization`
   - `Entity_Tanzu_TAS_Space`
   - `Entity_Tanzu_TAS_Application`

2. **Artifact Entities**
   - `ArtifactMetadata`
   - `ArtifactSBOM`
   - `ArtifactVulnerability`
   - `SpringArtifactMetadata`

3. **Infrastructure Entities**
   - `ManagementEndpoint`
   - `ManagementEndpointCollector`
   - `CapacityInfo`

4. **Security Entities**
   - `ArtifactVulnerabilityEntityDetails`
   - `Insight`
   - `TanzuHubPolicy`

#### Section 4: Common Workflows

**Workflow 1: Finding All Applications in a Foundation**
```markdown
### Steps:
1. Query for foundations
2. Navigate to organizations within foundation
3. List spaces within organizations
4. Query applications within spaces

### Example Query Pattern:
```graphql
query FindApplications($foundationId: ID!) {
  entityQuery {
    Entity_Tanzu_TAS_Foundation(id: $foundationId) {
      properties {
        name
      }
      IsContainedIn_RelOut {
        edges {
          node {
            ... on Entity_Tanzu_TAS_Organization {
              properties { name }
              IsContainedIn_RelOut {
                edges {
                  node {
                    ... on Entity_Tanzu_TAS_Space {
                      properties { name }
                      IsContainedIn_RelOut {
                        edges {
                          node {
                            ... on Entity_Tanzu_TAS_Application {
                              properties {
                                name
                                state
                                instances
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
```
```

**Workflow 2: Finding Vulnerable Artifacts**
```markdown
### Query Pattern:
```graphql
query FindVulnerabilities($severity: ArtifactVulnerabilitySeverity) {
  artifactVulnerabilityQuery {
    vulnerabilities(
      filter: {
        severity: $severity
        triageStatus: OPEN
      }
      first: 50
    ) {
      edges {
        node {
          id
          cveId
          severity
          score {
            value
            type
          }
          affectedArtifacts {
            edges {
              node {
                name
                version
              }
            }
          }
        }
      }
    }
  }
}
```
```

**Workflow 3: Setting Up Alerts**
```markdown
### Steps:
1. Create or identify notification target
2. Define alert condition (metric or log-based)
3. Configure notification rule
4. Test alert

### Example: Create Metric Alert
```graphql
mutation CreateMetricAlert($input: ObservabilityMetricAlertCreateInput!) {
  observabilityAlertMutationProvider {
    createMetricAlert(input: $input) {
      id
      name
      status
    }
  }
}
```
```

#### Section 5: Query Construction Best Practices

**1. Always Use Fragments for Repeated Structures**
```graphql
fragment AppDetails on Entity_Tanzu_TAS_Application {
  id
  properties {
    name
    state
    instances
    memory
  }
}

query {
  entityQuery {
    Entity_Tanzu_TAS_Application(first: 10) {
      edges {
        node {
          ...AppDetails
        }
      }
    }
  }
}
```

**2. Request Only Needed Fields**
- Reduces response size
- Improves performance
- Lowers query complexity

**3. Use Filters to Narrow Results**
```graphql
query FilteredApps {
  entityQuery {
    Entity_Tanzu_TAS_Application(
      filter: {
        property: "state"
        value: "STARTED"
      }
    ) {
      edges {
        node {
          properties { name }
        }
      }
    }
  }
}
```

**4. Handle Pagination**
```graphql
query PaginatedQuery($after: String) {
  entities(first: 20, after: $after) {
    edges {
      node { id }
    }
    pageInfo {
      hasNextPage
      endCursor
    }
  }
}
```

#### Section 6: Entity Relationship Navigation

**Relationship Types**:
- `IsContainedIn`: Parent-child relationships (Space â†’ Organization)
- `Contains`: Child-parent relationships (Organization â†’ Spaces)
- `IsAssociatedWith`: Peer relationships (Application â†’ Service Instance)
- `IsDeployedBy`: Deployment relationships (Foundation â†’ Ops Manager)

**Navigation Pattern**:
```graphql
# From Application to Foundation (traversing up)
Entity_Tanzu_TAS_Application {
  IsContainedIn_RelIn {  # to Space
    edges {
      node {
        ... on Entity_Tanzu_TAS_Space {
          IsContainedIn_RelIn {  # to Organization
            edges {
              node {
                ... on Entity_Tanzu_TAS_Organization {
                  IsContainedIn_RelIn {  # to Foundation
                    edges {
                      node {
                        ... on Entity_Tanzu_TAS_Foundation {
                          properties { name }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
```

#### Section 7: Common Pitfalls

**1. Query Complexity Limits**
- API returns `queryComplexity` in extensions
- Keep complexity under threshold
- Break large queries into smaller ones

**2. Type Confusion**
- TAS entities vs Spring entities are different
- Use correct type names (Entity_Tanzu_TAS_Application vs Entity_Tanzu_Spring_Application)

**3. Relationship Direction**
- `RelIn`: Incoming relationships (parent â†’ child)
- `RelOut`: Outgoing relationships (child â†’ parent)

**4. Filter Syntax**
- Different entities support different filters
- Use `tanzu_explore_schema` to check available filters

#### Section 8: Quick Reference

**Get All Foundations**:
```graphql
query { entityQuery { Entity_Tanzu_TAS_Foundation { edges { node { id properties { name } } } } } }
```

**Get Critical Vulnerabilities**:
```graphql
query {
  artifactVulnerabilityQuery {
    vulnerabilities(filter: { severity: CRITICAL }) {
      edges {
        node {
          cveId
          severity
        }
      }
    }
  }
}
```

**Get Active Alerts**:
```graphql
query {
  observabilityAlertQueryProvider {
    alerts(filter: { status: FIRING }) {
      edges {
        node {
          name
          severity
          status
        }
      }
    }
  }
}
```

**Check Capacity Recommendations**:
```graphql
query {
  capacityQuery {
    recommendations {
      edges {
        node {
          ... on CapacityOptimizeAction {
            classification
            description
          }
        }
      }
    }
  }
}
```

### 2.3 Skill Maintenance

**Update Schedule**:
- Review after API schema changes
- Add new workflows as discovered
- Incorporate user feedback
- Update examples based on actual usage

**Version Control**:
- Track in git
- Document changes
- Link to API version

---

## Phase 3: Integration & Testing

### 3.1 MCP Server + Skill Integration

**Claude's Workflow**:
1. User asks question in natural language
2. Claude reads skill to understand domain
3. Claude uses MCP tools to interact with API
4. Claude formats results naturally

**Example User Request**: "Show me all applications with critical vulnerabilities"

**Claude's Process**:
1. **Read skill** â†’ Learn about vulnerability queries
2. **Use `tanzu_explore_schema`** â†’ Confirm available filters
3. **Use `tanzu_graphql_query`** â†’ Execute vulnerability query
4. **Parse results** â†’ Extract and format for user

### 3.2 Testing Scenarios

**Scenario 1: Simple Query**
- Request: "List all foundations"
- Expected: Claude uses `tanzu_common_queries` or constructs basic query
- Validation: Returns valid foundation list

**Scenario 2: Complex Navigation**
- Request: "Find all applications in the 'production' foundation"
- Expected: Claude navigates Foundation â†’ Org â†’ Space â†’ Application
- Validation: Correct entity traversal

**Scenario 3: Security Analysis**
- Request: "What are my highest priority vulnerabilities?"
- Expected: Query vulnerabilities filtered by severity, sort by score
- Validation: Actionable results with context

**Scenario 4: Mutation**
- Request: "Create a policy to alert on critical CVEs"
- Expected: Claude constructs mutation with confirmation
- Validation: Policy created successfully

**Scenario 5: Troubleshooting**
- Request: "Why is my application not starting?"
- Expected: Claude queries application state, events, logs
- Validation: Multi-faceted diagnostic approach

### 3.3 User Acceptance Testing

**Test Users**:
- Platform administrators
- Application developers
- Security analysts

**Evaluation Criteria**:
- Query accuracy
- Response relevance
- Error handling
- Learning curve

---

## Phase 4: Deployment & Documentation

### 4.1 MCP Server Deployment

**Installation Methods**:

1. **Local Development**:
```bash
# Using Maven
mvn spring-boot:run

# With environment variables
export TANZU_PLATFORM_URL="https://tanzu-hub.kuhn-labs.com"
export TOKEN="your-token-here"
mvn spring-boot:run

# Using Gradle
./gradlew bootRun
```

2. **JAR Deployment**:
```bash
# Build executable JAR
mvn clean package -DskipTests

# Run JAR
java -jar target/tanzu-mcp-server-1.0.0.jar \
  --tanzu.platform.url=https://tanzu-hub.kuhn-labs.com \
  --tanzu.platform.token=$TOKEN
```

3. **Claude Desktop Configuration**:
```json
{
  "mcpServers": {
    "tanzu-platform": {
      "type": "streamable-http",
      "url": "http://localhost:8080/mcp",
      "metadata": {
        "name": "Tanzu Platform MCP Server",
        "version": "1.0.0",
        "description": "Natural language interface to Tanzu Platform GraphQL API"
      }
    }
  }
}
```

**For Remote Deployment**:
```json
{
  "mcpServers": {
    "tanzu-platform": {
      "type": "streamable-http",
      "url": "https://tanzu-mcp.your-domain.com/mcp",
      "headers": {
        "X-API-Key": "${MCP_API_KEY}"
      }
    }
  }
}
```

4. **Tanzu Platform Deployment** (on TAS):
```yaml
# manifest.yml
applications:
- name: tanzu-mcp-server
  memory: 1G
  instances: 2
  path: target/tanzu-mcp-server-1.0.0.jar
  buildpacks:
    - java_buildpack
  env:
    TANZU_PLATFORM_URL: https://tanzu-hub.kuhn-labs.com
    SPRING_PROFILES_ACTIVE: cloud
  services:
    - tanzu-api-credentials
  routes:
    - route: tanzu-mcp.apps.example.com
  health-check-type: http
  health-check-http-endpoint: /actuator/health
```

**Deployment Command**:
```bash
cf push
```

**Production Configuration (application-production.yml)**:
```yaml
spring:
  ai:
    mcp:
      server:
        transport: STREAMABLE_HTTP
        security:
          enabled: true
          api-key: ${MCP_API_KEY}
        cors:
          allowed-origins: 
            - https://claude.ai
            - https://app.claude.ai
          
tanzu:
  platform:
    graphql:
      timeout: 60s
      max-retries: 5
      
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
        
logging:
  level:
    root: INFO
    com.tanzu.mcp: DEBUG
  file:
    name: /var/log/tanzu-mcp-server.log
```

### 4.2 Skill Deployment

**For Projects**:
```bash
# Copy to project skills directory
cp SKILL.md /path/to/project/skills/tanzu-platform/SKILL.md
```

**For System-Wide**:
```bash
# Copy to Claude's skills directory
cp SKILL.md ~/.claude/skills/tanzu-platform/SKILL.md
```

### 4.3 Documentation

**README.md Sections**:
1. Overview
2. Prerequisites
3. Building from Source
4. Configuration
5. Running Locally
6. Deployment Options
7. Available Tools
8. Example Usage
9. Monitoring & Health Checks
10. Troubleshooting
11. Contributing

**Example README.md Content**:

```markdown
# Tanzu Platform MCP Server

A Spring Boot-based MCP (Model Context Protocol) server providing natural language interface to the Tanzu Platform GraphQL API.

## Overview

This server exposes Tanzu Platform capabilities through MCP tools, enabling Claude to interact with your Tanzu infrastructure, applications, and security data through natural language queries.

## Prerequisites

- Java 17 or higher
- Maven 3.8+ or Gradle 7+
- Access to Tanzu Platform (URL and Bearer token)

## Building from Source

### Maven
\`\`\`bash
mvn clean package
\`\`\`

### Gradle
\`\`\`bash
./gradlew build
\`\`\`

## Configuration

### Environment Variables
\`\`\`bash
export TANZU_PLATFORM_URL=https://tanzu-hub.kuhn-labs.com
export TOKEN=your-bearer-token-here
\`\`\`

### application.yml
See `src/main/resources/application.yml` for full configuration options.

## Running Locally

### Maven
\`\`\`bash
mvn spring-boot:run
\`\`\`

### JAR
\`\`\`bash
java -jar target/tanzu-mcp-server-1.0.0.jar
\`\`\`

The server will start on port 8080 by default. MCP endpoint: `http://localhost:8080/mcp`

## Deployment Options

- **Standalone JAR**: Deploy as executable JAR on any Java 17+ environment
- **Tanzu Application Service**: Push using provided `manifest.yml`
- **Cloud Foundry**: Compatible with any CF platform

## Available Tools

### 1. tanzu_graphql_query
Execute read-only GraphQL queries.

**Example**:
\`\`\`
Query: "List all foundations"
Claude executes: tanzu_graphql_query with appropriate GraphQL query
\`\`\`

### 2. tanzu_graphql_mutate
Execute GraphQL mutations (with safety confirmations).

### 3. tanzu_explore_schema
Explore API schema and documentation.

### 4. tanzu_find_entity_path
Find relationship paths between entity types.

### 5. tanzu_common_queries
Execute pre-built common query patterns.

## Example Usage

### Natural Language Queries

**User**: "Show me all applications in production foundation"

**Claude's Process**:
1. Uses `tanzu_common_queries` or constructs query
2. Executes via `tanzu_graphql_query`
3. Formats results naturally

**User**: "What are my critical vulnerabilities?"

**Claude's Process**:
1. Constructs vulnerability query with CRITICAL filter
2. Executes and analyzes results
3. Presents actionable summary

### Direct Tool Usage

\`\`\`java
// Via Spring AI
String query = """
    query {
      entityQuery {
        Entity_Tanzu_TAS_Foundation {
          edges {
            node {
              id
              properties { name }
            }
          }
        }
      }
    }
    """;

GraphQLResponse response = tanzuQueryTool.executeQuery(query, null, null);
\`\`\`

## Monitoring & Health Checks

### Health Endpoint
\`\`\`bash
curl http://localhost:8080/actuator/health
\`\`\`

### Metrics
\`\`\`bash
curl http://localhost:8080/actuator/metrics
\`\`\`

### Prometheus Metrics
\`\`\`bash
curl http://localhost:8080/actuator/prometheus
\`\`\`

### Cache Statistics
\`\`\`bash
curl http://localhost:8080/actuator/caches
\`\`\`

## Troubleshooting

### Server Won't Start
- Check Java version: `java -version` (must be 17+)
- Verify environment variables are set
- Check port 8080 is available

### Connection Issues
- Verify TANZU_PLATFORM_URL is accessible
- Check TOKEN is valid and not expired
- Review network/proxy settings

### GraphQL Errors
- Use `tanzu_explore_schema` to verify type names
- Check query syntax with GraphQL validator
- Review error messages in logs

### Performance Issues
- Check schema cache is warming up properly
- Monitor memory usage via actuator
- Adjust timeout settings in application.yml

## Development

### Running Tests
\`\`\`bash
mvn test
\`\`\`

### Integration Tests
\`\`\`bash
mvn verify -Pintegration-tests
\`\`\`

### Code Coverage
\`\`\`bash
mvn jacoco:report
open target/site/jacoco/index.html
\`\`\`

## API Documentation

### OpenAPI/Swagger
Available at: `http://localhost:8080/swagger-ui.html`

### Actuator Endpoints
Available at: `http://localhost:8080/actuator`

## Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

## License

[Your License Here]

## Support

- GitHub Issues: https://github.com/your-org/tanzu-mcp-server/issues
- Documentation: https://your-docs-site.com
- Slack: #tanzu-mcp-support
\`\`\`

**Additional Documentation Files**:

1. **ARCHITECTURE.md**: System architecture, design decisions
2. **API.md**: Detailed MCP tool API documentation
3. **DEPLOYMENT.md**: Comprehensive deployment guide
4. **DEVELOPMENT.md**: Developer setup and guidelines
5. **CHANGELOG.md**: Version history and changes

---

## Phase 5: Enhancements & Optimization

### 5.1 Advanced Features

**1. Query Template Library**
- Pre-built templates for common operations
- Parameterized queries
- Composable fragments

**2. Intelligent Caching**
- Cache entity lists
- Invalidate on mutations
- Reduce API calls

**3. Batch Operations**
- Group related queries
- Parallel execution
- Result aggregation

**4. Natural Language Query Builder**
- Parse user intent
- Suggest query structure
- Auto-complete entity names

**5. Visualization Hints**
- Suggest chart types for metrics
- Format topology as graphs
- Highlight critical items

### 5.2 Monitoring & Logging

**Metrics to Track**:
- API call volume
- Query complexity
- Error rates
- Response times
- Common query patterns

**Logging Strategy**:
- All queries/mutations
- Error details
- User feedback
- Performance metrics

### 5.3 Security Considerations

**Token Management**:
- Never log tokens
- Support token rotation
- Validate token before use
- Handle expiration gracefully

**Query Validation**:
- Prevent injection attacks
- Validate input parameters
- Limit query complexity
- Rate limiting

**Data Privacy**:
- Don't cache sensitive data
- Respect data retention policies
- Audit data access

---

## Success Metrics

### Technical Metrics
| Metric | Target | How to Measure |
|--------|--------|----------------|
| Query success rate | > 95% | Successful API responses / Total queries |
| Validation catch rate | > 80% | Errors caught by `tanzu_validate_query` before API call |
| Average response time | < 2s | End-to-end from NL request to formatted response |
| Schema coverage | > 80% | Skill documents entities that represent 80%+ of common operations |
| Error recovery rate | > 90% | Successfully corrected queries after first failure |
| Cache hit rate | > 90% | Schema lookups served from cache |

### User Experience Metrics
| Metric | Target | How to Measure |
|--------|--------|----------------|
| User satisfaction | > 4/5 | Post-interaction survey |
| Task completion rate | > 85% | User achieved goal without giving up |
| Time to insight | 50% reduction | Compared to manual GraphQL construction |
| Self-service rate | > 70% | Issues resolved without human support |
| First-query success | > 60% | User's first attempt works without retry |

### Quality Gates by Phase
| Phase | Gate Criteria |
|-------|---------------|
| MCP Server | All 6 tools pass integration tests; <500ms tool response time |
| Query Validation | Catches 80%+ of syntax/field errors; provides actionable suggestions |
| Skill | Covers TAS, Security, Observability domains; includes 20+ query templates |
| Integration | End-to-end tests pass; query success rate > 90% in testing |
| Production | No P1 incidents in first week; user feedback > 3.5/5 |

---

## Timeline Estimate (Revised)

> **Revision Note**: Based on schema complexity analysis (1,382 types, 6+ domains), the skill component and query validation layer require significantly more investment than originally planned.

### Phase 1: MCP Server Core (3 weeks)
- **Week 1**: Project setup and foundation
  - Maven/Gradle setup with dependencies
  - Spring AI MCP starter integration
  - Basic GraphQL client with WebClient
  - Configuration management
  - Authentication handling
  
- **Week 2**: Core tools implementation
  - Implement 6 MCP tools (including new `tanzu_validate_query`)
  - GraphQL service layer
  - Error handling and validation
  - Basic integration tests
  
- **Week 3**: Schema services and caching
  - Schema introspection service
  - Spring Cache integration with Caffeine
  - Entity relationship mapping
  - Domain-aware schema exploration

### Phase 1.5: Query Validation Layer (1 week) *(NEW)*
- **Days 1-3**: Validation implementation
  - GraphQL parser integration
  - Field validation against cached schema
  - "Did you mean?" suggestions using Levenshtein distance
  - Complexity estimation
  
- **Days 4-5**: Error feedback loop
  - Structured error responses for self-correction
  - Integration with query tool
  - Testing validation scenarios

### Phase 2: Skill Development (2-3 weeks) *(EXPANDED)*
- **Week 1**: Core structure and TAS domain
  - Skill architecture setup (SKILL.md + subdirectories)
  - TAS domain documentation (Foundation → Application)
  - Entity navigation patterns
  - Common query templates (10+)
  
- **Week 2**: Additional domains
  - Security domain (vulnerabilities, CVEs, policies)
  - Observability domain (alerts, metrics, notifications)
  - Spring domain (artifacts, dependencies)
  - Capacity domain (recommendations)
  
- **Week 3**: Patterns and troubleshooting
  - Pagination patterns
  - Filtering patterns by entity type
  - Error recovery guide
  - Anti-patterns documentation
  - Query construction best practices

### Phase 3: Integration Testing (1 week)
- **Days 1-3**: Integration testing
  - Test MCP server with Claude Desktop/claude.ai
  - Validate tool responses match skill guidance
  - Test common workflows end-to-end
  - Validate query validation catches errors
  
- **Days 4-5**: User acceptance testing
  - Test with actual use cases (natural language → results)
  - Measure query success rate
  - Gather feedback on skill clarity
  - Refine based on observed failures

### Phase 4: Deployment (1 week)
- **Days 1-2**: Deployment preparation
  - TAS manifest configuration
  - Environment-specific configurations
  - Health check endpoints
  
- **Days 3-4**: Production hardening
  - Security configuration
  - Monitoring and alerting setup
  - Logging configuration
  - Token refresh mechanisms
  
- **Day 5**: Documentation and release
  - Deployment guides
  - Runbooks
  - Release notes

### Phase 5: Iteration (Ongoing)
- **Month 1**: Gather usage feedback
  - Track query success/failure patterns
  - Identify missing skill content
  - Monitor performance
  
- **Month 2+**: Iterative improvements
  - Expand common query templates
  - Refine domain documentation
  - Add new entity types as API evolves

---

### Timeline Summary

| Phase | Original | Revised | Notes |
|-------|----------|---------|-------|
| MCP Server Core | 3-4 weeks | 3 weeks | Focused on core tools |
| Query Validation | (not planned) | 1 week | **NEW** - Critical for self-correction |
| Skill Development | 1 week | 2-3 weeks | **EXPANDED** - Domain-partitioned |
| Integration Testing | 1 week | 1 week | Unchanged |
| Deployment | 3-5 days | 1 week | Slightly expanded |
| **Total** | **6-8 weeks** | **8-10 weeks** | More realistic |

**Why the increase?**
1. Query validation layer is essential for reducing failed API calls
2. Skill component needs domain-specific depth (not just one file)
3. 1,382-type schema requires comprehensive navigation guidance
4. Error recovery documentation prevents user frustration

**Note**: This investment pays off in:
- Higher query success rate (>95% target)
- Faster iteration when queries fail (self-correction)
- Better user experience (natural language actually works)
- Lower support burden (users can troubleshoot themselves)

---

## Next Steps

1. **Review this revised plan** with stakeholders
2. **Set up development environment**
3. **Create GitHub repository** for MCP server
4. **Begin Phase 1: MCP Server implementation**
5. **Parallel: Start skill content outline** (don't wait for Phase 2)
6. **Schedule weekly checkpoints**
7. **Define success metrics** (query success rate, response time, user satisfaction)

---

## Resources & References

- **Spring AI MCP Documentation**: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html
- **Spring Boot Documentation**: https://docs.spring.io/spring-boot/docs/current/reference/html/
- **Spring WebClient**: https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html
- **GraphQL Java**: https://www.graphql-java.com/
- **GraphQL Best Practices**: https://graphql.org/learn/best-practices/
- **MCP Specification**: https://spec.modelcontextprotocol.io/
- **Tanzu Platform API**: https://tanzu-hub.kuhn-labs.com/hub/graphql
- **Spring Cache**: https://docs.spring.io/spring-framework/reference/integration/cache.html
- **Caffeine Cache**: https://github.com/ben-manes/caffeine
- **Spring Boot Actuator**: https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html
- **JUnit 5**: https://junit.org/junit5/docs/current/user-guide/
- **Mockito**: https://site.mockito.org/
- **TestContainers** (for integration tests): https://www.testcontainers.org/

---

## Appendix: Technology Decision Rationale

### Chosen Approach: Spring Boot + Spring AI MCP Server

**Selected**: Streamable HTTP MCP server with Spring Boot

**Key Advantages**:
- Production-ready with Spring Boot's mature ecosystem
- Streamable HTTP better for cloud/web deployments
- Natural fit with Tanzu/VMware technology stack
- Type safety and compile-time validation
- Comprehensive monitoring and observability
- Easier horizontal scaling
- Better enterprise security features

### Alternative Approaches Considered

#### Option A: Python with FastMCP (stdio)
**Pros**: 
- Faster initial development
- Simpler for prototyping
- Less boilerplate code
- Lighter weight

**Cons**: 
- stdio transport less suitable for web deployment
- Less robust for production environments
- Limited monitoring capabilities
- Not aligned with Tanzu ecosystem

**When to Choose**: Quick prototypes, local-only usage, simple tools

#### Option B: Node.js with MCP SDK
**Pros**: 
- JavaScript ecosystem
- Good for web-based deployments
- Async by default

**Cons**: 
- Less common in enterprise Java shops
- Not aligned with Tanzu/Spring ecosystem
- Type safety requires TypeScript

**When to Choose**: JavaScript-heavy teams, web-first applications

#### Option C: Pure REST API Wrapper (No MCP)
**Pros**: 
- Simpler architecture
- No MCP protocol overhead
- Direct HTTP access

**Cons**: 
- Claude would need to use bash/curl directly
- Less structured tool interface
- No benefit from MCP's tool discovery
- More complex for Claude to use

**When to Choose**: Simple single-purpose APIs, non-LLM clients

#### Option D: Conversational AI Integration (Using Tanzu's built-in conversational API)
**Pros**: 
- Built-in to platform
- Potentially simpler setup

**Cons**: 
- Limited to Tanzu's conversational capabilities
- Less flexible than MCP
- Observed in schema but might not be fully featured

**When to Choose**: If already available and meets requirements

### Decision Matrix

| Criteria | Spring Boot MCP | Python FastMCP | Node.js MCP | REST Wrapper |
|----------|----------------|----------------|-------------|--------------|
| Production Readiness | â­â­â­â­â­ | â­â­â­ | â­â­â­â­ | â­â­â­â­ |
| Development Speed | â­â­â­ | â­â­â­â­â­ | â­â­â­â­ | â­â­â­â­ |
| Tanzu Alignment | â­â­â­â­â­ | â­â­ | â­â­ | â­â­â­ |
| Type Safety | â­â­â­â­â­ | â­â­ | â­â­â­â­ | â­â­â­â­ |
| Scalability | â­â­â­â­â­ | â­â­â­ | â­â­â­â­ | â­â­â­â­â­ |
| Observability | â­â­â­â­â­ | â­â­ | â­â­â­ | â­â­â­â­ |
| Cloud Deployment | â­â­â­â­â­ | â­â­â­ | â­â­â­â­ | â­â­â­â­â­ |
| MCP Integration | â­â­â­â­â­ | â­â­â­â­â­ | â­â­â­â­â­ | â­ |

**Conclusion**: Spring Boot + Spring AI provides the best balance of production readiness, ecosystem alignment, and enterprise features for a Tanzu-focused deployment.

---

## Quick Start Guide

**For the Impatient**: Get a basic MCP server running in 30 minutes:

```bash
# 1. Generate Spring Boot project
curl https://start.spring.io/starter.tgz \
  -d dependencies=web,webflux,actuator,cache \
  -d type=maven-project \
  -d language=java \
  -d bootVersion=3.2.0 \
  -d baseDir=tanzu-mcp-server \
  -d groupId=com.tanzu \
  -d artifactId=mcp-server | tar -xzf -

cd tanzu-mcp-server

# 2. Add Spring AI MCP dependency to pom.xml
# (See Phase 1.1 for complete pom.xml)

# 3. Create application.yml with Tanzu credentials
# (See Phase 1.1 for complete configuration)

# 4. Implement first MCP tool
# (See Phase 1.2 for TanzuQueryTool example)

# 5. Run the server
export TANZU_PLATFORM_URL=https://tanzu-hub.kuhn-labs.com
export TOKEN=your-token
mvn spring-boot:run

# 6. Test with curl
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
      "name": "tanzu_graphql_query",
      "arguments": {
        "query": "query { entityQuery { Entity_Tanzu_TAS_Foundation { edges { node { id } } } } }"
      }
    },
    "id": 1
  }'
```

**Expected Result**: JSON response with foundation data

**Next Steps**: Continue with Phase 1 for full implementation
