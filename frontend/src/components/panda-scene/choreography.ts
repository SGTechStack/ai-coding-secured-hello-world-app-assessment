// choreography.ts - Office Panda Meltdown timeline (Author C).
//
// Single source of truth for the Pose type, named poses, the phase timeline,
// declared timed events and camera choreography helpers. Imports pure-math
// helpers from ./easing (Author E). No THREE-side effects except constructing
// Vector3 values inside sampleCamera.
//
// TypeScript rules: strict, noUnusedLocals, noUnusedParameters, erasableSyntaxOnly.
// No enums, no namespaces, no constructor parameter properties. Union literal
// types are used instead of enums.

import * as THREE from 'three';
import {
  clamp01,
  lerp,
  smoothstep,
  seg,
  windowRamp,
  bump,
  easeInCubic,
  easeOutCubic,
  easeInOutCubic,
  easeOutBack,
  valueNoise,
} from './easing';

// ---------------------------------------------------------------------------
// 4.1 Pose type
// ---------------------------------------------------------------------------

export interface Pose {
  // gross body
  pitch: number; // rad pitchGroup.rotation.x. 0 upright, -PI/2 flat on back
  roll: number; // rad rollGroup.rotation.y. lying: 0 face-up, +/-PI/2 on side
  hunch: number; // rad spine.rotation.x forward hunch (+ = lean forward)
  sway: number; // rad spine.rotation.z lateral sway
  bodyScaleY: number; // world-vertical squash (1 = neutral)
  bodyScaleXZ: number; // horizontal counter-scale
  // head/neck
  headDown: number; // rad neck.rotation.x, + = look down
  headTurn: number; // rad neck.rotation.y
  headTilt: number; // rad neck.rotation.z cock
  headLift: number; // rad subtracted from neck.rotation.x to raise face off surface
  // face
  eyeOpen: number; // 0 shut .. 1 normal .. 1.6 wide/horror
  eyeSquint: number; // 0..1 vertical squeeze (crying)
  browInner: number; // rad inner-end raise (distress)
  browLower: number; // rad whole-brow lower (anger/effort)
  mouthOpen: number; // 0 closed .. 1 wide
  mouthWide: number; // -1 pucker .. 0 neutral .. 1 stretched
  mouthFrown: number; // 0 flat .. 1 deep frown arc
  // arms (symmetric base; per-side lag overlays added in panda.ts)
  armRaise: number; // rad shoulder.rotation.x, NEGATIVE raises forward
  armSpread: number; // rad magnitude, shoulder.rotation.z outward per side
  elbowBend: number; // rad elbow.rotation.x, NEGATIVE bends toward face
  // legs (symmetric base)
  hipFlex: number; // rad hip.rotation.x, + = knee toward chest
  kneeBend: number; // rad knee.rotation.x
  legSpread: number; // rad magnitude outward per side
}

// The 24 channel keys, used by blend/clonePose so every field is always covered.
const POSE_KEYS: readonly (keyof Pose)[] = [
  'pitch',
  'roll',
  'hunch',
  'sway',
  'bodyScaleY',
  'bodyScaleXZ',
  'headDown',
  'headTurn',
  'headTilt',
  'headLift',
  'eyeOpen',
  'eyeSquint',
  'browInner',
  'browLower',
  'mouthOpen',
  'mouthWide',
  'mouthFrown',
  'armRaise',
  'armSpread',
  'elbowBend',
  'hipFlex',
  'kneeBend',
  'legSpread',
];

function makePose(values: Pose): Pose {
  // Identity constructor; keeps the object shape monomorphic.
  return values;
}

// ---------------------------------------------------------------------------
// Named poses (values from the contract table; tuned within tolerance).
// ---------------------------------------------------------------------------

export const SIT: Pose = makePose({
  pitch: 0,
  roll: 0,
  hunch: 0.1,
  sway: 0,
  bodyScaleY: 1,
  bodyScaleXZ: 1,
  headDown: 0.06,
  headTurn: 0,
  headTilt: 0,
  headLift: 0,
  eyeOpen: 1,
  eyeSquint: 0,
  browInner: 0,
  browLower: 0,
  mouthOpen: 0.1,
  mouthWide: 0,
  mouthFrown: 0.2,
  armRaise: 0.2,
  armSpread: 0.15,
  elbowBend: -0.3,
  hipFlex: 1.35,
  kneeBend: -0.25,
  legSpread: 0.15,
});

