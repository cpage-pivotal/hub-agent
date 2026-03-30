# Error Recovery Guide

This document describes common errors and how to recover from them.

## Error Categories

1. **Syntax Errors** - Invalid GraphQL syntax
2. **Validation Errors** - Valid syntax but invalid schema usage
3. **Execution Errors** - Query fails during execution
4. **Authentication Errors** - Token or permission issues
5. **Network Errors** - Connection problems

## MOST COMMON ERRORS

### ERROR: Cannot query field "name" on type "*_Properties"

**Error Message**:
```json
{
  "errors": [{
    "message": "Cannot query field \"name\" on type \"Entity_Tanzu_TAS_Space_Properties\"."
  }]
}
```

**Cause**: Trying to use `properties.name` - this field does NOT exist!

**Fix**: Entity names are at the entity level, not in properties:
```graphql
# WRONG
node {
  properties {
    name  # This doesn't exist!
  }
}

# CORRECT - entityName is at entity level
node {
  entityName           # This is the entity's name!
  properties {
    guid               # Properties has guid, state, etc.
    state
  }
}
```

### ERROR: Cannot query field "contains" on type "*_RelOut"

**Error Message**:
```json
{
  "errors": [{
    "message": "Cannot query field \"contains\" on type \"Entity_Tanzu_TAS_Space_RelOut\"."
  }]
}
```

**Cause**: Using generic `contains` field which doesn't exist. Relationships use snake_case entity type names.

**Fix**: Use the correct entity-specific relationship field:
```graphql
# WRONG
relationshipsIn {
  contains {  # This doesn't exist!
    edges { ... }
  }
}

# CORRECT - use snake_case entity type name
relationshipsIn {
  isContainedIn {
    tanzu_tas_application(first: 100) {  # Use entity type name!
      edges {
        node {
          entityName
        }
      }
    }
  }
}
```

### ERROR: Fragment cannot be spread here

**Error Message**:
```json
{
  "errors": [{
    "message": "Fragment cannot be spread here as objects of type \"*_RelOut\" can never be of type \"Entity_Tanzu_*_Type\"."
  }]
}
```

**Cause**: Using inline fragments on relationship types that don't need them.

**Fix**: Relationship fields return typed connections, no fragments needed:
```graphql
# WRONG - fragments not needed here
relationshipsIn {
  isContainedIn {
    ... on Entity_Tanzu_TAS_Application_Type {  # Wrong place!
      entityName
    }
  }
}

# CORRECT - use the entity-specific field directly
relationshipsIn {
  isContainedIn {
    tanzu_tas_application(first: 100) {
      edges {
        node {
          entityName
          properties { state }
        }
      }
    }
  }
}
```

## Other Common Errors

### Unknown Field Error

**Error Message**:
```json
{
  "errors": [{
    "message": "Field 'unknownField' not found on type 'Entity_Tanzu_TAS_Application_Properties'"
  }]
}
```

**Cause**: Requested a field that doesn't exist on the type.

**Fix**:
1. Use `tanzu_explore_schema` to find correct field names:
   ```
   tanzu_explore_schema(typeName: "Entity_Tanzu_TAS_Application_Properties")
   ```
2. Check for typos in field names
3. Remember: `entityName` is at entity level, not properties!

### Unknown Type Error

**Error Message**:
```json
{
  "errors": [{
    "message": "Unknown type 'Entity_Tanzu_TAS_Application'"
  }]
}
```

**Cause**: Using incorrect type name (often missing `_Type` suffix).

**Fix**:
1. Add the `_Type` suffix: `Entity_Tanzu_TAS_Application_Type`
2. Check type naming conventions in `reference/type-naming.md`
3. Use `tanzu_explore_schema` to find correct type name

### Invalid Query Structure

**Error Message**:
```json
{
  "errors": [{
    "message": "Cannot query field 'Entity_Tanzu_TAS_Foundation_Type' on type 'EntityQuery'"
  }]
}
```

**Cause**: Not following the required query hierarchy.

