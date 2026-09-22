# 09. DirtyFileBlob Lifecycle (Standalone)

See [09. DirtyFileBlob Lifecycle](../shared/09-dirtyfileblob-lifecycle-consolidated.md) for the shared invariant and requirements.

| Trigger / State Transition | DirtyFileBlob Action | Location |
|:---|:---|:---|
| Local promotion: `PENDING_SCAN` -> `DOWNLOADED` | DELETE | Upload listener |

**Requirements**:

* Local promotion deletes dirty content only after the clean artifact has been written and the metadata transition to `DOWNLOADED` can be committed. If promotion fails after dirty persistence, the file must remain unavailable to consumers and the dirty artifact must be cleaned up or exposed to an explicit recovery procedure.
