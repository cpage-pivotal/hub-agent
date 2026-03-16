---
name: tanzu-platform
description: "AUTONOMOUS skill for Tanzu Platform queries. ALWAYS load and follow this skill when the user mentions Tanzu, TAS, foundations, organizations, spaces, or applications. Credentials are pre-configured — run `python3 .goose/skills/tanzu-hub/scripts/get-token.py` to get a token, then call tanzu_common_queries with the token. NEVER ask the user for a token or credentials."
compatibility: Requires Python 3 (stdlib only) and the tanzu-platform MCP server (hub). TANZU_HUB_URL, TANZU_HUB_USER, and TANZU_HUB_PASSWORD are pre-configured as environment variables.
---

# Tanzu Platform Skill

This skill is fully autonomous. Credentials are pre-configured as environment variables. DO NOT ask the user for tokens, credentials, or manual steps. DO NOT explore the environment, search for files, or read config. Execute the steps below immediately.

## Step 1: Get a token

Run this exact command — do not search for the script, it is already installed at this path:

```bash
python3 .goose/skills/tanzu-hub/scripts/get-token.py
```

The script reads TANZU_HUB_URL, TANZU_HUB_USER, and TANZU_HUB_PASSWORD from environment variables (already configured) and prints a raw JWT token to stdout. Capture that token. Pass it to MCP tools as-is — no "Bearer " prefix.

If a tool returns an auth error, re-run the script for a fresh token (tokens expire after 30 min).

## Step 2: Call the right MCP tool

Match the user's request to a `tanzu_common_queries` pattern:

| Request | Pattern |
|---|---|
| List foundations | `list_foundations` |
| Find foundation by name | `get_foundation_by_name` |
| List organizations | `list_organizations` |
| List spaces | `list_spaces` or `spaces_summary` |
| List applications | `list_applications` |
| Find stopped apps | `find_stopped_apps` |
| Stopped apps per space | `count_stopped_apps_by_space` |
| App state distribution | `summarize_app_states` |
| Spaces with apps | `spaces_with_apps` |
| Service bindings | `list_service_bindings` |
| Vulnerabilities | `find_vulnerabilities` |
| Critical CVEs | `find_critical_cves` |
| Insights | `list_insights` |
| Artifact SBOM | `get_artifact_sbom` |
| Alerts | `list_alerts` |
| Capacity | `check_capacity` |
| Spring apps | `list_spring_apps` |
| App health | `get_app_health` |

Call it: `tanzu_common_queries(pattern: "list_foundations", token: "<token>")`

If no pattern matches, build a custom query — see "Custom Queries" below.

## Step 3: Present results

Format the response for the user. Use tables for lists, bullet points for details.

## Gotchas

- Entity names are `entityName`, NOT `properties.name`.
- Relationships use snake_case: `tanzu_tas_application`, not `contains`.
- For aggregation, prefer `count_stopped_apps_by_space` or `summarize_app_states` over `spaces_with_apps`.
- Auth errors → re-run get-token.py and retry.

## Custom Queries

When no common pattern matches:

1. Read `reference/query-construction.md` for syntax
2. Read the domain file: `domains/TAS.md`, `domains/Spring.md`, `domains/Observability.md`, `domains/Security.md`, or `domains/Capacity.md`
3. Validate with `tanzu_validate_query`, then execute with `tanzu_graphql_query`
4. On errors, read `troubleshooting/error-recovery.md`

## MCP Tools

| Tool | Purpose |
|------|---------|
| `tanzu_common_queries` | Pre-built patterns — try first |
| `tanzu_graphql_query` | Custom read queries |
| `tanzu_validate_query` | Validate before executing |
| `tanzu_graphql_mutate` | Create/update/delete |
| `tanzu_explore_schema` | Discover types and fields |
| `tanzu_find_entity_path` | Find entity relationships |
