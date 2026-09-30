# Office Panda Meltdown - Implementation Contract

Target: replace the static crying panda in the login page with a ~12 s seamless
"office panda meltdown" loop, split across five leaf modules plus one integrator
file. Four authors can work in parallel; the integrator owns the wiring.

Repo root: C:\chris\dev\temp\Lab\chris\ai-coding-secured-hello-world-app-assessment
Frontend: ./frontend (React 19 + Vite 8 + TypeScript 6, three@^0.186.1).

## 0. Ownership and parallelism

| File | Owner role | Depends only on |
| --- | --- | --- |
| frontend/src/components/panda-scene/easing.ts | Author E (math) | nothing |
| frontend/src/components/panda-scene/choreography.ts | Author C (timeline) | easing.ts |
| frontend/src/components/panda-scene/panda.ts | Author P (rig) | easing.ts, choreography.ts (Pose type only) |
| frontend/src/components/panda-scene/office.ts | Author O (props) | easing.ts |
| frontend/src/components/panda-scene/tears.ts | Author T (fx) | easing.ts |
| frontend/src/components/crying-panda-scene.ts | Integrator | all of the above |
| frontend/src/components/crying-panda.tsx | Integrator | - |
| frontend/src/components/crying-panda.css | Integrator | - |

RULE: only the Integrator edits crying-panda-scene.ts, crying-panda.tsx and
crying-panda.css. Authors E/C/P/O/T never touch those three files, and never
edit each other's module. choreography.ts is the single source of truth for the
Pose type and all named poses; panda.ts imports it, never redefines it.

TypeScript rules everywhere (tsconfig: strict, noUnusedLocals, noUnusedParameters,
erasableSyntaxOnly): NO enums, NO constructor parameter properties, NO namespaces.
Use import * as THREE from 'three'. No new deps, no React Three Fiber. Use union
literal types instead of enums. Plain class fields with an explicit constructor
body (not parameter properties).

---

## 1. Coordinate and sign conventions (authoritative for all modules)

World: right-handed, +Y up, floor plane at y = 0. Camera sits in +Z, looking
toward -Z. The panda sits near world (0, 0, -0.1).

Rig hierarchy (each level is a THREE.Group; child transforms are LOCAL):

    pandaRoot        rotation.y = YAW (const 0.5)   position = world placement
      body           position.x = slide (local x)   position.y = height
                     scale = (sx, sy, sz) world-vertical squash/stretch
        pitchGroup   rotation.x = pitch             -PI/2 = flat on back
          rollGroup  rotation.y = roll (spine axis) 0 = face up when lying
            trousers
            legL/legR (hip pivots)
            spine    rotation.x = hunch  rotation.z = sway
              chest / shirt
              collar
              tiePivot -> tieBlade
              neck   rotation order YXZ
                head
                  earL/earR (pivots)
                  eyeL/eyeR groups (sclera+pupil+highlight + tearAnchor child)
                  browL/browR (pivots)
                  muzzle, nose
                  mouthGroup (hole, tongue, frownArc)
              shoulderL/shoulderR (pivots)
                upperSleeveL/R
                  elbowL/R (pivots)
                    forearmL/R + black paw

Sign conventions (poses and choreography rely on these):

- Arm raise FORWARD (toward camera/monitor): shoulder.rotation.x NEGATIVE.
  0 = arm hanging down. -PI/2 = arm straight forward, horizontal.
- Arm spread OUTWARD per side: shoulder.rotation.z = side * s, s >= 0 spreads
  outward. side = -1 for LEFT, +1 for RIGHT. The side multiplier hides the
  mirroring from pose authors (they pass one positive armSpread).
- Arm raise to CEILING ("WHYYYY"): shoulder.rotation.x near -2.6 (past vertical,
  up and slightly back) with small outward z.
- Elbow BEND (forearm toward face/body): elbow.rotation.x NEGATIVE. 0 = straight.
- Inner brow ends UP (distress): brow.rotation.z = side * b, b > 0 raises the
  INNER end and drops the outer end. side handles mirroring; author passes one
  positive browInner channel.
- Head lift OFF a resting surface while lying: pose author uses headLift >= 0
  meaning "raise the face away from whatever surface it rests on"; panda.ts maps
  neck.rotation.x -= headLift in the local frame. Because neck is inside
  rollGroup it lifts correctly whether on left side (roll -PI/2), right side
  (roll +PI/2) or on back (roll 0, pitch -PI/2, where it tucks the chin toward
  the chest to look down the body).
