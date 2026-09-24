# Fix Recommendation Patterns

| Mutation Pattern | Surviving Example | Recommended Fix |
|------------------|-------------------|-----------------|
| Conditional changed/removed | Branch still passes when condition flips | Test both branch outcomes and boundary values |
| Boolean flipped | `true` becomes `false` but test passes | Assert exact state and user-visible result |
| Math/operator changed | `+` becomes `-`, `>` becomes `>=` | Assert exact computed values and edge cases |
| Return value changed | Empty/null/default value still accepted | Assert exact return and error behaviour |
| Function/method call removed | Side effect disappears unnoticed | Verify persisted state, emitted event, callback, or mock interaction |
| Constant changed | Limit, default, or label changes unnoticed | Test externally visible behaviour, not duplicated implementation constants |
| Exception removed/changed | Invalid input no longer fails correctly | Assert error type, message, status, or validation result |
| Async behaviour changed | Promise/task/callback path survives | Assert eventual state and failure path |
