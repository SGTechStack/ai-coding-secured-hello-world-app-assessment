# Edge Cases to Probe

Only probe edge cases relevant to the active acceptance criteria or visible app behaviour.

| Edge Case | When Applicable | How to Test |
|-----------|-----------------|-------------|
| Empty state | AC mentions no records, no results, or first-use behaviour | Navigate to relevant view and verify clear empty-state UI |
| Validation error | AC includes forms or user input | Submit invalid or missing values and verify inline or summary errors |
| Successful submission | AC includes create or update action | Submit valid values and verify confirmation, navigation, or persisted state |
| Auth boundary | AC mentions roles, login, sessions, or permissions | Verify unauthenticated redirect or restricted controls as applicable |
| Loading state | AC includes async fetch, save, refresh, or search | Trigger the action and verify loading or progress state if observable |
| Navigation / deep link | Feature adds routes or links | Open direct URL and navigate through visible app controls |
| Responsive layout | User-facing web UI | Check at least one desktop and one mobile viewport when supported by tooling |
| Backend unavailable | UI depends on a service and failure handling is specified | Exercise the view with service unavailable only if safe and reversible |