- Head tilt DOWN (looking at desk/monitor when sitting): neck.rotation.x POSITIVE
  via headDown. Head shake (crying): oscillate neck.rotation.y (headTurn). Head
  cock: neck.rotation.z (headTilt).
- Roll / no-slip: positive roll moves the body along +local x.
  slide = lerp(SIT_SLIDE, ROLL_K * roll, lieWeight),
  lieWeight = clamp01(-pitch / (PI/2)),
  lying height = LIE_Y + LIE_BOB * sin(roll)^2, ROLL_K = BODY_ROLL_RADIUS.
  When sitting (lieWeight 0), roll acts as a facing yaw and does NOT slide.

Units: rotations in radians; positions/scales in rig units (1.0 ~ head-radius
scale); time in seconds.

---

## 2. Rig dimensions (shared truth; panda.ts builds these, choreography reaches for them)

All positions LOCAL to the stated parent. Head visual radius ~0.62.

| Landmark | Value |
| --- | --- |
| SIT_Y (body.position.y sitting) | 0.86 |
| LIE_Y (body.position.y lying) | 0.44 |
| LIE_BOB (roll bob amplitude) | 0.07 |
| SIT_SLIDE (body.position.x sitting) | 0.0 |
| BODY_ROLL_RADIUS (ROLL_K) | 0.62 |
| head centre (local to neck) | (0, 0.30, 0.02) |
| head visual radius | 0.62 |
| neck pivot (local to spine top) | (0, 1.30, 0.02) |
| spine/waist pivot (local to rollGroup) | (0, 0.55, 0) |
| shoulderL / shoulderR (local to spine) | (-0.66, 1.02, 0.02) / (0.66, 1.02, 0.02) |
| upper sleeve length (shoulder->elbow) | 0.52 |
| forearm length (elbow->paw) | 0.46 |
| hipL / hipR (local to rollGroup) | (-0.34, 0.30, 0.04) / (0.34, 0.30, 0.04) |
| upper leg length (hip->knee) | 0.34 |
| lower leg length (knee->foot) | 0.30 |
| tiePivot (local to chest, at collar) | (0, 1.06, 0.30) |
| tie blade length | 0.62 |
| earL / earR (local to head) | (-0.50, 0.52, -0.05) / (0.50, 0.52, -0.05) |
| eyeL / eyeR group (local to head) | (-0.26, 0.10, 0.54) / (0.26, 0.10, 0.54) |
| tearAnchor (local to each eye group, lower-inner) | (side * 0.02, -0.10, 0.10) |
| browL / browR (local to head) | (-0.26, 0.30, 0.52) / (0.26, 0.30, 0.52) |
| mouthGroup (local to head) | (0, -0.22, 0.55) |

panda.ts MUST expose these as exported readonly constants (PANDA_DIMS) so
choreography.ts and the integrator camera code read them without duplicating
magic numbers.

---

## 3. Module: easing.ts (Author E)

Pure math. Keep THREE-free (operate on plain numbers). Exports:

    export function clamp01(x: number): number
    export function lerp(a: number, b: number, t: number): number
    export function smoothstep(edge0: number, edge1: number, x: number): number
    // seg: normalised progress of t within [start,end], clamped to [0,1]
    export function seg(t: number, start: number, end: number): number
    // windowRamp: 0 at edges, 1 in the middle of [start,end] with rise/fall ramps (seconds)
    export function windowRamp(t: number, start: number, end: number, rise: number, fall: number): number
    // bump: smooth 0->1->0 hump over [start,end] (peak at centre)
    export function bump(t: number, start: number, end: number): number
    export function easeInCubic(t: number): number
    export function easeOutCubic(t: number): number
    export function easeInOutCubic(t: number): number
    export function easeOutBack(t: number, overshoot?: number): number   // default overshoot 1.70158
    export function easeOutBounce(t: number): number
    // 1D value noise in [-1,1], smooth, deterministic; seed selects an independent stream
    export function valueNoise(x: number, seed?: number): number

    // Critically-damped-ish spring. Plain fields, explicit constructor (no param props).
    export class Spring {
      value: number
      velocity: number
      private target: number
      private stiffness: number
      private damping: number
      private initialised: boolean
      constructor(stiffness: number, damping: number)
      setTarget(target: number): void
      // Advance by dt seconds; substep internally to <= 1/120 for stability.
      // On FIRST update after construction/reset: snap value=target, velocity=0.
      update(target: number, dt: number): number
      reset(value: number): void   // snap immediately, clear velocity, mark initialised
    }

