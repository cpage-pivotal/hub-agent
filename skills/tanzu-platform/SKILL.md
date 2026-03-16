---
name: tanzu-platform
description: Query and manage Tanzu Platform resources — foundations, organizations, spaces, applications, vulnerabilities, alerts, and capacity — using natural language. Use when the user asks about their Tanzu Platform environment, wants to list or inspect TAS resources, investigate application health or stopped apps, find CVEs or security vulnerabilities, or check platform capacity.
compatibility: Requires Python 3 (stdlib only) and the tanzu-platform MCP server. Set TANZU_HUB_URL, TANZU_HUB_USER, and TANZU_HUB_PASSWORD before use.
---

# Tanzu Platform Natural Language Interface Skill

## Quick Start — Follow These Steps First

For most Tanzu Platform questions, you only need two steps:

**Step 1: Get a token** (required once per session, tokens last 30 minutes):

```bash
python3 scripts/get-token.py
```

Reads `TANZU_HUB_URL`, `TANZU_HUB_USER`, `TANZU_HUB_PASSWORD` from environment variables. Capture the printed token for Step 2.

**Step 2: Call `tanzu_common_queries` with the matching pattern:**

| User Request | Pattern to Use |
|---|---|
| "List my foundations" | `list_foundations` |
| "Find a foundation by name" | `get_foundation_by_name` |
| "List organizations" | `list_organizations` |
| "List spaces" / "Space overview" | `list_spaces` or `spaces_summary` |
| "List applications" | `list_applications` |
| "Find stopped apps" | `find_stopped_apps` |
| "Spaces with stopped apps" / "How many stopped apps per space?" | `count_stopped_apps_by_space` |
| "App state distribution" / "How many started vs stopped?" | `summarize_app_states` |
| "Show spaces with their apps" | `spaces_with_apps` |
| "List service bindings" | `list_service_bindings` |
| "Find vulnerabilities" | `find_vulnerabilities` |
| "Find critical CVEs" | `find_critical_cves` |
| "List insights" | `list_insights` |
| "Show artifact SBOM" | `get_artifact_sbom` |
| "List alerts" | `list_alerts` |
| "Check capacity" | `check_capacity` |
| "List Spring apps" | `list_spring_apps` |
| "App health status" | `get_app_health` |

Example call:

```
tanzu_common_queries(pattern: "list_foundations", token: "<token from step 1>")
```

**That's it.** If a pre-built pattern matches the request, do NOT use schema exploration, query validation, or the full query construction workflow. Just get the token and call the pattern.

## Decision Tree — When to Use What

```
Can a tanzu_common_queries pattern handle the request?
  YES → Get token → call tanzu_common_queries. Done.
  NO  → Is it a simple read query you can construct from the reference below?
    YES → Get token → build query → tanzu_validate_query → tanzu_graphql_query. Done.
    NO  → Get token → use tanzu_explore_schema to discover types → build query → validate → execute.
```

Only fall through to the full workflow when no pre-built pattern exists and you need to discover the schema.

---

## Authentication

All MCP tools require a `token` argument. Tokens rotate every 30 minutes — always get a fresh token at the start of each session.

| Variable | Description |
|----------|-------------|
| `TANZU_HUB_URL` | Tanzu Hub URL (defaults to `https://tanzu-hub.kuhn-labs.com`) |
| `TANZU_HUB_USER` | Your Tanzu Hub username |
| `TANZU_HUB_PASSWORD` | Your Tanzu Hub password |

## Available MCP Tools

| Tool | Purpose | When to Use |
|------|---------|-------------|
| `tanzu_common_queries` | Pre-built patterns | **Try this first** for any standard operation |
| `tanzu_graphql_query` | Execute read queries | Custom queries not covered by common patterns |
| `tanzu_validate_query` | Validate query syntax | Before executing any custom query |
| `tanzu_graphql_mutate` | Execute mutations | Creating, updating, or deleting resources |
| `tanzu_explore_schema` | Discover schema | Only when you need to find unknown types/fields |
| `tanzu_find_entity_path` | Navigate relationships | Only when crossing unfamiliar entity boundaries |

## Choosing Efficient Patterns

**Before executing any query, determine if the question asks for COUNTS/AGGREGATIONS vs DETAILS:**

| Question Type | Pattern to Use | Example Questions |
|--------------|----------------|-------------------|
| **Counts/Aggregations** | `count_stopped_apps_by_space`, `summarize_app_states` | "How many stopped apps?", "Spaces with >2 stopped apps?" |
| **Summaries** | `spaces_summary`, `list_spaces` | "List all spaces", "Space overview" |
| **Full Details** | `spaces_with_apps`, `list_applications` | "Show me all apps in each space with their config" |

---

## Custom Query Construction

If no `tanzu_common_queries` pattern matches, read `reference/query-construction.md` for the full query syntax, naming conventions, entity hierarchy, and relationship navigation rules.

For domain-specific entity fields and properties, read the relevant domain file only when needed:
- TAS (foundations, orgs, spaces, apps): `domains/TAS.md`
- Spring Boot metadata: `domains/Spring.md`
- Alerts and observability: `domains/Observability.md`
- Vulnerabilities and CVEs: `domains/Security.md`
- Capacity and recommendations: `domains/Capacity.md`

For query templates and patterns:
- Read `patterns/common-queries.md` if you need a starting query template
- Read `patterns/filtering.md` if the request requires filtering by field values
- Read `patterns/pagination.md` if the result set may exceed a single page
- Read `patterns/entity-navigation.md` if traversing relationships between entity types
- Read `patterns/mutations.md` before executing any mutation

If the API returns an error, read `troubleshooting/error-recovery.md`. For queries that time out or return unexpected results, read `troubleshooting/anti-patterns.md`.
