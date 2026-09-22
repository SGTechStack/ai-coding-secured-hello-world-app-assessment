# 04. Display Name Validation

**Goal**: Validate filenames to prevent path traversal and injection attacks.


**Pattern**: `^[a-zA-Z0-9_\- ]+$` — Allows alphanumerics, underscores, hyphens, and spaces.

```java
// JPA Entity Annotation
@Column(nullable = false)
@Pattern(regexp = "^[a-zA-Z0-9_\\- ]+$")
private String displayName;

// Validation check
public void validateDisplayName(String displayName) {
    if (!displayName.matches("^[a-zA-Z0-9_\\- ]+$")) {
        throw new FileValidationException("Display name contains invalid characters.");
    }
}
```

> **Fix**: Broader pattern adopted (legacy said "alphanumeric only" but code allowed spaces/hyphens).