export const SHOCK: Pose = makePose({
  pitch: 0,
  roll: 0,
  hunch: -0.05,
  sway: 0,
  bodyScaleY: 1.03,
  bodyScaleXZ: 0.99,
  headDown: 0.2,
  headTurn: 0,
  headTilt: 0,
  headLift: 0,
  eyeOpen: 1.6,
  eyeSquint: 0,
  browInner: 0.15,
  browLower: -0.1,
  mouthOpen: 0.35,
  mouthWide: 0,
  mouthFrown: 0.3,
  armRaise: 0.05,
  armSpread: 0.1,
  elbowBend: -0.2,
  hipFlex: 1.35,
  kneeBend: -0.25,
  legSpread: 0.15,
});

export const CONCERN: Pose = makePose({
  pitch: 0,
  roll: 0,
  hunch: 0.18,
  sway: 0,
  bodyScaleY: 1,
  bodyScaleXZ: 1,
  headDown: 0.15,
  headTurn: 0,
  headTilt: 0,
  headLift: 0,
  eyeOpen: 1.1,
  eyeSquint: 0.1,
  browInner: 0.35,
  browLower: 0.1,
  mouthOpen: 0.15,
  mouthWide: 0,
  mouthFrown: 0.6,
  armRaise: 0.2,
  armSpread: 0.15,
  elbowBend: -0.5,
  hipFlex: 1.35,
  kneeBend: -0.25,
  legSpread: 0.15,
});

export const CRY: Pose = makePose({
  pitch: 0,
  roll: 0,
  hunch: 0.35,
  sway: 0,
  bodyScaleY: 0.98,
  bodyScaleXZ: 1.01,
  headDown: 0.3,
  headTurn: 0,
  headTilt: 0,
  headLift: 0,
  eyeOpen: 0.5,
  eyeSquint: 0.8,
  browInner: 0.6,
  browLower: 0.2,
  mouthOpen: 0.7,
  mouthWide: 0.4,
  mouthFrown: 1.0,
  armRaise: 0.4,
  armSpread: 0.25,
  elbowBend: -1.2,
  hipFlex: 1.4,
  kneeBend: -0.3,
  legSpread: 0.2,
});

export const WAIL: Pose = makePose({
  pitch: -1.571,
  roll: 0,
  hunch: 0.1,
  sway: 0,
  bodyScaleY: 1,
  bodyScaleXZ: 1,
  headDown: -0.3,
  headTurn: 0,
  headTilt: 0,
  headLift: 0.2,
  eyeOpen: 0.4,
  eyeSquint: 1.0,
  browInner: 0.7,
  browLower: 0.3,
  mouthOpen: 1.0,
  mouthWide: 0.6,
  mouthFrown: 0.9,
  armRaise: -2.6,
  armSpread: 0.5,
  elbowBend: -0.2,
  hipFlex: 1.2,
  kneeBend: -1.3,
  legSpread: 0.4,
});

export const BACK: Pose = makePose({
  pitch: -1.571,
  roll: 0,
  hunch: 0.05,
  sway: 0,
  bodyScaleY: 1,
  bodyScaleXZ: 1,
  headDown: 0.05,
  headTurn: 0,
  headTilt: 0,
  headLift: 0.15,
  eyeOpen: 1.0,
  eyeSquint: 0.2,
  browInner: 0.4,
  browLower: 0.1,
  mouthOpen: 0.3,
  mouthWide: 0.2,
  mouthFrown: 0.6,
  armRaise: 0.5,
  armSpread: 0.3,
  elbowBend: -0.4,
  hipFlex: 1.1,
  kneeBend: -1.1,
  legSpread: 0.3,
});

export const EXHAUSTED: Pose = makePose({
  pitch: -1.45,
  roll: 1.4,
  hunch: 0.2,
  sway: 0.08,
  bodyScaleY: 0.97,
  bodyScaleXZ: 1.02,
  headDown: 0.1,
  headTurn: 0.2,
  headTilt: 0.1,
  headLift: 0.05,
  eyeOpen: 0.4,
  eyeSquint: 0.6,
  browInner: 0.3,
  browLower: 0.15,
  mouthOpen: 0.1,
  mouthWide: 0.1,
  mouthFrown: 0.7,
  armRaise: 0.6,
  armSpread: 0.2,
  elbowBend: -0.6,
  hipFlex: 0.7,
  kneeBend: -0.6,
  legSpread: 0.2,
});

