# Security Domain

## Overview

The Security domain provides vulnerability management, CVE tracking, compliance monitoring, and security policy enforcement for the Tanzu Platform.

## Key Entity Types

| Entity Type | Description |
|------------|-------------|
| ArtifactVulnerability | Vulnerability in an artifact |
| CVE | Common Vulnerabilities and Exposures entry |
| Insight | Security insights and recommendations |
| TanzuHubPolicy | Security and compliance policies |

## Vulnerability Severities

| Severity | Description | Priority |
|----------|-------------|----------|
| CRITICAL | Immediate exploitation risk | Highest |
| HIGH | Serious security risk | High |
| MEDIUM | Moderate security concern | Medium |
| LOW | Minor security issue | Low |
| UNKNOWN | Severity not determined | Varies |

## Common Queries

### Find Critical Vulnerabilities

```graphql
query CriticalVulns {
  artifactVulnerabilityQuery {
    vulnerabilities(filter: { severity: CRITICAL }) {
      edges {
        node {
          id
          cveId
          severity
          score {
            value
            type
          }
          description
        }
      }
    }
  }
}
```

### Find Vulnerabilities by Severity

```graphql
query VulnsBySeverity($severity: ArtifactVulnerabilitySeverity!) {
  artifactVulnerabilityQuery {
    vulnerabilities(filter: { severity: $severity }) {
      edges {
        node {
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

### Find Open (Untriaged) Vulnerabilities

```graphql
query OpenVulns {
  artifactVulnerabilityQuery {
    vulnerabilities(filter: { triageStatus: OPEN }) {
      edges {
        node {
          cveId
          severity
          triageStatus
        }
      }
    }
  }
}
```

### List Security Policies

```graphql
# Use tanzu_explore_schema to discover policy query structure
```

## Triage Statuses

| Status | Description |
|--------|-------------|
| OPEN | Not yet reviewed |
| IN_PROGRESS | Being investigated |
| RESOLVED | Issue addressed |
| FALSE_POSITIVE | Not a real vulnerability |
| ACCEPTED_RISK | Known but accepted |

## CVE Score Types

| Type | Description |
|------|-------------|
| CVSS_V2 | CVSS version 2.0 score |
| CVSS_V3 | CVSS version 3.x score |

## Related Domains

- **Spring** - Dependency vulnerabilities
- **TAS** - Application security
- **Observability** - Security alerts

## Best Practices

1. **Prioritize by severity** - Address CRITICAL and HIGH first
2. **Track affected artifacts** - Understand blast radius
3. **Monitor triage status** - Ensure vulnerabilities are being addressed
4. **Use filters** - Large environments may have many vulnerabilities

## Notes

- Vulnerability data is refreshed periodically
- Some vulnerabilities may affect multiple artifacts
- CVE IDs link to external CVE databases