Conventions: easing fn t inputs are in [0,1] (callers pass seg(...) results).
windowRamp/bump take absolute seconds and gate procedural overlays so they are
exactly 0 at phase edges.

Acceptance criteria (E):
- seg(t,a,b) returns 0 for t<=a, 1 for t>=b, linear between; a===b returns t>=a?1:0 with no NaN.
- windowRamp and bump return exactly 0 at and outside their edges.
- Spring.update snaps on first call; stable at dt up to 0.05 (substepped); settles without oscillation blow-up for stiffness 40-400 near-critical damping.
- valueNoise is C1-smooth; different seed values decorrelated.
- No any, no unused params, builds clean under strict.

---

## 4. Module: choreography.ts (Author C)

Single source of truth for the Pose type, named poses, phase timeline, events and
camera targets. Imports easing helpers.

### 4.1 Pose type (channels, units, signs, SIT defaults)

Every channel is a number; Pose is a plain interface, all fields required.
blend(out,a,b,k) lerps every field. side mirroring happens inside panda.ts, so
poses carry ONE value per logical control.

    export interface Pose {
      // gross body
      pitch: number       // rad pitchGroup.rotation.x. 0 upright, -PI/2 flat on back
      roll: number        // rad rollGroup.rotation.y. lying: 0 face-up, +/-PI/2 on side
      hunch: number       // rad spine.rotation.x forward hunch (+ = lean forward)
      sway: number        // rad spine.rotation.z lateral sway
      bodyScaleY: number  // world-vertical squash (1 = neutral)
      bodyScaleXZ: number // horizontal counter-scale
      // head/neck
      headDown: number    // rad neck.rotation.x, + = look down
      headTurn: number    // rad neck.rotation.y
      headTilt: number    // rad neck.rotation.z cock
      headLift: number    // rad subtracted from neck.rotation.x to raise face off surface
      // face
      eyeOpen: number     // 0 shut .. 1 normal .. 1.6 wide/horror
      eyeSquint: number   // 0..1 vertical squeeze (crying)
      browInner: number   // rad inner-end raise (distress)
      browLower: number   // rad whole-brow lower (anger/effort)
      mouthOpen: number   // 0 closed .. 1 wide
      mouthWide: number   // -1 pucker .. 0 neutral .. 1 stretched
      mouthFrown: number  // 0 flat .. 1 deep frown arc
      // arms (symmetric base; per-side lag overlays added in panda.ts)
      armRaise: number    // rad shoulder.rotation.x, NEGATIVE raises forward
      armSpread: number   // rad magnitude, shoulder.rotation.z outward per side
      elbowBend: number   // rad elbow.rotation.x, NEGATIVE bends toward face
      // legs (symmetric base)
      hipFlex: number     // rad hip.rotation.x, + = knee toward chest
      kneeBend: number    // rad knee.rotation.x
      legSpread: number   // rad magnitude outward per side
    }

    export const SIT: Pose            // values in the table below
    export const REALISE_START: Pose  // = SIT (loop seam pose)
    export const SHOCK: Pose
    export const CONCERN: Pose
    export const CRY: Pose
    export const WAIL: Pose
    export const LIE: Pose            // = clonePose(BACK) with roll set by the phase
    export const BACK: Pose
    export const EXHAUSTED: Pose
    export function blend(out: Pose, a: Pose, b: Pose, k: number): Pose
    export function clonePose(p: Pose): Pose

Named-pose values (author C may tune within +/-20%, keeping seams):

