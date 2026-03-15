# Deployment Guide

This guide covers building, running, and deploying the Tanzu Platform MCP Server.

## Prerequisites

- Java 21 or higher
- Maven 3.8+ (or use included `mvnw` wrapper)
- Access to Tanzu Platform (URL and bearer token)

## Environment Variables

| Variable | Required | Description | Example |
|----------|----------|-------------|---------|
| `TANZU_PLATFORM_URL` | Yes | Tanzu Platform base URL | `https://tanzu-hub.kuhn-labs.com` |
| `TOKEN` | Yes | Bearer token for authentication | `eyJ...` |

## Building

### Using Maven Wrapper (Recommended)

```bash
cd hub-mcp

# Build without tests
./mvnw clean package -DskipTests

# Build with tests
./mvnw clean package

# Just compile
./mvnw compile
```

### Using System Maven

```bash
cd hub-mcp
mvn clean package -DskipTests
```

### Build Output

- Executable JAR: `target/hub-mcp-0.0.1-SNAPSHOT.jar`

## Running Locally

### Option 1: Maven Spring Boot Plugin

```bash
cd hub-mcp

# Set environment variables
export TANZU_PLATFORM_URL=https://tanzu-hub.kuhn-labs.com
export TOKEN=your-bearer-token

# Run
./mvnw spring-boot:run
```

### Option 2: Executable JAR

```bash
# Build first
cd hub-mcp
./mvnw clean package -DskipTests

# Set environment variables and run
export TANZU_PLATFORM_URL=https://tanzu-hub.kuhn-labs.com
export TOKEN=your-bearer-token
java -jar target/hub-mcp-0.0.1-SNAPSHOT.jar
```

### Option 3: With Inline Properties

```bash
java -jar target/hub-mcp-0.0.1-SNAPSHOT.jar \
  --tanzu.platform.url=https://tanzu-hub.kuhn-labs.com \
  --tanzu.platform.token=$TOKEN
```

### Default Ports

- MCP Server: `http://localhost:8080`
- MCP Endpoint: `http://localhost:8080/mcp`
- Actuator: `http://localhost:8080/actuator`

## Verifying the Server

### Health Check

```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{
  "status": "UP"
}
```

### Test MCP Endpoint

```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/list",
    "id": 1
  }'
```

### Test a Query

```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
      "name": "tanzu_common_queries",
      "arguments": {
        "pattern": "list_foundations"
      }
    },
    "id": 1
  }'
```

## Claude Desktop Configuration

### For Local Development

Add to Claude Desktop's MCP configuration:

```json
{
  "mcpServers": {
    "tanzu-platform": {
      "type": "streamable-http",
      "url": "http://localhost:8080/mcp",
      "metadata": {
        "name": "Tanzu Platform MCP Server",
        "version": "1.0.0"
      }
    }
  }
}
```

### For Remote Deployment

```json
{
  "mcpServers": {
    "tanzu-platform": {
      "type": "streamable-http",
      "url": "https://your-mcp-server.example.com/mcp",
      "headers": {
        "X-API-Key": "${MCP_API_KEY}"
      }
    }
  }
}
```

## Deploying to Tanzu Application Service (TAS)

### Create manifest.yml

```yaml
applications:
- name: tanzu-mcp-server
  memory: 1G
  instances: 1
  path: target/hub-mcp-0.0.1-SNAPSHOT.jar
  buildpacks:
    - java_buildpack
  env:
    TANZU_PLATFORM_URL: https://tanzu-hub.kuhn-labs.com
    SPRING_PROFILES_ACTIVE: cloud
    JBP_CONFIG_OPEN_JDK_JRE: '{ jre: { version: 21.+ } }'
  services:
    - tanzu-api-credentials
  routes:
    - route: tanzu-mcp.apps.example.com
  health-check-type: http
  health-check-http-endpoint: /actuator/health
```

### Create User-Provided Service for Credentials

```bash
cf create-user-provided-service tanzu-api-credentials -p '{"TOKEN":"your-bearer-token"}'
```

### Deploy

```bash
# Build
cd hub-mcp
./mvnw clean package -DskipTests

# Push to TAS
cf push
```

### Verify Deployment

```bash
# Check app status
cf app tanzu-mcp-server

# Check logs
cf logs tanzu-mcp-server --recent

# Test health
curl https://tanzu-mcp.apps.example.com/actuator/health
```

## Deploying to Kubernetes

### Create Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: tanzu-mcp-server
spec:
  replicas: 1
  selector:
    matchLabels:
      app: tanzu-mcp-server
  template:
    metadata:
      labels:
        app: tanzu-mcp-server
    spec:
      containers:
      - name: tanzu-mcp-server
        image: your-registry/tanzu-mcp-server:latest
        ports:
        - containerPort: 8080
        env:
        - name: TANZU_PLATFORM_URL
          value: "https://tanzu-hub.kuhn-labs.com"
        - name: TOKEN
          valueFrom:
            secretKeyRef:
              name: tanzu-credentials
              key: token
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
```

### Create Secret

```bash
kubectl create secret generic tanzu-credentials \
  --from-literal=token=your-bearer-token
```

### Create Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: tanzu-mcp-server
spec:
  selector:
    app: tanzu-mcp-server
  ports:
  - port: 80
    targetPort: 8080
  type: ClusterIP
```

### Create Ingress (Optional)

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: tanzu-mcp-server
  annotations:
    kubernetes.io/ingress.class: nginx
spec:
  rules:
  - host: tanzu-mcp.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: tanzu-mcp-server
            port:
              number: 80
```

## Configuration Reference

### application.yml

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
      refresh-cron: "0 0 2 * * ?"

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

### Production Profile (application-production.yml)

```yaml
spring:
  ai:
    mcp:
      server:
        transport: STREAMABLE_HTTP

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
    org.tanzu.hubmcp: INFO
```

## Troubleshooting

### Server Won't Start

1. **Check Java version**: `java -version` (must be 21+)
2. **Verify environment variables**: `echo $TANZU_PLATFORM_URL`
3. **Check port availability**: `lsof -i :8080`
4. **Review logs**: Look for startup errors

### Connection Issues

1. **Verify URL**: `curl $TANZU_PLATFORM_URL/hub/graphql`
2. **Check token**: Ensure token is valid and not expired
3. **Network/proxy**: Check firewall and proxy settings

### Authentication Errors

1. **Token format**: Should be a valid JWT
2. **Token expiration**: Refresh if expired
3. **Permissions**: Verify token has required scopes

### Cache Issues

1. **Force refresh**: Restart the server
2. **Check actuator**: `curl localhost:8080/actuator/caches`
3. **Review logs**: Look for cache refresh messages

## Monitoring

### Key Endpoints

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Overall health |
| `/actuator/health/liveness` | Kubernetes liveness |
| `/actuator/health/readiness` | Kubernetes readiness |
| `/actuator/metrics` | All metrics |
| `/actuator/caches` | Cache statistics |

### Useful Metrics

- `http.server.requests` - Request timing
- `cache.gets` - Cache access stats
- `jvm.memory.used` - Memory usage
- `system.cpu.usage` - CPU usage