// REALISE_START is the loop-seam pose. It must equal SIT so tc=0 == tc=12.
export const REALISE_START: Pose = clonePose(SIT);

// LIE is BACK with the roll set by the phase (rolling lies on the side).
// Kept as an exported reference pose (roll left onto side).
export const LIE: Pose = (function makeLie(): Pose {
  const p = clonePose(BACK);
  p.roll = -1.67;
  return p;
})();

// ---------------------------------------------------------------------------
// Pose algebra
// ---------------------------------------------------------------------------

export function clonePose(p: Pose): Pose {
  return {
    pitch: p.pitch,
    roll: p.roll,
    hunch: p.hunch,
    sway: p.sway,
    bodyScaleY: p.bodyScaleY,
    bodyScaleXZ: p.bodyScaleXZ,
    headDown: p.headDown,
    headTurn: p.headTurn,
    headTilt: p.headTilt,
    headLift: p.headLift,
    eyeOpen: p.eyeOpen,
    eyeSquint: p.eyeSquint,
    browInner: p.browInner,
    browLower: p.browLower,
    mouthOpen: p.mouthOpen,
    mouthWide: p.mouthWide,
    mouthFrown: p.mouthFrown,
    armRaise: p.armRaise,
    armSpread: p.armSpread,
    elbowBend: p.elbowBend,
    hipFlex: p.hipFlex,
    kneeBend: p.kneeBend,
    legSpread: p.legSpread,
  };
}

export function blend(out: Pose, a: Pose, b: Pose, k: number): Pose {
  for (let i = 0; i < POSE_KEYS.length; i++) {
    const key = POSE_KEYS[i];
    out[key] = lerp(a[key], b[key], k);
  }
  return out;
}

function copyInto(out: Pose, src: Pose): Pose {
  for (let i = 0; i < POSE_KEYS.length; i++) {
    const key = POSE_KEYS[i];
    out[key] = src[key];
  }
  return out;
}

// ---------------------------------------------------------------------------
// 4.2 Phases and timeline
// ---------------------------------------------------------------------------

export type PhaseName =
  | 'realisation'
  | 'crying'
  | 'collapse'
  | 'rollingA'
  | 'despair'
  | 'rollingB'
  | 'exhaustion'
  | 'recovery';

export interface PhaseSpan {
  name: PhaseName;
  start: number;
  end: number;
}

export const CYCLE = 12.0;

const HALF_PI = Math.PI / 2;

export const PHASES: readonly PhaseSpan[] = [
  { name: 'realisation', start: 0.0, end: 1.8 },
  { name: 'crying', start: 1.8, end: 3.8 },
  { name: 'collapse', start: 3.8, end: 4.6 },
  { name: 'rollingA', start: 4.6, end: 6.4 },
  { name: 'despair', start: 6.4, end: 7.6 },
  { name: 'rollingB', start: 7.6, end: 9.2 },
  { name: 'exhaustion', start: 9.2, end: 11.2 },
  { name: 'recovery', start: 11.2, end: 12.0 },
];

// Roll waypoints used by the rolling phases (radians about the spine axis).
const ROLL_A_START = -1.67; // collapse lands here (on left side)
const ROLL_A_MID = 1.75; // rollingA rolls across to the right side
const ROLL_A_END = 0.0; // rollingA A2 settles face-up to hug head
const ROLL_B_END = -1.7; // rollingB flails back to the left side

// Scratch poses reused inside samplePose (module-scope so no per-frame alloc).
const scratchA: Pose = clonePose(SIT);
const scratchB: Pose = clonePose(SIT);

// ---- Per-phase samplers. Each writes the FULL pose into out and returns it. ----