| Channel | SIT | SHOCK | CONCERN | CRY | WAIL | BACK | EXHAUSTED |
| --- | --- | --- | --- | --- | --- | --- | --- |
| pitch | 0 | 0 | 0 | 0 | -1.571 | -1.571 | -1.45 |
| roll | 0 | 0 | 0 | 0 | 0 | 0 | 1.4 |
| hunch | 0.10 | -0.05 | 0.18 | 0.35 | 0.10 | 0.05 | 0.20 |
| sway | 0 | 0 | 0 | 0 | 0 | 0 | 0.08 |
| bodyScaleY | 1 | 1.03 | 1 | 0.98 | 1 | 1 | 0.97 |
| bodyScaleXZ | 1 | 0.99 | 1 | 1.01 | 1 | 1 | 1.02 |
| headDown | 0.06 | 0.20 | 0.15 | 0.30 | -0.30 | 0.05 | 0.10 |
| headTurn | 0 | 0 | 0 | 0 | 0 | 0 | 0.2 |
| headTilt | 0 | 0 | 0 | 0 | 0 | 0 | 0.1 |
| headLift | 0 | 0 | 0 | 0 | 0.2 | 0.15 | 0.05 |
| eyeOpen | 1 | 1.6 | 1.1 | 0.5 | 0.4 | 1.0 | 0.4 |
| eyeSquint | 0 | 0 | 0.1 | 0.8 | 1.0 | 0.2 | 0.6 |
| browInner | 0 | 0.15 | 0.35 | 0.6 | 0.7 | 0.4 | 0.3 |
| browLower | 0 | -0.1 | 0.1 | 0.2 | 0.3 | 0.1 | 0.15 |
| mouthOpen | 0.1 | 0.35 | 0.15 | 0.7 | 1.0 | 0.3 | 0.1 |
| mouthWide | 0 | 0 | 0 | 0.4 | 0.6 | 0.2 | 0.1 |
| mouthFrown | 0.2 | 0.3 | 0.6 | 1.0 | 0.9 | 0.6 | 0.7 |
| armRaise | 0.2 | 0.05 | 0.2 | 0.4 | -2.6 | 0.5 | 0.6 |
| armSpread | 0.15 | 0.10 | 0.15 | 0.25 | 0.5 | 0.3 | 0.2 |
| elbowBend | -0.3 | -0.2 | -0.5 | -1.2 | -0.2 | -0.4 | -0.6 |
| hipFlex | 0.9 | 0.9 | 0.9 | 1.0 | 1.2 | 1.1 | 0.7 |
| kneeBend | -0.9 | -0.9 | -0.9 | -1.0 | -1.3 | -1.1 | -0.6 |
| legSpread | 0.15 | 0.15 | 0.15 | 0.2 | 0.4 | 0.3 | 0.2 |

### 4.2 Phases, timeline and sampler

Loop length CYCLE = 12.0 s. Cycle time tc = accumulatedTime % CYCLE.

    export type PhaseName =
      | 'realisation' | 'crying' | 'collapse'
      | 'rollingA' | 'despair' | 'rollingB'
      | 'exhaustion' | 'recovery'
    export interface PhaseSpan { name: PhaseName; start: number; end: number }
    export const CYCLE: number   // 12.0
    export const PHASES: readonly PhaseSpan[]
    // Fills out with the pose for cycle-time tc, applies phase blends + deterministic
    // procedural overlays gated by windows that are 0 at edges. Returns active phase.
    export function samplePose(tc: number, out: Pose): PhaseName

Timeline (each phase starts EXACTLY from the previous phase's end pose so the
seam at tc=0/tc=12 is invisible):

| Phase | Span (s) | Start -> end pose | Key beats and overlays |
| --- | --- | --- | --- |
| realisation | 0.0-1.8 | SIT -> SHOCK -> CONCERN | 0.0 seated looking at monitor; 0.4 freeze; 0.6 eyes widen (easeOutBack on eyeOpen); 0.9 shoulders rise; 1.2 head tilts down; 1.4-1.8 mouth trembles (valueNoise * bump) |
| crying | 1.8-3.8 | CONCERN -> CRY (hold) | head shake sin on headTurn; shoulder sob bounce pow(max(0,sin),6) on bodyScaleY+armRaise; alternating eye wipe (per-side elbowBend deepens); mouth open/close; tears emit |
| collapse | 3.8-4.6 | CRY -> LIE(roll -1.67) | torso tilts FIRST (pitch leads, easeInCubic); arms reach out (armRaise -> -0.9); head lags via spring; legs lose balance; IMPACT at 4.4 -> squash kick, camera shake, papers jump, mug shake, chair swivel |
| rollingA | 4.6-6.4 | LIE roll -1.67 -> +1.75 -> 0 | A1 4.6-5.6 roll -1.67->+1.75 (easeInOutCubic) with arm flail + leg kick (valueNoise); vertical bounce via height bob; A2 5.6-6.4 roll +1.75->0 into hug-head (elbowBend -1.4, armRaise -1.6) |
| despair | 6.4-7.6 | BACK stare -> WAIL -> BACK | 6.4-6.95 flat on back staring up, comedic pause; WHY at 6.95 arms slam to ceiling (WAIL, easeOutBack), tear FOUNTAIN burst, mouth widest, camera push-in + shake; 7.3-7.6 arms lower toward BACK |
| rollingB | 7.6-9.2 | BACK -> roll right -> roll left ~-1.7 | on-back punch carpet (2 punches) + kick; roll right covering face (arms over face, elbowBend deep); roll left flailing to roll ~-1.7 |
| exhaustion | 9.2-11.2 | LIE(-1.7)/face-down -> EXHAUSTED | overlay amplitudes decay to 0; breathing sin on bodyScaleY tiny; leg twitch (rare valueNoise spike); shoulder hiccup; few tears; slowly lifts head (headLift easeOutCubic) to look at monitor; MONITOR FLASH ~10.65; pause; eye twitch (eyeOpen quick dip) |
| recovery | 11.2-12.0 | EXHAUSTED -> SIT | sit back up (pitch/roll -> 0 easeInOutCubic, hip/knee to seated), settle to SIT so tc=12 == tc=0 |

