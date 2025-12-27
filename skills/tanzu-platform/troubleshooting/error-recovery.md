# Error Recovery Guide

This document describes common errors and how to recover from them.

## Error Categories

1. **Syntax Errors** - Invalid GraphQL syntax
2. **Validation Errors** - Valid syntax but invalid schema usage
3. **Execution Errors** - Query fails during execution
4. **Authentication Errors** - Token or permission issues
5. **Network Errors** - Connection problems

## Common Errors and Fixes

### 1. Unknown Field Error

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
3. Look for "did you mean" suggestions from `tanzu_validate_query`

### 2. Unknown Type Error

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

### 3. Invalid Query Structure

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
            query(first: 10) { ... }
          }
        }
      }
    }
  }
}
```

### 4. Case Sensitivity Error

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

### 5. Missing Required Argument

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

### 6. Authentication Error

**Error Message**:
```json
{
  "errors": [{
    "message": "Not authenticated"
  }]
}
```

**Cause**: Missing or invalid bearer token.

**Fix**:
1. Check `TOKEN` environment variable is set
2. Verify token is not expired
3. Ensure token has correct permissions

### 7. Authorization Error

**Error Message**:
```json
{
  "errors": [{
    "message": "Not authorized to access this resource"
  }]
}
```

**Cause**: Token lacks required permissions.

**Fix**:
1. Verify user has access to the resource
2. Check organization/space permissions
3. Request elevated permissions if needed

### 8. Query Complexity Exceeded

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

### 9. Timeout Error

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

### 10. Network/Connection Error

**Error Message**:
```
Connection refused: https://tanzu-hub.kuhn-labs.com/hub/graphql
```

**Cause**: Cannot reach the Tanzu Platform API.

**Fix**:
1. Check network connectivity
2. Verify `TANZU_PLATFORM_URL` is correct
3. Check for firewall/proxy issues
4. Verify the service is running

## Error Recovery Workflow

### Step 1: Identify Error Type

Read the error message carefully:
- "Field not found" → Field name issue
- "Unknown type" → Type name issue
- "Not authenticated" → Token issue
- "Timeout" → Performance issue

### Step 2: Use Validation

Before fixing, validate the corrected query:
```
tanzu_validate_query(
  query: "your corrected query",
  suggestFixes: true
)
```

### Step 3: Check Schema

Use schema exploration to verify names:
```
tanzu_explore_schema(
  search: "your search term",
  domain: "TAS"
)
```

### Step 4: Try Common Patterns

Reference working patterns in `patterns/common-queries.md`.

### Step 5: Simplify and Build Up

If a complex query fails:
1. Start with simplest possible query
2. Verify it works
3. Add complexity incrementally
4. Identify which addition causes failure

## Self-Correction with tanzu_validate_query

The `tanzu_validate_query` tool provides "did you mean" suggestions:

```json
{
  "valid": false,
  "errors": [{
    "type": "UNKNOWN_FIELD",
    "message": "Field 'nme' not found on type '...Properties'"
  }],
  "suggestions": [
    "Unknown field 'nme'. Did you mean: name?"
  ]
}
```

Use these suggestions to correct queries before execution.

## Prevention Strategies

1. **Always validate first** - Use `tanzu_validate_query` before `tanzu_graphql_query`
2. **Use common patterns** - Start from known working queries
3. **Check schema** - Verify type and field names exist
4. **Start simple** - Build complex queries incrementally
5. **Handle pagination** - Include `pageInfo` to manage large results
