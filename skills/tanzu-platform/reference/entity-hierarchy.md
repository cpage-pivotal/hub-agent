# Entity Hierarchy Reference

This document provides a visual reference for entity relationships in the Tanzu Platform.

## TAS Entity Hierarchy

```
Tanzu Platform
│
├── Foundation Groups
│   └── [Group of foundations]
│
└── Foundations (TAS)
    │
    ├── Organizations
    │   │
    │   └── Spaces
    │       │
    │       ├── Applications
    │       │   ├── Routes
    │       │   ├── Service Bindings
    │       │   └── Environment Variables
    │       │
    │       ├── Service Instances
    │       │
    │       └── Security Groups
    │
    ├── BOSH Directors
    │   └── Deployments
    │       └── VMs
    │
    ├── Ops Managers
    │   └── Tiles
    │
    └── Management Endpoints
```

## Entity Relationship Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                          Platform                                    │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ contains
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                          Foundation                                  │
│  Entity_Tanzu_TAS_Foundation_Type                                   │
│  - name, api_endpoint, version                                      │
└─────────────────────────────────────────────────────────────────────┘
                    │                               │
                    │ contains                      │ contains
                    ▼                               ▼
┌───────────────────────────────┐   ┌───────────────────────────────┐
│         Organization          │   │        BOSH Director          │
│ Entity_Tanzu_TAS_Org_Type    │   │ Entity_Tanzu_TAS_BOSH..._Type │
│ - name, quota                 │   │ - name, version               │
└───────────────────────────────┘   └───────────────────────────────┘
              │
              │ contains
              ▼
┌───────────────────────────────┐
│           Space               │
│ Entity_Tanzu_TAS_Space_Type  │
│ - name, organization          │
└───────────────────────────────┘
              │
              │ contains
              ▼
┌───────────────────────────────┐
│        Application            │
│ Entity_Tanzu_TAS_App_Type    │
│ - name, state, instances,     │
│   memory, disk_quota          │
└───────────────────────────────┘
```

## Containment Relationships

| Parent Entity | Child Entity | Relationship |
|---------------|--------------|--------------|
| Platform | Foundation | `contains` / `isContainedIn` |
| Foundation | Organization | `contains` / `isContainedIn` |
| Organization | Space | `contains` / `isContainedIn` |
| Space | Application | `contains` / `isContainedIn` |
| Foundation | BOSH Director | `contains` / `isContainedIn` |
| Foundation | Ops Manager | `contains` / `isContainedIn` |

## Navigation Paths

### From Application to Foundation (Upward)

```
Application
    │
    │ relationshipsOut.isContainedIn
    ▼
Space
    │
    │ relationshipsOut.isContainedIn
    ▼
Organization
    │
    │ relationshipsOut.isContainedIn
    ▼
Foundation
```

### From Foundation to Applications (Downward)

```
Foundation
    │
    │ relationshipsIn.contains
    ▼
Organizations (list)
    │
    │ relationshipsIn.contains
    ▼
Spaces (list)
    │
    │ relationshipsIn.contains
    ▼
Applications (list)
```

## Cross-Domain Relationships

```
┌─────────────────────────┐
│     TAS Application     │
│                         │
└───────────┬─────────────┘
            │
            │ isAssociatedWith
            ▼
┌─────────────────────────┐     ┌─────────────────────────┐
│    Spring Artifact      │────▶│    Vulnerability        │
│   (if Spring app)       │     │   (Security domain)     │
└─────────────────────────┘     └─────────────────────────┘
```

## Entity Type Naming

All TAS entity types follow the pattern:

```
Entity_Tanzu_TAS_{EntityName}_Type
```

Examples:
- `Entity_Tanzu_TAS_Foundation_Type`
- `Entity_Tanzu_TAS_Organization_Type`
- `Entity_Tanzu_TAS_Space_Type`
- `Entity_Tanzu_TAS_Application_Type`
- `Entity_Tanzu_TAS_BOSHDirector_Type`
- `Entity_Tanzu_TAS_OpsManager_Type`

## Supporting Types

Each entity type has associated types:

| Base Entity | Query Type | Connection Type | Edge Type | Properties Type |
|-------------|------------|-----------------|-----------|-----------------|
| `...Foundation_Type` | `...Foundation_Query` | `...FoundationConnection` | `...FoundationEdge` | `...Foundation_Properties` |
| `...Application_Type` | `...Application_Query` | `...ApplicationConnection` | `...ApplicationEdge` | `...Application_Properties` |

## Relationship Types

Each entity has RelIn and RelOut types:

| Entity | RelIn Type | RelOut Type |
|--------|------------|-------------|
| `...Foundation_Type` | `...Foundation_RelIn` | `...Foundation_RelOut` |
| `...Application_Type` | `...Application_RelIn` | `...Application_RelOut` |

## Depth Reference

| Starting Point | Destination | Depth (steps) |
|----------------|-------------|---------------|
| Application | Space | 1 |
| Application | Organization | 2 |
| Application | Foundation | 3 |
| Space | Organization | 1 |
| Space | Foundation | 2 |
| Organization | Foundation | 1 |
| Foundation | Organization | 1 |
| Foundation | Space | 2 |
| Foundation | Application | 3 |

## Query Structure Reference

For querying entities directly:

```
entityQuery
└── typed
    └── tanzu
        └── {domain}           # e.g., tas, spring
            └── {entityType}   # e.g., foundation, application
                └── query(...)
                    └── edges
                        └── node
```

For navigating relationships:

```
node
└── relationshipsOut (or relationshipsIn)
    └── {relationshipType}     # e.g., isContainedIn, contains
        └── edges
            └── node
                └── ... on {TargetEntityType}
```