Continuity requirement: samplePose(0,out) and samplePose(CYCLE,out) MUST produce
identical channel values (test with tolerance 1e-4).

### 4.3 Events

    export type EventName = 'impact' | 'punch' | 'why' | 'monitorFlash' | 'kickBurst'
    export interface TimedEvent { name: EventName; at: number; strength: number }
    export const EVENTS: readonly TimedEvent[]

| name | at (s) | strength | effect (consumed by owner module) |
| --- | --- | --- | --- |
| impact | 4.40 | 1.0 | squash spring kick + camera shake + paper hop + mug shake + chair swivel |
| why | 6.95 | 1.0 | tear fountain burst + camera push-in start |
| punch | 7.85 | 0.6 | small camera shake + nearby paper hop |
| punch | 8.15 | 0.6 | small camera shake + nearby paper hop |
| kickBurst | 8.55 | 0.5 | small paper hop from foot region |
| monitorFlash | 10.65 | 1.0 | monitor CanvasTexture swaps to alt error + light tint pulse + eye twitch trigger |

Event firing is edge-detected by the INTEGRATOR against wrap-around cycle time
(fire when prevTc < at <= tc, and handle the wrap tc < prevTc). Choreography only
DECLARES events; it does not fire them. EVENTS is sorted ascending by at.

### 4.4 Camera helper values

    export interface CameraShot {
      basePos: THREE.Vector3      // (1.6, 2.7, 7.4)
      baseTarget: THREE.Vector3   // (-0.25, 0.8, -0.5)
      fov: number                 // 38
      pushIn: number              // 0..1 extra dolly toward target, peaks during WHY
      handheld: number            // 0..1 gentle handheld during biggest tantrum
      followX: number             // 0..1 horizontal follow of panda slide
    }
    export function sampleCamera(tc: number): CameraShot

pushIn ramps up around the why beat (6.7-7.2) then eases back by 7.6; handheld is
nonzero across despair+rollingB; followX tracks the panda slide softly. The
integrator adds impact shake + accumulated-time drift on top.

Acceptance criteria (C):
- All named poses are complete Pose objects; blend covers every field.
- Seam identity: pose at 0 == pose at CYCLE within 1e-4 on every channel.
- No channel from samplePose exceeds documented ranges (eyeOpen in [0,1.6], pitch in [-PI/2-0.05, 0.05]).
- Overlays are exactly 0 at every phase boundary (verified at boundary timestamps).
- EVENTS sorted ascending by at, all within [0, CYCLE).

---

## 5. Module: panda.ts (Author P)

Builds the rig to section-2 dims; exposes handles + applyPose. Owns updateFace
and updateSecondaryMotion (private helpers called inside applyPose). Imports
Spring + easing from easing.ts and the Pose TYPE from choreography.ts.

    export interface PandaDims {   // readonly re-export of section-2 constants
      SIT_Y: number; LIE_Y: number; LIE_BOB: number; SIT_SLIDE: number
      BODY_ROLL_RADIUS: number
      headCentre: THREE.Vector3; headRadius: number
      neckPivot: THREE.Vector3; spinePivot: THREE.Vector3
      // ...all landmarks from section 2
    }
    export const PANDA_DIMS: PandaDims

    export interface PandaRig {
      root: THREE.Group            // add to the scene
      slide: number                // current body.position.x (read after applyPose)
      height: number               // current body.position.y
      leftTearAnchor: THREE.Object3D   // world-updated each applyPose
      rightTearAnchor: THREE.Object3D
      applyPose(pose: Pose, clock: number, dt: number): void  // clock = accumulated time
      settle(pose: Pose): void     // snap all springs (reduced-motion + mount)
      kickSquash(strength: number): void  // adds downward velocity to squash spring (on impact)
      dispose(): void              // dispose only geometries/materials THIS module created
    }
    export function createPanda(): PandaRig