// realisation 0.0-1.8 : SIT -> SHOCK -> CONCERN with widening eyes, rising
// shoulders, head tilting down, comedic mouth tremble at the tail.
function phaseRealisation(u: number, tc: number, out: Pose): Pose {
  // Beats:
  //   0.0-0.34  SIT -> SHOCK (freeze then eyes widen w/ easeOutBack)
  //   0.55-1.0  SHOCK -> CONCERN (head tilts down, settle)
  const toShock = smoothstep(0.0, 0.34, u);
  const widen = easeOutBack(clamp01((u - 0.3) / 0.25));
  blend(out, SIT, SHOCK, toShock);
  out.eyeOpen = lerp(out.eyeOpen, 1.6, widen * (1 - smoothstep(0.6, 1.0, u)));

  const toConcern = easeInOutCubic(clamp01((u - 0.55) / 0.45));
  blend(out, out, CONCERN, toConcern);

  // Shoulders rise then relax (gated: 0 at edges).
  const shoulderRise = bump(tc, 0.7, 1.5);
  out.armRaise += -0.18 * shoulderRise;
  out.bodyScaleY += 0.02 * shoulderRise;

  // Head tilts down as it settles into concern (gated: 0 at the 1.8 seam so the
  // realisation end pose equals CONCERN exactly).
  out.headDown += 0.12 * bump(tc, 1.0, 1.8);

  // Mouth trembles 1.4-1.8 (valueNoise gated by a bump so it is 0 at 1.8 seam).
  const tremble = bump(tc, 1.4, 1.8);
  out.mouthOpen += 0.12 * tremble * (0.5 + 0.5 * valueNoise(tc * 22.0, 1));
  out.mouthWide += 0.05 * tremble * valueNoise(tc * 19.0, 2);
  return out;
}

// crying 1.8-3.8 : CONCERN -> CRY (hold) with head shake, sob bounce,
// alternating eye wipe, mouth open/close.
function phaseCrying(u: number, tc: number, out: Pose): Pose {
  const toCry = easeInOutCubic(clamp01(u / 0.35));
  blend(out, CONCERN, CRY, toCry);

  // Gate all overlays so they are exactly 0 at both edges (1.8 and 3.8).
  const active = windowRamp(tc, 1.8, 3.8, 0.25, 0.25);

  // Head shake.
  out.headTurn += 0.16 * active * Math.sin((tc - 1.8) * 7.5);

  // Shoulder sob bounce.
  const sob = Math.pow(Math.max(0, Math.sin((tc - 1.8) * 5.0)), 6);
  out.bodyScaleY += 0.05 * active * sob;
  out.armRaise += -0.15 * active * sob;
  out.hunch += 0.06 * active * sob;

  // Mouth open/close wail cadence.
  out.mouthOpen += 0.2 * active * (0.5 + 0.5 * Math.sin((tc - 1.8) * 4.0));

  // Alternating eye wipe (symmetric here; panda.ts adds per-side lag).
  const wipePhase = Math.sin((tc - 1.8) * 2.2);
  const wipe = Math.pow(Math.abs(wipePhase), 3) * active;
  out.elbowBend += -0.5 * wipe;
  out.armRaise += -0.25 * wipe;

  // Distressed brow flicker (small, gated to 0 at edges).
  out.browInner += 0.08 * active * (0.5 + 0.5 * valueNoise(tc * 9.0, 3));
  return out;
}

// collapse 3.8-4.6 : CRY -> LIE(roll -1.67). Torso tilts FIRST, arms reach out,
// head lags (spring in panda.ts), legs lose balance, IMPACT at 4.4.
function phaseCollapse(u: number, tc: number, out: Pose): Pose {
  const pitchK = easeInCubic(clamp01(u / 0.85));
  const rollK = easeInCubic(clamp01((u - 0.15) / 0.85));

  copyInto(out, CRY);
  out.pitch = lerp(CRY.pitch, -HALF_PI, pitchK);
  out.roll = lerp(CRY.roll, ROLL_A_START, rollK);

  const bodyK = smoothstep(0.0, 1.0, u);
  out.hunch = lerp(CRY.hunch, LIE.hunch, bodyK);
  out.sway = lerp(CRY.sway, LIE.sway, bodyK);
  out.bodyScaleY = lerp(CRY.bodyScaleY, LIE.bodyScaleY, bodyK);
  out.bodyScaleXZ = lerp(CRY.bodyScaleXZ, LIE.bodyScaleXZ, bodyK);
  out.headDown = lerp(CRY.headDown, LIE.headDown, bodyK);
  out.headLift = lerp(CRY.headLift, LIE.headLift, bodyK);
  out.eyeOpen = lerp(CRY.eyeOpen, LIE.eyeOpen, bodyK);
  out.eyeSquint = lerp(CRY.eyeSquint, LIE.eyeSquint, bodyK);
  out.browInner = lerp(CRY.browInner, LIE.browInner, bodyK);
  out.browLower = lerp(CRY.browLower, LIE.browLower, bodyK);
  out.mouthOpen = lerp(CRY.mouthOpen, LIE.mouthOpen, bodyK);
  out.mouthWide = lerp(CRY.mouthWide, LIE.mouthWide, bodyK);
  out.mouthFrown = lerp(CRY.mouthFrown, LIE.mouthFrown, bodyK);
  out.hipFlex = lerp(CRY.hipFlex, LIE.hipFlex, bodyK);
  out.kneeBend = lerp(CRY.kneeBend, LIE.kneeBend, bodyK);
  out.legSpread = lerp(CRY.legSpread, LIE.legSpread, bodyK);

  // Arms reach out toward the fall then settle (gated to 0 at edges).
  const reach = bump(tc, 3.8, 4.5);
  out.armRaise = lerp(CRY.armRaise, LIE.armRaise, bodyK) + -0.9 * reach;
  out.armSpread = lerp(CRY.armSpread, LIE.armSpread, bodyK) + 0.2 * reach;
  out.elbowBend = lerp(CRY.elbowBend, LIE.elbowBend, bodyK);

  // Legs lose balance (gated to 0 at edges).
  const flail = windowRamp(tc, 3.9, 4.5, 0.15, 0.15);
  out.hipFlex += 0.25 * flail * Math.sin((tc - 3.9) * 12.0);
  return out;
}

