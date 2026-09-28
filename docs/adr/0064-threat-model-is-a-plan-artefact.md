---
status: accepted
---

# ADR-064: The threat model is an artefact of the plan, not the build

The threat model in `docs/threat-model/` was run after the design decisions were made and before the spec was
written, so its findings could still change the design cheaply. It models the **decided design**, not the built
code. We keep it as a first-class document, point IM8 pm-6's data-flow evidence at it, and update it when a flow or
boundary changes. We do not delete it once the code exists, and we do not regenerate it from the build.

## What the model is

- An OWASP Threat Dragon v2 JSON model plus a written report, using STRIDE per data-flow-diagram element.
- Three diagrams:
  - origins, stores and trust boundaries;
  - the API drawn as its ordered filter bands;
  - the out-of-band privileged channels (the recovery runner and break-glass), which hold no session and never
    pass through the HTTP filter chain.
- Six trust boundaries and fourteen threats.
- LINDDUN was not run as a separate pass. The personal data in scope is one email address per account plus a
  client IP address, and both already have a decided privacy position (ADR-044, ADR-054). A LINDDUN pass would
  only restate them.

## What it deliberately does not restate

The model does not re-derive threats the design had already handled. An inherited threat is marked `Mitigated`
and cites the decision that owns it. So the `Open` set is exactly what the design decisions missed. That set falls
into three kinds:

- **composition:** two decisions that are each correct but combine badly;
- **band-level controls:** controls that run at a filter band, not at an endpoint, so they apply to paths that no
  endpoint-keyed registry covers;
- **late channels:** privileged channels designed after the flows they touch had already been modelled.

A reader looking for every threat to a login form will not find it here. That is deliberate. The design documents
(the ADRs, the deferral register and the test plan) carry those threats.

## Considered options

- **Delete it after the build, as a stale pre-build document.** This is the likeliest reversal. It removes the
  only data-flow and trust-boundary documentation the project has. The handover document refuses to carry IM8
  pm-6's network-topology and data-flow diagram *because* this model exists, so deleting the model turns that
  refusal into an unexplained gap.
- **Make it a living, build-generated model, gated for drift.** Nothing in the codebase can generate a trust
  boundary, an actor, or a composition finding. A gate over hand-drawn diagrams would test only that the file
  parses.
- **Keep it as a plan artefact with an update trigger (chosen).**

## Consequences

- **IM8 pm-6 (System Documentation, MUST)** requires architecture documentation, network topology and data-flow
  diagrams. The `im8-review` FAIL condition asks for *up-to-date* documentation and includes an agent-assessed
  check that documented data flows match the code. So this model is evidence only while it still describes the
  system. **Update trigger:** any change to a trust boundary, an external entity, a data store, or a privileged
  channel, and any new flow that crosses a boundary. The update edits the diagrams and re-runs STRIDE on the
  changed elements only.
- A finding that the built code departs from the model is reported against the model. It is not settled by
  editing the model to match the code.
- **Known limit:** the JSON is valid against the Threat Dragon v2 schema, but at the time of writing it has not
  been opened in a Threat Dragon instance. Its topology is right; its diagram geometry is plausible but has not
  been laid out.
