# Mental Models for Pragmatic Review

## Inversion

Inversion is the practice of thinking about a problem from the opposite perspective. Instead of asking "How can I make this better?", ask "How can this fail if I change nothing?" or "What is the worst-case scenario if this flaw remains?".

### Examples

- **Strategy**: "Our plan doesn't include a backup marketing agency."
- **Inversion**: "If we don't hire a backup, and our main agency performs well, what fails? Nothing. If they fail, do we have internal capacity to bridge the gap? Yes. Then hiring a backup might be Noise (unnecessary cost/complexity)."
- **Engineering**: "This component doesn't use the latest architectural pattern."
- **Inversion**: "If we keep the current pattern, what breaks? Does it prevent scaling? Is it causing bugs? No? Then refactoring might be Noise."

## Second-Order Thinking

Second-order thinking is considering the consequences of your consequences. Most people stop at the first order (the immediate result). A pragmatic reviewer looks at the second, third, and fourth orders.

### Examples

- **Policy Fix**: "Let's implement a strict approval process for all budget changes to prevent overspending."
- **First-Order**: Budget overspending is reduced.
- **Second-Order**: Team velocity slows down as people wait for approvals.
- **Third-Order**: High-performers feel micromanaged and less autonomous.
- **Fourth-Order**: Talent starts to leave, leading to a long-term decline in productivity that exceeds the original budget savings.
- **Verdict**: If the downstream costs outweigh the immediate benefits, the "fix" is Noise or even harmful.

## Signal vs. Noise Classification

### Signal (Must Address)

- **Fundamental Correctness**: Logical flaws that invalidate the entire proposal or system.
- **Critical Risk**: Security, safety, or legal risks that could lead to catastrophic failure.
- **Core Goal Impediment**: Issues that directly prevent the primary objective from being achieved.
- **Systemic Fragility**: Flaws that make the system brittle and prone to cascading failures.

### Noise (Acceptable Trade-off)

- **Subjective Preference**: "I would have worded this differently" or "I prefer a different color."
- **Speculative Generality**: Solving for "what if" scenarios that have low probability or impact (YAGNI).
- **Surface-Level Consistency**: Enforcing strict adherence to secondary standards that don't impact the outcome.
- **Diminishing Returns**: Fixes where the cost of implementation and maintenance exceeds the value provided.