// rollingA 4.6-6.4 : LIE roll -1.67 -> +1.75 (flail+kick) -> 0 (hug head).
function phaseRollingA(_u: number, tc: number, out: Pose): Pose {
  copyInto(out, LIE); // base lying pose (roll overwritten below)

  const a1 = clamp01((tc - 4.6) / 1.0);
  const a2 = clamp01((tc - 5.6) / 0.8);

  if (tc < 5.6) {
    out.roll = lerp(ROLL_A_START, ROLL_A_MID, easeInOutCubic(a1));
  } else {
    out.roll = lerp(ROLL_A_MID, ROLL_A_END, easeInOutCubic(a2));
  }

  // Overlays gated to 0 at 4.6 and 6.4.
  const roll1 = windowRamp(tc, 4.6, 5.6, 0.2, 0.15);
  const roll2 = windowRamp(tc, 5.6, 6.4, 0.15, 0.2);

  // Arm flail via value noise (A1).
  out.armRaise += -0.7 * roll1 * valueNoise(tc * 6.0, 4);
  out.armSpread += 0.35 * roll1 * (0.5 + 0.5 * valueNoise(tc * 5.0, 5));
  out.elbowBend += -0.5 * roll1 * valueNoise(tc * 7.0, 6);

  // Leg kick (A1).
  out.hipFlex += 0.4 * roll1 * (0.5 + 0.5 * Math.sin((tc - 4.6) * 9.0));
  out.kneeBend += -0.3 * roll1 * valueNoise(tc * 8.0, 7);
  out.legSpread += 0.2 * roll1 * Math.abs(valueNoise(tc * 6.5, 8));

  // Face during flail: scrunched crying.
  out.eyeSquint = lerp(out.eyeSquint, 1.0, roll1);
  out.mouthOpen = lerp(out.mouthOpen, 0.9, roll1);

  // A2 hug head: elbows fold, arms up covering the face.
  out.armRaise = lerp(out.armRaise, -1.6, roll2);
  out.elbowBend = lerp(out.elbowBend, -1.4, roll2);
  out.armSpread = lerp(out.armSpread, 0.15, roll2);
  out.headDown = lerp(out.headDown, 0.35, roll2);
  return out;
}

// rollingA end pose, used so despair starts exactly where rollingA ended.
function rollingAEnd(out: Pose): Pose {
  return phaseRollingA(1.0, 6.4, out);
}

// despair 6.4-7.6 : BACK stare -> WAIL (WHY at 6.95) -> BACK.
function phaseDespair(u: number, tc: number, out: Pose): Pose {
  rollingAEnd(scratchA);
  const settleToBack = easeOutCubic(clamp01((tc - 6.4) / 0.4));
  blend(out, scratchA, BACK, settleToBack);

  // Comedic pause: hold the stare (gated so 0 at edges).
  const stare = windowRamp(tc, 6.5, 6.9, 0.1, 0.1);
  out.eyeOpen = lerp(out.eyeOpen, 1.1, stare);
  out.mouthOpen += 0.05 * stare * (0.5 + 0.5 * valueNoise(tc * 14.0, 9));

  // WHY burst at 6.95: arms slam up (WAIL) with easeOutBack, then lower.
  const whyUp = easeOutBack(clamp01((tc - 6.95) / 0.25));
  const whyDown = easeInOutCubic(clamp01((tc - 7.3) / 0.3));
  const whyAmount = clamp01(whyUp - whyDown);
  blend(out, out, WAIL, whyAmount);

  // Resolve back exactly to BACK by the phase end (7.6) for the seam.
  const toBackEnd = smoothstep(0.85, 1.0, u);
  blend(out, out, BACK, toBackEnd);
  return out;
}

