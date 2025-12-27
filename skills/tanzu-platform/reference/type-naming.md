# Type Naming Conventions

This document explains the naming conventions used in the Tanzu Platform GraphQL API schema.

## Overview

The Tanzu Platform schema contains 1,382 types. Understanding naming conventions is essential for:
- Constructing valid queries
- Navigating the schema
- Avoiding common errors

## Entity Type Naming

### Pattern

```
Entity_Tanzu_{Domain}_{EntityName}_Type
```

### Components

| Component | Description | Example |
|-----------|-------------|---------|
| `Entity_` | Prefix for all entity types | `Entity_` |
| `Tanzu_` | Namespace identifier | `Tanzu_` |
| `{Domain}_` | Domain identifier (PascalCase) | `TAS_`, `Spring_` |
| `{EntityName}` | Entity name (PascalCase) | `Foundation`, `Application` |
| `_Type` | Suffix for entity types | `_Type` |

### Examples

| Entity | Full Type Name |
|--------|----------------|
| Foundation | `Entity_Tanzu_TAS_Foundation_Type` |
| Organization | `Entity_Tanzu_TAS_Organization_Type` |
| Space | `Entity_Tanzu_TAS_Space_Type` |
| Application | `Entity_Tanzu_TAS_Application_Type` |
| Spring Artifact | `Entity_Tanzu_Spring_Artifact_Type` |

## Domain Identifiers

| Domain | Identifier in Type Names | Query Path |
|--------|-------------------------|------------|
| Tanzu Application Service | `TAS` | `tas` |
| Spring | `Spring` | `spring` |
| Platform | `Platform` | `platform` |
| TKG | `TKG` | `tkg` |
| TMC | `TMC` | `tmc` |

**Important**: In type names, domains use PascalCase (`TAS`). In query paths, domains use lowercase (`tas`).

## Acronym Handling

Known acronyms remain UPPERCASE in type names:

| Acronym | Meaning | Example |
|---------|---------|---------|
| TAS | Tanzu Application Service | `Entity_Tanzu_TAS_...` |
| TKG | Tanzu Kubernetes Grid | `Entity_Tanzu_TKG_...` |
| TMC | Tanzu Mission Control | `Entity_Tanzu_TMC_...` |
| BOSH | BOSH Director | `...TAS_BOSHDirector_Type` |
| VM | Virtual Machine | `...TAS_VM_Type` |
| API | Application Programming Interface | Various |
| ID | Identifier | Field names |

## Supporting Type Suffixes

| Suffix | Purpose | Example |
|--------|---------|---------|
| `_Type` | Entity type | `Entity_Tanzu_TAS_Foundation_Type` |
| `_Query` | Query entry point | `Entity_Tanzu_TAS_Foundation_Query` |
| `Connection` | Paginated list | `Entity_Tanzu_TAS_FoundationConnection` |
| `Edge` | Edge in connection | `Entity_Tanzu_TAS_FoundationEdge` |
| `_Properties` | Entity properties | `Entity_Tanzu_TAS_Foundation_Properties` |
| `_RelIn` | Incoming relationships | `Entity_Tanzu_TAS_Foundation_RelIn` |
| `_RelOut` | Outgoing relationships | `Entity_Tanzu_TAS_Foundation_RelOut` |
| `Input` | Mutation input | `AlertCreateInput` |

## Query Path vs Type Name

| Context | Convention | Example |
|---------|------------|---------|
| Query path | lowercase | `tanzu.tas.foundation` |
| Type name | PascalCase | `Entity_Tanzu_TAS_Foundation_Type` |
| Field name | camelCase | `relationshipsOut`, `isContainedIn` |

### Query Path Structure

```graphql
query {
  entityQuery {
    typed {
      tanzu {              # lowercase
        tas {              # lowercase
          foundation {     # lowercase
            query(first: 10) { ... }
          }
        }
      }
    }
  }
}
```

### Type Name in Fragments

```graphql
... on Entity_Tanzu_TAS_Foundation_Type {  # PascalCase
  properties {
    name
  }
}
```

## Relationship Field Naming

Relationship fields use camelCase:

| Field | Usage |
|-------|-------|
| `relationshipsIn` | Incoming relationships |
| `relationshipsOut` | Outgoing relationships |
| `isContainedIn` | Child-to-parent containment |
| `contains` | Parent-to-child containment |
| `isAssociatedWith` | Peer association |
| `isDeployedBy` | Deployment relationship |

## Property Field Naming

Properties within `properties` objects use snake_case or camelCase depending on the field:

```graphql
properties {
  name           # camelCase
  state          # camelCase
  disk_quota     # snake_case (varies)
  instances      # camelCase
}
```

## Converting Between Formats

### From Query Path to Type Name

1. Start with `Entity_Tanzu_`
2. Convert domain to uppercase: `tas` → `TAS_`
3. Convert entity to PascalCase: `foundation` → `Foundation`
4. Add `_Type` suffix

Example: `tanzu.tas.foundation` → `Entity_Tanzu_TAS_Foundation_Type`

### From Type Name to Query Path

1. Remove `Entity_Tanzu_` prefix
2. Extract domain (e.g., `TAS`) and convert to lowercase: `tas`
3. Extract entity name and convert to lowercase: `Foundation` → `foundation`
4. Remove `_Type` suffix

Example: `Entity_Tanzu_TAS_Foundation_Type` → `tanzu.tas.foundation`

## Common Mistakes

### Wrong Case in Query Path

```graphql
# WRONG
tanzu {
  TAS {  # Should be lowercase
    Foundation { ... }  # Should be lowercase
  }
}

# CORRECT
tanzu {
  tas {
    foundation { ... }
  }
}
```

### Wrong Case in Type Name

```graphql
# WRONG
... on entity_tanzu_tas_foundation_type { ... }  # Should be PascalCase

# CORRECT
... on Entity_Tanzu_TAS_Foundation_Type { ... }
```

### Missing Suffix

```graphql
# WRONG
... on Entity_Tanzu_TAS_Foundation { ... }  # Missing _Type suffix

# CORRECT
... on Entity_Tanzu_TAS_Foundation_Type { ... }
```

### Wrong Acronym Case

```graphql
# WRONG
Entity_Tanzu_Tas_Foundation_Type  # TAS should be uppercase

# CORRECT
Entity_Tanzu_TAS_Foundation_Type
```

## Using tanzu_explore_schema

When unsure about type names, use the explore tool:

```
tanzu_explore_schema(
  search: "foundation",
  domain: "TAS"
)
```

This returns matching types with correct naming.
