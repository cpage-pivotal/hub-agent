# Query Construction Reference

Use this file when no `tanzu_common_queries` pattern covers the request and you need to build a custom GraphQL query.

## Query Structure

Queries **MUST** follow this hierarchy:

```
entityQuery → typed → tanzu → {domain} → {entityType} → query(...)
```

```graphql
query {
  entityQuery {
    typed {
      tanzu {
        tas {                           # Domain (lowercase)
          foundation {                  # Entity type (lowercase)
            query(first: 10) {          # Query method with pagination
              edges {
                node {
                  id
                  entityName            # Name is at entity level, NOT properties!
                }
              }
              pageInfo {
                hasNextPage
                endCursor
              }
            }
          }
        }
      }
    }
  }
}
```

## CRITICAL: Entity Name vs Properties

**The entity name is `entityName` at the entity level, NOT `properties.name`!**

```graphql
# CORRECT
node {
  entityName                          # This is the entity's name!
  properties {
    guid                              # Properties has guid, state, etc.
    state
  }
}

# WRONG - name does NOT exist on properties
node {
  properties {
    name                              # ERROR: field "name" doesn't exist!
  }
}
```

## Naming Conventions

| Component | Convention | Example |
|-----------|------------|---------|
| Domains | lowercase | `tas`, `spring`, `platform` |
| Entity types in queries | lowercase | `foundation`, `application`, `space` |
| Entity type names | PascalCase with `_Type` suffix | `Entity_Tanzu_TAS_Foundation_Type` |
| Properties types | PascalCase with `_Properties` suffix | `Entity_Tanzu_TAS_Foundation_Properties` |
| Relationship entity fields | snake_case | `tanzu_tas_application`, `tanzu_tas_space` |

## Entity Hierarchy

```
Platform
└── Foundation Groups
    └── Foundations (TAS)
        ├── Organizations
        │   └── Spaces
        │       └── Applications
        ├── BOSH Directors
        └── Ops Managers
```

## Relationship Navigation

Relationships use **snake_case entity type names**, NOT generic `contains`:

| To Navigate | Use Field |
|-------------|-----------|
| Space → Applications | `relationshipsIn.isContainedIn.tanzu_tas_application` |
| Organization → Spaces | `relationshipsIn.isContainedIn.tanzu_tas_space` |
| Foundation → Organizations | `relationshipsIn.isContainedIn.tanzu_tas_organization` |
| Application → Space | `relationshipsOut.isContainedIn.tanzu_tas_space` |
| Space → Organization | `relationshipsOut.isContainedIn.tanzu_tas_organization` |

- **`relationshipsIn`**: Children (entities contained IN this entity)
- **`relationshipsOut`**: Parents (entities this entity IS CONTAINED IN)

## Common Entity Fields

```graphql
node {
  id                    # Opaque global ID
  entityId              # Canonical entity identifier
  entityName            # Human-readable name (THIS IS THE NAME!)
  entityType            # Type discriminator
  properties { ... }    # Type-specific properties (no 'name' field!)
  relationshipsIn { ... }   # Incoming relationships
  relationshipsOut { ... }  # Outgoing relationships
  tags { key value }    # Key/value tags
}
```

## Critical Rules

1. **Entity name is `entityName`** at entity level, NOT `properties.name`
2. **Relationship fields use snake_case** like `tanzu_tas_application`, NOT `contains`
3. **Always validate custom queries** before execution using `tanzu_validate_query`
4. **Request only needed fields** to avoid complexity limits
5. **Handle pagination** with `first: N` at each query level
6. **Domains and entity types are lowercase** in queries