// rollingB 7.6-9.2 : BACK -> punch carpet x2 + kick -> roll right covering face
// -> roll left flailing to roll ~-1.7.
function phaseRollingB(_u: number, tc: number, out: Pose): Pose {
  copyInto(out, BACK);

  // Roll trajectory: on back for the punches, then right (covering face),
  // then left to ROLL_B_END.
  let roll = 0.0;
  if (tc < 8.3) {
    roll = 0.0;
  } else if (tc < 8.7) {
    roll = lerp(0.0, 1.2, easeInOutCubic((tc - 8.3) / 0.4));
  } else {
    roll = lerp(1.2, ROLL_B_END, easeInOutCubic((tc - 8.7) / 0.5));
  }
  out.roll = roll;

  const active = windowRamp(tc, 7.6, 9.2, 0.2, 0.2);

  // Punch carpet: two downward arm jabs around 7.85 and 8.15.
  const punch1 = bump(tc, 7.7, 8.0);
  const punch2 = bump(tc, 8.0, 8.3);
  const punch = punch1 + punch2;
  out.armRaise += 0.5 * punch;
  out.elbowBend += -0.3 * punch;

  // Kick feet around 8.55 (kickBurst).
  const kick = bump(tc, 8.4, 8.75);
  out.hipFlex += 0.5 * kick * (0.5 + 0.5 * Math.sin((tc - 8.4) * 16.0));
  out.kneeBend += -0.4 * kick;
  out.legSpread += 0.2 * kick;

  // Cover face while rolling right 8.3-8.7.
  const cover = windowRamp(tc, 8.3, 8.7, 0.1, 0.1);
  out.armRaise = lerp(out.armRaise, -1.5, cover);
  out.elbowBend = lerp(out.elbowBend, -1.5, cover);
  out.headDown = lerp(out.headDown, 0.3, cover);

  // Roll-left flail 8.7-9.2.
  const flail = windowRamp(tc, 8.7, 9.2, 0.1, 0.15);
  out.armRaise += -0.6 * flail * valueNoise(tc * 6.0, 10);
  out.armSpread += 0.3 * flail * Math.abs(valueNoise(tc * 5.0, 11));
  out.hipFlex += 0.3 * flail * Math.sin((tc - 8.7) * 11.0);

  // General distressed face across the phase.
  out.eyeSquint = lerp(out.eyeSquint, 0.9, active);
  out.mouthOpen = lerp(out.mouthOpen, 0.8, active);
  out.browInner = lerp(out.browInner, 0.6, active);
  return out;
}

// rollingB end pose, used so exhaustion starts exactly where rollingB ended.
function rollingBEnd(out: Pose): Pose {
  return phaseRollingB(1.0, 9.2, out);
}

// exhaustion 9.2-11.2 : decaying rocking -> breathing -> leg twitch -> head lift
// toward monitor -> MONITOR FLASH ~10.65 -> eye twitch.
function phaseExhaustion(_u: number, tc: number, out: Pose): Pose {
  rollingBEnd(scratchB);
  const toExhausted = easeOutCubic(clamp01((tc - 9.2) / 0.9));
  blend(out, scratchB, EXHAUSTED, toExhausted);

  // Overlays gated to 0 at both edges (9.2 and 11.2).
  const active = windowRamp(tc, 9.2, 11.2, 0.3, 0.2);

  // Residual rocking decays across the phase.
  const rockDecay = 1.0 - smoothstep(9.2, 10.4, tc);
  out.roll += 0.12 * active * rockDecay * Math.sin((tc - 9.2) * 4.0);

  // Breathing: tiny bodyScaleY sine.
  out.bodyScaleY += 0.015 * active * Math.sin((tc - 9.2) * 1.6);

  // Leg twitch: rare value-noise spike.
  const twitchN = valueNoise(tc * 3.0, 12);
  const twitch = twitchN > 0.6 ? (twitchN - 0.6) / 0.4 : 0;
  out.hipFlex += 0.15 * active * twitch;

  // Shoulder hiccup: occasional small pulse.
  const hiccup = Math.pow(Math.max(0, Math.sin((tc - 9.2) * 2.3)), 8);
  out.bodyScaleY += 0.02 * active * hiccup;
  out.armRaise += -0.08 * active * hiccup;

  // Slowly lifts head to look at monitor 10.0-11.0.
  const lift = easeOutCubic(clamp01((tc - 10.0) / 1.0));
  out.headLift += 0.25 * lift;
  out.headDown = lerp(out.headDown, -0.1, lift * 0.5);
  out.headTurn = lerp(out.headTurn, 0.35, lift);

  // Eye twitch after the monitor flash (~10.65): a quick dip in eyeOpen.
  const twitchWin = bump(tc, 10.7, 10.95);
  out.eyeOpen -= 0.35 * twitchWin;
  return out;
}