applyPose responsibilities, in order:
1. Map gross channels using section-1 signs; compute lieWeight, slide (no-slip),
   height; apply body scale (world-vertical squash with XZ counter-scale).
2. Per-side arm/leg mapping: shoulder.rotation.x = armRaise,
   shoulder.rotation.z = side * armSpread, elbow.rotation.x = elbowBend;
   hips/knees similarly. Per-side asymmetry (wipes/kicks) is baked into the
   sampled pose by choreography; panda.ts only adds spring-lag overlays here.
3. updateFace(pose): eyeOpen -> eye group scale.y; eyeSquint squeezes further and
   moves lids; browInner -> per-side brow.rotation.z = side*browInner - browLower;
   mouthOpen -> mouthGroup.scale.y, mouthWide -> scale.x, mouthFrown -> frown arc.
4. updateSecondaryMotion(clock, dt): springs for head lag (target from neck angles
   driven by roll/pitch/sway), arm lag, elbow lag, ear bounce (kicked by head
   angular velocity), tie (gravity + tiePivot world accel mapped into spine-local
   frame, clamped so the blade cannot enter the belly), squash spring (kicked via
   kickSquash). Substep dt to 1/120 inside springs.
5. Tear anchors are children of the eye groups, so step 3 already positions them;
   the tear module reads their world positions.

Materials/geometry sharing: one SphereGeometry reused for all ellipsoids (scaled
per mesh); shared MeshStandardMaterial instances for ivory/black/shirt-white/tie/
trouser/skin. Meshes castShadow/receiveShadow true.

Acceptance criteria (P):
- Rig matches section-2 dims; settle(SIT) shows a recognisable seated office panda (untucked white shirt, loosened tie, dark trousers, black ears/eye-patches/nose, expressive mouth).
- Every part is its own pivot; applyPose(SIT) vs applyPose(WAIL) moves arms/legs/head/face independently; the whole panda is NEVER rotated rigidly (root.rotation stays at YAW).
- Springs snap on first applyPose/settle (no fly-in). Tie never clips into the belly at any pose.
- Reuses one sphere geometry + a small fixed material set; dispose frees exactly what it created.
- Builds clean under strict/noUnusedParameters.

---

## 6. Module: tears.ts (Author T)

Reusable tear pool as one InstancedMesh (no per-frame allocation). Per-instance
opacity via onBeforeCompile injecting an instanced attribute into
#include <begin_vertex> and applying it at #include <alphamap_fragment>. Imports
easing only.

    export interface TearEmitter {
      position: THREE.Vector3       // world spawn point (eye tear anchor)
      velocity: THREE.Vector3       // world base velocity (inherit eye velocity for fling)
      headQuaternion: THREE.Quaternion  // inherit head orientation/motion
      side: 1 | -1                  // which eye
      rate: number                  // tears per second to spawn (0 = none)
      fountain: number              // 0 normal .. 1 fountain burst (WHY)
    }
    export interface TearSystem {
      update(dt: number, emitters: readonly TearEmitter[]): void
      settleStatic(emitters: readonly TearEmitter[]): void  // reduced-motion frame
      dispose(): void               // dispose geometry+material and remove the mesh from scene
    }
    export function createTearSystem(scene: THREE.Scene, capacity?: number): TearSystem  // default 96

Behaviour:
- Each live tear keeps position/velocity/lifetime/scale/opacity in typed arrays;
  recycled from a free list. Gravity each step; sideways fling inherits velocity
  so head shakes throw tears laterally. fountain spawns upward + outward with
  higher rate.
- Fade opacity over lifetime; on y<=floorY become a flat splat (scale.y small,
  scale.xz larger) then recycle.
- Writes instance matrices + opacity attribute; sets instanceMatrix.needsUpdate
  and the custom attribute needsUpdate.

