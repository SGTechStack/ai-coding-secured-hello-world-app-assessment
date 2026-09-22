# 18. AWS Distributed Scheduling (ShedLock)

**Goal**: Coordinate scheduled lifecycle jobs only when more than one node may run the same scheduler.

**SHEDLOCK Table (DDL)**:

| Field | Type | Constraint |
|:---|:---|:---|
| `name` | `VARCHAR(64)` | PK |
| `lock_until` | `TIMESTAMP` | NOT NULL |
| `locked_at` | `TIMESTAMP` | NOT NULL |
| `locked_by` | `VARCHAR(255)` | NOT NULL |

**Configuration**:

```yaml
file:
  scanner:
    lock-at-most-for: 10m
    lock-at-least-for: 30s
```

**Clock Source Guidance**:

* Prefer database-backed time in AWS so lock ownership does not depend on node-local clock drift.
* Use ShedLock only when scheduler ownership is genuinely shared.
* Do not add ShedLock to a dedicated batch-owner deployment just for consistency.

**Logic**:

1. Multiple nodes attempt to start the lifecycle scheduler.
2. Only one node acquires the `SHEDLOCK` row and executes the owned phase.
3. The lock is released after execution or after `lock-at-most-for`.

Standalone never uses ShedLock. AWS only needs it when scheduled lifecycle ownership is shared across nodes.
