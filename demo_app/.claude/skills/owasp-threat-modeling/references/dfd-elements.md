# DFD Elements and STRIDE Applicability

## Element Types

| Element | Symbol | Description |
|---------|--------|-------------|
| Process | Circle / rounded rectangle | Applications, microservices, API endpoints that transform data |
| Data Store | Parallel lines | Databases, file systems, caches, message queues that persist data |
| External Entity | Rectangle | Users, external systems, third-party services outside the trust boundary |
| Data Flow | Arrow | Communication channels between elements showing data direction |
| Trust Boundary | Dashed line | Separates zones of different trust levels (internet/DMZ/internal, user/admin) |

## STRIDE Applicability by Element

| Element Type | S | T | R | I | D | E |
|---|---|---|---|---|---|---|
| External Entity | X | | X | | | |
| Process | X | X | X | X | X | X |
| Data Store | | X | | X | X | |
| Data Flow | | X | | X | X | |

## Threat Model File Format

Threat Dragon stores models as JSON — commit alongside the code they describe.

```json
{
  "version": "2.2.0",
  "summary": {
    "title": "Feature Name",
    "owner": "Security Team",
    "description": "Threat model for the <feature> flow"
  },
  "detail": {
    "contributors": [{"name": "Author"}],
    "diagrams": [
      {
        "id": 0,
        "title": "Level 0 DFD",
        "diagramType": "STRIDE",
        "cells": []
      }
    ]
  }
}
```

Threat Dragon participates in the CycloneDX TMBOM standard, enabling export to a common format consumable by other threat modeling tools and GRC platforms.