Acceptance criteria (T):
- Zero allocations in update after warm-up (pool + typed arrays only).
- Per-instance opacity works via onBeforeCompile against r186 MeshStandard/Physical shader includes (begin_vertex, alphamap_fragment); no shader-compile error in build/preview.
- Tears spawn at eye anchors, fall, fling sideways on head shake, burst upward in fountain mode at WHY, fade and splat on the floor.
- dispose removes the InstancedMesh and frees its geometry+material.
- No PCFSoftShadowMap dependence; tears need not cast shadows.

---

## 7. Module: office.ts (Author O)

Builds the stylised office; exposes reactive handles + an update fn. Imports
Spring, easing, valueNoise.

    export interface OfficeHandles {
      root: THREE.Group
      papers: THREE.Object3D[]      // floor papers "BUG","FAILED BUILD","URGENT","PRODUCTION ISSUE"
      crumpled: THREE.Object3D[]    // crumpled paper balls
      mug: THREE.Object3D
      chairSwivel: THREE.Object3D   // chair seat/back group that swivels
      plantLeaves: THREE.Object3D[]
      monitorScreen: THREE.MeshBasicMaterial   // uses the active CanvasTexture as .map
      monitorTexNormal: THREE.CanvasTexture    // default error screen
      monitorTexAlt: THREE.CanvasTexture       // alternate error (flash)
      monitorLight: THREE.PointLight           // warm desk/monitor light, tinted on flash
      deskPosition: THREE.Vector3              // ~(-2.5, 0, -0.5)
    }
    export function createOffice(scene: THREE.Scene): OfficeHandles

    export interface OfficeFrame {
      dt: number
      clock: number
      pandaSlide: number     // rig.slide, so props react as the panda rolls past
      pandaHeight: number
      impact: boolean        // true only on the frame the event fires
      punch: boolean
      kickBurst: boolean
      monitorFlash: boolean
      flashProgress: number  // 0..1 decaying tint after a flash (owned by integrator)
    }
    export function updateOfficeReactions(office: OfficeHandles, frame: OfficeFrame): void
    export function disposeOffice(office: OfficeHandles): void  // dispose geometries, materials, and BOTH CanvasTextures

Behaviour:
- Papers/crumpled hop+spin (spring kick) when the panda's world x passes near
  them or on impact/punch/kickBurst; settle back down.
- mug shakes on impact.
- chairSwivel gets an angular kick on impact, then friction + weak return to a
  home angle (slow decaying swivel).
- plantLeaves wobble via per-leaf springs driven by valueNoise (idle) plus a kick
  when the panda rolls near.
- On monitorFlash the integrator swaps monitorScreen.map to monitorTexAlt for a
  beat then back to monitorTexNormal; monitorLight tint pulses using flashProgress.

Static pieces (built, not reactive): desk, monitor body, displaced keyboard,
empty cups, filing cabinet, back wall (z ~ -3.2) with poster
"DAYS SINCE LAST PRODUCTION ISSUE: 0", left wall (x ~ -4), carpet, pendant lamp.
Slightly messy, not overcrowded; the panda is the focus.

Acceptance criteria (O):
- Reads as a modern cartoon office; monitor shows an error screen via CanvasTexture; papers labelled as specified.
- Reactive props respond to impact and to the panda rolling past (position-driven); chair swivels then settles with friction; plant idles with subtle wobble.
- Two CanvasTextures created and disposed in disposeOffice; monitor light exists and tints on flash.
- Shares geometries/materials where sensible; modest polys; casts/receives shadows.
- Builds clean under strict.

---

## 8. Module (INTEGRATOR only): crying-panda-scene.ts

Preserves the EXISTING public API and all lifecycle behaviour; swaps the static
panda for the modular scene. Signature UNCHANGED:

    export function createPandaScene(host: HTMLElement): { setPaused(value: boolean): void; dispose(): void }

Preserve from the current file:
- WebGLRenderer({ antialias:true, alpha:true }), setPixelRatio(min(dpr,1.75)),
  shadowMap.enabled = true, ACES tone mapping + exposure.
- CHANGE for r186: shadowMap.type = THREE.PCFShadowMap (NOT PCFSoftShadowMap,
  which is removed and warns); add light.shadow.radius (~3-4) on the shadow-casting
  DirectionalLight for softness.
- ResizeObserver resize (camera aspect, renderer size, render once).
- prefers-reduced-motion + document.hidden gating via renderer.setAnimationLoop;
  keep the syncMotion() pattern.
- Pointer parallax (pointermove/pointerleave) feeding a small camera offset.
- setPaused toggles and calls syncMotion.
- dispose: stop loop, disconnect observer, remove listeners, dispose
  geometries+materials by traversal AND call panda.dispose(), tears.dispose(),
  disposeOffice(office) (frees CanvasTextures + InstancedMesh + module assets),
  then renderer.dispose() + remove DOM node.