**Fix**: Use the correct structure:
```graphql
query {
  entityQuery {
    typed {
      tanzu {
        tas {
          foundation {
            query(first: 10) {
              edges {
                node {
                  entityName
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

### Case Sensitivity Error

**Error Message**:
```json
{
  "errors": [{
    "message": "Cannot query field 'TAS' on type 'TanzuQuery'"
  }]
}
```

**Cause**: Using wrong case in query path (domains must be lowercase).

**Fix**: Use lowercase for query paths:
```graphql
# WRONG
tanzu { TAS { ... } }

# CORRECT
tanzu { tas { ... } }
```

### Missing Required Argument

**Error Message**:
```json
{
  "errors": [{
    "message": "Field 'query' argument 'first' is required"
  }]
}
```

**Cause**: Missing required pagination argument.

**Fix**: Add the required argument:
```graphql
query(first: 10) { ... }
```

### Authentication Error

**Error Message**:
```json
{
  "errors": [{
    "message": "Not authenticated"
  }]
}
```

**Cause**: The MCP server could not obtain a valid token from the credential broker.

**Fix**:
1. Verify the credential broker is reachable and the delegation token is valid
2. Ensure the user has granted access to the `tanzu-hub` target system in the broker UI
3. Check that the hub-mcp app has valid CF instance identity certificates (for mTLS)

### Query Complexity Exceeded

**Error Message**:
```json
{
  "errors": [{
    "message": "Query complexity exceeds maximum allowed"
  }]
}
```

**Cause**: Query requests too much data.

**Fix**:
1. Reduce `first` argument values
2. Remove unnecessary fields
3. Break into multiple smaller queries
4. Avoid deep nesting

### Timeout Error

**Error Message**:
```json
{
  "errors": [{
    "message": "Query execution timeout"
  }]
}
```

**Cause**: Query took too long to execute.

**Fix**:
1. Reduce scope of query
2. Add more restrictive filters
3. Use pagination with smaller page sizes
4. Simplify relationship traversal

## Error Recovery Workflow

### Step 1: Identify Error Type

Read the error message carefully:
- "Field not found" → Field name issue (check if using `properties.name`)
- "Cannot query field contains" → Use entity-specific field like `tanzu_tas_application`
- "Unknown type" → Type name issue
- "Not authenticated" → Token issue
- "Timeout" → Performance issue

### Step 2: Check the Most Common Mistakes

1. **Are you using `properties.name`?** → Use `entityName` at entity level
2. **Are you using `contains`?** → Use `tanzu_tas_{entity}` fields
3. **Are you using inline fragments on relationships?** → Use entity-specific fields

### Step 3: Use Validation

Before fixing, validate the corrected query:
```
tanzu_validate_query(
  query: "your corrected query",
  suggestFixes: true
)
```

### Step 4: Check Schema

Use schema exploration to verify names:
```
tanzu_explore_schema(
  search: "your search term",
  domain: "TAS"
)
```

### Step 5: Try Common Patterns

Reference working patterns in `patterns/common-queries.md`.

The `spaces_with_apps` pattern is especially useful for questions about spaces and their applications.

### Step 6: Simplify and Build Up

If a complex query fails:
1. Start with simplest possible query
2. Verify it works
3. Add complexity incrementally
4. Identify which addition causes failure

## Quick Reference: Correct Patterns

### Entity Name
```graphql
# Use entityName at entity level
node {
  entityName
  properties { guid state }
}
```

### Relationship Navigation
```graphql
# Use snake_case entity type field
relationshipsIn {
  isContainedIn {
    tanzu_tas_application(first: 100) {
      edges {
        node {
          entityName
        }
      }
    }
  }
}
```

### Relationship Field Names
| To Get | Use Field |
|--------|-----------|
| Applications | `tanzu_tas_application` |
| Spaces | `tanzu_tas_space` |
| Organizations | `tanzu_tas_organization` |
| Foundations | `tanzu_tas_foundation` |

## Prevention Strategies

1. **Always validate first** - Use `tanzu_validate_query` before `tanzu_graphql_query`
2. **Use common patterns** - Start from known working queries in `patterns/common-queries.md`
3. **Remember entityName** - Entity names are at entity level, not `properties.name`
4. **Remember snake_case** - Relationship fields use `tanzu_tas_{entity}` format
5. **Check schema** - Verify type and field names exist
6. **Start simple** - Build complex queries incrementally