// exhaustion end pose, used so recovery starts exactly where exhaustion ended.
function exhaustionEnd(out: Pose): Pose {
  return phaseExhaustion(1.0, 11.2, out);
}

// recovery 11.2-12.0 : EXHAUSTED -> SIT (sit back up, easeOutBack settle).
function phaseRecovery(u: number, _tc: number, out: Pose): Pose {
  exhaustionEnd(scratchA);
  const rise = easeInOutCubic(clamp01(u / 0.8));
  const settle = easeOutBack(clamp01(u), 1.2);
  blend(out, scratchA, SIT, rise);
  // Blend the last stretch fully onto SIT so tc=12 == tc=0 exactly.
  blend(out, out, SIT, smoothstep(0.8, 1.0, u));
  // Torso overshoot on the way up (0 at both ends by construction).
  const overshoot = clamp01(settle - rise);
  out.hunch += -0.06 * overshoot;
  out.pitch += -0.04 * overshoot;
  return out;
}

// ---------------------------------------------------------------------------
// samplePose: dispatch to the active phase.
// ---------------------------------------------------------------------------

export function samplePose(tc: number, out: Pose): PhaseName {
  let t = tc % CYCLE;
  if (t < 0) t += CYCLE;

  for (let i = 0; i < PHASES.length; i++) {
    const span = PHASES[i];
    if (t < span.end || i === PHASES.length - 1) {
      const u = seg(t, span.start, span.end);
      switch (span.name) {
        case 'realisation':
          phaseRealisation(u, t, out);
          break;
        case 'crying':
          phaseCrying(u, t, out);
          break;
        case 'collapse':
          phaseCollapse(u, t, out);
          break;
        case 'rollingA':
          phaseRollingA(u, t, out);
          break;
        case 'despair':
          phaseDespair(u, t, out);
          break;
        case 'rollingB':
          phaseRollingB(u, t, out);
          break;
        case 'exhaustion':
          phaseExhaustion(u, t, out);
          break;
        case 'recovery':
          phaseRecovery(u, t, out);
          break;
      }
      clampPose(out);
      return span.name;
    }
  }
  copyInto(out, SIT);
  return 'realisation';
}

// Keep every channel within its documented range.
function clampPose(p: Pose): void {
  p.pitch = Math.min(0.05, Math.max(-HALF_PI - 0.05, p.pitch));
  p.eyeOpen = Math.min(1.6, Math.max(0, p.eyeOpen));
  p.eyeSquint = clamp01(p.eyeSquint);
  p.mouthOpen = clamp01(p.mouthOpen);
  p.mouthWide = Math.min(1, Math.max(-1, p.mouthWide));
  p.mouthFrown = clamp01(p.mouthFrown);
  p.bodyScaleY = Math.max(0.1, p.bodyScaleY);
  p.bodyScaleXZ = Math.max(0.1, p.bodyScaleXZ);
}

// ---------------------------------------------------------------------------
// 4.3 Events (declared only; the integrator edge-detects and fires them).
// ---------------------------------------------------------------------------

export type EventName = 'impact' | 'punch' | 'why' | 'monitorFlash' | 'kickBurst';

export interface TimedEvent {
  name: EventName;
  at: number;
  strength: number;
}

export const EVENTS: readonly TimedEvent[] = [
  { name: 'impact', at: 4.4, strength: 1.0 },
  { name: 'why', at: 6.95, strength: 1.0 },
  { name: 'punch', at: 7.85, strength: 0.6 },
  { name: 'punch', at: 8.15, strength: 0.6 },
  { name: 'kickBurst', at: 8.55, strength: 0.5 },
  { name: 'monitorFlash', at: 10.65, strength: 1.0 },
];