New integrator responsibilities:
- Lights: HemisphereLight + shadow-casting DirectionalLight (shadow.radius,
  mapSize 1024, normalBias) + warm PointLight near the desk (office owns the
  monitor-light tint).
- animationState = { time: number, phase: PhaseName }; accumulate time only when
  not paused/reduced/hidden. tc = time % CYCLE.
- Per frame: phase = samplePose(tc, poseScratch); rig.applyPose(poseScratch, time, dt);
  build TearEmitter[] from rig.leftTearAnchor/rightTearAnchor world positions +
  head quaternion + phase-driven rate/fountain; tears.update(dt, emitters); fire
  events by wrap-around edge detection to set OfficeFrame flags + rig.kickSquash +
  camera shake/push-in; updateOfficeReactions(office, frame); updateCamera.
- updateCamera(tc, dt): read sampleCamera(tc); apply followX toward rig.slide; add
  pushIn dolly; handheld + impact shake using ACCUMULATED-time noise (so nothing
  jumps at the seam); add pointer parallax; smooth return via lerp.
- Reduced-motion static frame: tc = 2.35, samplePose into scratch, rig.settle(pose),
  tears.settleStatic(emitters), render once.
- Choreography on tc (cycle time); drift/noise on time (accumulated), so the seam
  is invisible. Reuse one Pose scratch object and a small pool of TearEmitter
  objects (no per-frame allocation).

Acceptance criteria (integrator):
- Public API identical; pause button, reduced-motion, visibility, resize, pointer parallax, dispose all still work.
- No console warning about PCFSoftShadowMap; shadows soft via PCFShadowMap + radius; panda casts a visible shadow.
- ~12 s seamless loop across all eight phases; no visible jump at the seam.
- npm run build and npm run lint pass in ./frontend.

---

## 9. Module (INTEGRATOR only): crying-panda.tsx

- Keep the structure, lazy import, setPaused/dispose handling, status states,
  pause button, and the Chinese words block AS-IS.
- ONLY change: the aria-label on .panda-stage to describe the new scene, e.g.
  aria-label="Animated 3D cartoon panda having an office meltdown".

## 10. Module (INTEGRATOR only): crying-panda.css

- Keep the 570px desktop / 340px mobile stage and existing layout contract (grid
  columns, card placement, words positioning). Adjust ONLY the .panda-canvas
  insets if the wider office scene is clipped; prefer leaving CSS unchanged.

---

## 11. Cross-module contract summary (the seams)

1. Pose type + named poses + samplePose + sampleCamera + EVENTS + CYCLE +
   PhaseName live ONLY in choreography.ts. panda.ts imports the Pose TYPE.
   Integrator imports the sampler, camera, events, CYCLE, PhaseName.
2. panda.ts exposes PandaRig (slide, height, tear anchors, applyPose, settle,
   kickSquash, dispose) + PANDA_DIMS. Integrator + camera read dims/anchors/slide;
   nobody else builds the rig.
3. tears.ts exposes createTearSystem + TearEmitter + TearSystem. Integrator
   constructs emitters from rig anchors and phase state each frame.
4. office.ts exposes OfficeHandles, createOffice, updateOfficeReactions,
   disposeOffice. Integrator passes an OfficeFrame each tick and drives the
   monitor-flash texture swap.
5. easing.ts is a leaf used by everyone; freeze its API first, since a signature
   change there is the only change forcing a cross-author sync.

Global acceptance: no new dependencies; strict / noUnusedLocals /
noUnusedParameters / erasableSyntaxOnly clean; npm run build + npm run lint green;
60 FPS target via shared geometries/materials, object reuse, InstancedMesh tears,
modest polys; do not commit; backend untouched.

## 12. Suggested build/verify order

1. Author E lands easing.ts first and freezes its API.
2. Author C lands choreography.ts (Pose + poses + timeline + events + camera).
3. Authors P, O, T work in parallel against those frozen types.
4. Integrator wires crying-panda-scene.ts, updates the crying-panda.tsx
   aria-label, tunes CSS only if clipped, then runs:
       cd frontend; npm run build; npm run lint
   and optional headless Chrome screenshots per phase:
       chrome --headless=new --use-angle=swiftshader --enable-unsafe-swiftshader --screenshot
