# Tanzu Platform Skill

Query the Tanzu Platform to answer questions about TAS foundations, organizations, spaces, applications, services, Spring Boot apps, vulnerabilities, observability, and capacity.

## Entity Queries

Use `tanzu_entity_query` for any entity in the Tanzu Platform hierarchy.

### Entity types

| Domain | Types |
|--------|-------|
| tas (default) | foundation, organization, space, application, serviceinstance, boshdirector, boshvm, buildpack, deployment, domain, droplet, isolationsegment, opsmanager, organizationquota, processinstance, resource, revision, serviceoffering, serviceplan, stemcell, tile, vcenterconfig, vmtype |
| spring | application (use domain: "spring"), instance, cloudgateway, appreplica |
| platform | foundationgroup, organizationgroup, spacegroup |

### Scoping (narrow results to a parent entity)

Pass a `scope` JSON to scope queries to a parent entity. The server handles relationship navigation automatically.

- "List foundations" -> entityType: "foundation"
- "Orgs in foundation X" -> entityType: "organization", scope: {"foundation": "X"}
- "Spaces in org Y" -> entityType: "space", scope: {"organization": "Y"}
- "Apps in space Z" -> entityType: "application", scope: {"space": "Z"}
- "Apps in org Y on foundation X" -> entityType: "application", scope: {"foundation": "X", "organization": "Y"}

### Common filters

Pass a `filter` JSON to filter by entity properties. The server validates property names and suggests corrections if wrong.

**Applications:**
- "stopped apps" -> filter: {"property": "state", "value": "STOPPED"}
- "running apps" -> filter: {"property": "state", "value": "STARTED"}
- "Spring Boot apps" -> filter: {"property": "springApp", "value": true}
- "apps with crashed instances" -> filter: {"property": "crashedInstanceCount", "operator": "GT", "value": 0}
- "Java apps" -> filter: {"property": "buildpack", "operator": "CONTAINS", "value": "java"}

**Service instances:**
- "upgradeable services" -> filter: {"property": "upgradeAvailable", "value": true}

If unsure of the property name, try your best guess -- the server will suggest valid property names if wrong.

### Combining scope + filter

- "stopped apps in foundation X" -> entityType: "application", scope: {"foundation": "X"}, filter: {"property": "state", "value": "STOPPED"}

### Include details

- Pass include: "properties" for full entity properties
- Default is "minimal" (entityName, entityId, entityType only)

## Mutations

Use `tanzu_graphql_mutate` for write operations. Always confirm destructive actions with the user first.

## Raw GraphQL (fallback)

If no tool above fits, use `tanzu_graphql_query` with raw GraphQL. Key rules:
- Use `entityName` for names (NOT `properties.name`)
- Use `entityQuery -> typed -> tanzu -> {domain} -> {entityType} -> query(...)`
- Relationships use `relationshipsIn.isContainedIn.tanzu_tas_{entity}` (NOT `contains`)