// ---------------------------------------------------------------------------
// 4.4 Camera choreography helpers.
// ---------------------------------------------------------------------------

export interface CameraShot {
  basePos: THREE.Vector3;
  baseTarget: THREE.Vector3;
  fov: number;
  pushIn: number; // 0..1 extra dolly toward target, peaks during WHY
  handheld: number; // 0..1 gentle handheld during biggest tantrum
  followX: number; // 0..1 horizontal follow of panda slide
}

// Reusable vectors so sampleCamera does not allocate per frame.
const CAM_BASE_POS = new THREE.Vector3(1.6, 2.7, 7.4);
const CAM_BASE_TARGET = new THREE.Vector3(-0.25, 0.8, -0.5);
const shotBasePos = new THREE.Vector3();
const shotBaseTarget = new THREE.Vector3();

export function sampleCamera(tc: number): CameraShot {
  let t = tc % CYCLE;
  if (t < 0) t += CYCLE;

  // pushIn ramps up around the WHY beat (6.7-7.2), holds, eases back by 7.6.
  const pushUp = smoothstep(6.7, 7.0, t);
  const pushDown = smoothstep(7.2, 7.6, t);
  const pushIn = clamp01(pushUp - pushDown);

  // handheld is nonzero across despair + rollingB (6.4-9.2), soft ramps at edges.
  const handheld = windowRamp(t, 6.4, 9.2, 0.4, 0.6);

  // followX tracks the panda slide softly during the rolling phases (4.6-9.2).
  const followX = windowRamp(t, 4.6, 9.2, 0.6, 0.6) * 0.8;

  shotBasePos.copy(CAM_BASE_POS);
  shotBaseTarget.copy(CAM_BASE_TARGET);

  return {
    basePos: shotBasePos,
    baseTarget: shotBaseTarget,
    fov: 38,
    pushIn,
    handheld,
    followX,
  };
}

// ---------------------------------------------------------------------------
// Continuity self-check (called by the integrator's dev test; never imports TS
// from a .mjs). Samples just before/after every phase boundary and the loop
// seam, returning channel names whose jump exceeds a threshold. High-frequency
// tremble channels are excluded because overlays are intentionally lively there.
// ---------------------------------------------------------------------------

const TREMBLE_CHANNELS: readonly (keyof Pose)[] = [
  'headTurn', // head shake during crying
  'mouthOpen', // wail cadence / tremble
  'mouthWide', // tremble
  'browInner', // distressed flicker
];

export function checkContinuity(): string[] {
  const problems: string[] = [];
  const eps = 1e-3; // sampling offset (seconds) either side of a boundary
  const threshold = 0.06; // max allowed per-channel jump across a boundary

  const before: Pose = clonePose(SIT);
  const after: Pose = clonePose(SIT);

  const isTremble = (key: keyof Pose): boolean => {
    for (let i = 0; i < TREMBLE_CHANNELS.length; i++) {
      if (TREMBLE_CHANNELS[i] === key) return true;
    }
    return false;
  };

  // Internal phase edges plus the loop seam (0 == CYCLE).
  const boundaries: number[] = [];
  for (let i = 1; i < PHASES.length; i++) {
    boundaries.push(PHASES[i].start);
  }
  boundaries.push(CYCLE);

  for (let b = 0; b < boundaries.length; b++) {
    const edge = boundaries[b];
    samplePose(edge - eps, before);
    samplePose(edge === CYCLE ? eps : edge + eps, after);

    for (let i = 0; i < POSE_KEYS.length; i++) {
      const key = POSE_KEYS[i];
      if (isTremble(key)) continue;
      const jump = Math.abs(after[key] - before[key]);
      if (jump > threshold) {
        problems.push(`${edge.toFixed(2)}s:${key}=${jump.toFixed(4)}`);
      }
    }
  }

  // Exact seam identity (0 vs CYCLE) on a tighter tolerance, at the marks.
  samplePose(0, before);
  samplePose(CYCLE, after);
  for (let i = 0; i < POSE_KEYS.length; i++) {
    const key = POSE_KEYS[i];
    const jump = Math.abs(after[key] - before[key]);
    if (jump > 1e-4) {
      problems.push(`seam0==CYCLE:${key}=${jump.toFixed(6)}`);
    }
  }

  return problems;
}
