# 09. DirtyFileBlob Lifecycle

**Goal**: Prevent resource leaks so no dirty blob survives past successful handoff or a terminal state.

**Risk**: If dirty blobs are deleted only on some terminal transitions, timeout and retry-exhaustion paths can leave orphaned `DirtyFileBlob` rows indefinitely, consuming storage and making quota calculations inaccurate.

**Recommended implementation**: Make dirty-blob deletion mandatory on every terminal state transition and on every successful handoff, enforced through `StateMachineService` rather than scattered across individual adapters. `deleteDirty` must be idempotent — no error if the blob is already absent.

**Reason**: A single invariant ("zero dirty blobs after terminal state") is easier to verify and test than auditing every adapter path individually. Idempotent deletes prevent double-delete exceptions during retry scenarios.

**Invariant**:

After any file reaches a terminal state, zero `DirtyFileBlob` records shall exist.

**Requirements**:

* `deleteDirty` and `deleteClean` must be idempotent.

**Profile-specific lifecycle tables**:

* AWS multi-phase lifecycle: [09. AWS DirtyFileBlob Lifecycle](../aws/09-aws-dirtyfileblob-lifecycle.md)
* Standalone local promotion lifecycle: [09. Standalone DirtyFileBlob Lifecycle](../standalone/09-standalone-dirtyfileblob-lifecycle.md)
