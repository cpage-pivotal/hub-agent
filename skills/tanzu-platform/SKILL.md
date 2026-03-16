---
name: tanzu-platform
description: Query and manage Tanzu Platform resources — foundations, organizations, spaces, applications, vulnerabilities, alerts, and capacity. Use when the user asks about their Tanzu Platform environment, wants to list or inspect TAS resources, investigate application health or stopped apps, find CVEs or security vulnerabilities, check platform capacity, or mentions Tanzu Hub, Tanzu Platform, or TAS in any context.
compatibility: Requires Python 3 (stdlib only) and the tanzu-platform MCP server (hub). TANZU_HUB_URL, TANZU_HUB_USER, and TANZU_HUB_PASSWORD are pre-configured as environment variables.
---

# Tanzu Platform Skill

## Workflow

For every Tanzu Platform request, execute these steps in order. Do not ask the user for a token — generate it yourself.

### Step 1: Get a token

Run this script — it reads credentials from pre-configured environment variables and prints a bearer token to stdout:

```bash
python3 scripts/get-token.py
```

Capture the printed token string. Tokens expire after 30 minutes; get a fresh one if a tool call returns an authentication error.

### Step 2: Pick the right tool call

Check if a `tanzu_common_queries` pattern matches the request:

| User Request | Pattern |
|---|---|
| List foundations | `list_foundations` |
| Find a foundation by name | `get_foundation_by_name` |
| List organizations | `list_organizations` |
| List spaces / space overview | `list_spaces` or `spaces_summary` |
| List applications | `list_applications` |
| Find stopped apps | `find_stopped_apps` |
| Stopped apps per space | `count_stopped_apps_by_space` |
| App state distribution | `summarize_app_states` |
| Spaces with their apps | `spaces_with_apps` |
| Service bindings | `list_service_bindings` |
| Vulnerabilities | `find_vulnerabilities` |
| Critical CVEs | `find_critical_cves` |
| Insights | `list_insights` |
| Artifact SBOM | `get_artifact_sbom` |
| Alerts | `list_alerts` |
| Capacity | `check_capacity` |
| Spring apps | `list_spring_apps` |
| App health | `get_app_health` |

If a pattern matches, call it directly:

```
tanzu_common_queries(pattern: "list_foundations", token: "<token>")
```

If no pattern matches, fall through to custom query construction (see below).

### Step 3: Present the results

Format the response clearly for the user. For lists, use tables or bullet points.

## Gotchas

- Entity names are `entityName` at the entity level, NOT `properties.name` (that field does not exist).
- Relationship navigation uses snake_case entity fields like `tanzu_tas_application`, NOT generic `contains`.
- Prefer `count_stopped_apps_by_space` or `summarize_app_states` for aggregation questions — `spaces_with_apps` returns very large payloads.
- If a tool call fails with an auth error, get a fresh token and retry.

## Custom Query Construction

If no `tanzu_common_queries` pattern matches:

1. Read `reference/query-construction.md` for query syntax and naming conventions
2. Read the relevant domain file for entity fields:
   - TAS (foundations, orgs, spaces, apps): `domains/TAS.md`
   - Spring Boot: `domains/Spring.md`
   - Alerts/observability: `domains/Observability.md`
   - Vulnerabilities/CVEs: `domains/Security.md`
   - Capacity: `domains/Capacity.md`
3. Validate with `tanzu_validate_query` before executing with `tanzu_graphql_query`
4. If the API returns an error, read `troubleshooting/error-recovery.md`

## Available MCP Tools

| Tool | Purpose |
|------|---------|
| `tanzu_common_queries` | Pre-built query patterns — try this first |
| `tanzu_graphql_query` | Execute custom read queries |
| `tanzu_validate_query` | Validate query syntax before execution |
| `tanzu_graphql_mutate` | Create, update, or delete resources |
| `tanzu_explore_schema` | Discover unknown types/fields |
| `tanzu_find_entity_path` | Navigate unfamiliar entity relationships |
