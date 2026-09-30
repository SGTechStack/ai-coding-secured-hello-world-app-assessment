import * as THREE from 'three'
import type { Pose } from './choreography'
import { Spring, clamp01, lerp } from './easing'
import { furGeometry, surfaceTexture } from './surfaces'

// =============================================================================
// panda.ts - the cute procedural office-worker panda rig.
//
// Owns: rig construction (section-2 dims), applyPose (gross channel mapping +
// per-side arm/leg mapping + updateFace + updateSecondaryMotion), spring-based
// secondary motion, tear-anchor / body world getters for the camera, tears and
// office modules.
//
// Coordinate + sign conventions are the ones fixed in the contract, section 1:
//   - arm raise FORWARD  -> shoulder.rotation.x NEGATIVE
//   - arm spread OUTWARD  -> shoulder.rotation.z = side * armSpread (side +/-1)
//   - elbow bend to face  -> elbow.rotation.x NEGATIVE
//   - inner brow up       -> brow.rotation.z = side * browInner
//   - head lift off floor -> neck.rotation.x -= headLift (in the rollGroup frame)
//   - head look down      -> neck.rotation.x += headDown
//   - roll / no-slip slide -> positive roll moves body along +local x.
// =============================================================================

const PI = Math.PI
const HALF_PI = PI / 2

// -----------------------------------------------------------------------------
// Section 2 dimensions, exported so choreography and the integrator camera read
// the same numbers instead of duplicating magic values.
// -----------------------------------------------------------------------------

export interface PandaDims {
  readonly SIT_Y: number
  readonly LIE_Y: number
  readonly LIE_BOB: number
  readonly SIT_SLIDE: number
  readonly BODY_ROLL_RADIUS: number
  readonly headCentre: THREE.Vector3
  readonly headRadius: number
  readonly neckPivot: THREE.Vector3
  readonly spinePivot: THREE.Vector3
  readonly shoulderL: THREE.Vector3
  readonly shoulderR: THREE.Vector3
  readonly upperSleeveLength: number
  readonly forearmLength: number
  readonly hipL: THREE.Vector3
  readonly hipR: THREE.Vector3
  readonly upperLegLength: number
  readonly lowerLegLength: number
  readonly tiePivot: THREE.Vector3
  readonly tieBladeLength: number
  readonly earL: THREE.Vector3
  readonly earR: THREE.Vector3
  readonly eyeL: THREE.Vector3
  readonly eyeR: THREE.Vector3
  readonly tearAnchorLocal: THREE.Vector3
  readonly browL: THREE.Vector3
  readonly browR: THREE.Vector3
  readonly mouthGroup: THREE.Vector3
}

export const PANDA_DIMS: PandaDims = {
  SIT_Y: 0.12, // trouser seat (rollGroup y -0.10) rests on the carpet
  LIE_Y: 0.44,
  LIE_BOB: 0.07,
  SIT_SLIDE: 0.0,
  BODY_ROLL_RADIUS: 0.62,
  headCentre: new THREE.Vector3(0, 0.3, 0.02),
  headRadius: 0.62,
  neckPivot: new THREE.Vector3(0, 1.3, 0.02),
  spinePivot: new THREE.Vector3(0, 0.55, 0),
  shoulderL: new THREE.Vector3(-0.66, 1.02, 0.02),
  shoulderR: new THREE.Vector3(0.66, 1.02, 0.02),
  upperSleeveLength: 0.52,
  forearmLength: 0.46,
  hipL: new THREE.Vector3(-0.34, 0.3, 0.04),
  hipR: new THREE.Vector3(0.34, 0.3, 0.04),
  upperLegLength: 0.34,
  lowerLegLength: 0.3,
  tiePivot: new THREE.Vector3(0, 1.06, 0.51),
  tieBladeLength: 0.62,
  earL: new THREE.Vector3(-0.5, 0.52, -0.05),
  earR: new THREE.Vector3(0.5, 0.52, -0.05),
  eyeL: new THREE.Vector3(-0.26, 0.1, 0.54),
  eyeR: new THREE.Vector3(0.26, 0.1, 0.54),
  tearAnchorLocal: new THREE.Vector3(0.02, -0.1, 0.1),
  browL: new THREE.Vector3(-0.26, 0.3, 0.52),
  browR: new THREE.Vector3(0.26, 0.3, 0.52),
  mouthGroup: new THREE.Vector3(0, -0.22, 0.55),
}

const YAW = 0.5

// -----------------------------------------------------------------------------
// Public rig handle.
// -----------------------------------------------------------------------------

export interface PandaRig {
  root: THREE.Group
  slide: number
  height: number
  leftTearAnchor: THREE.Object3D
  rightTearAnchor: THREE.Object3D
  applyPose(pose: Pose, clock: number, dt: number): void
  settle(pose: Pose): void
  kickSquash(strength: number): void
  /** World position of the body node (camera + office follow). */
  getBodyWorldPosition(out: THREE.Vector3): THREE.Vector3
  /** World velocity estimate of an eye anchor (side -1 = left, +1 = right). */
  getEyeVelocity(side: 1 | -1, out: THREE.Vector3): THREE.Vector3
  /** World quaternion of the head (tears inherit head orientation/motion). */
  getHeadWorldQuaternion(out: THREE.Quaternion): THREE.Quaternion
  dispose(): void
}

// -----------------------------------------------------------------------------
// Per-side arm/leg handle bundles.
// -----------------------------------------------------------------------------

interface ArmHandle {
  shoulder: THREE.Group
  elbow: THREE.Group
  side: 1 | -1
  // spring lag state
  raiseLag: Spring
  spreadLag: Spring
  elbowLag: Spring
}

interface LegHandle {
  hip: THREE.Group
  knee: THREE.Group
  side: 1 | -1
  flexLag: Spring
}

interface EarHandle {
  pivot: THREE.Group
  side: 1 | -1
  bounce: Spring
}

interface EyeHandle {
  group: THREE.Group
  lids: THREE.Mesh
  pupil: THREE.Mesh
  tearAnchor: THREE.Object3D
  side: 1 | -1
  // previous world position for velocity estimate
  prevWorld: THREE.Vector3
  velocity: THREE.Vector3
}

interface BrowHandle {
  pivot: THREE.Group
  side: 1 | -1
}

// =============================================================================
// createPanda
// =============================================================================

export function createPanda(): PandaRig {
  // --- shared geometry: one unit sphere reused for every ellipsoid ---------
  const sphere = new THREE.SphereGeometry(1, 48, 32)
  // shared capsules for limbs / tie / snout tube-like shapes
  const capsuleLimb = new THREE.CapsuleGeometry(0.16, 0.4, 6, 12)
  const capsuleForearm = new THREE.CapsuleGeometry(0.14, 0.34, 6, 12)
  const capsuleLeg = new THREE.CapsuleGeometry(0.17, 0.28, 6, 12)
  const capsuleShin = new THREE.CapsuleGeometry(0.15, 0.22, 6, 12)
  const boxGeo = new THREE.BoxGeometry(1, 1, 1)

  const ownedGeometries = new Set<THREE.BufferGeometry>([
    sphere,
    capsuleLimb,
    capsuleForearm,
    capsuleLeg,
    capsuleShin,
    boxGeo,
  ])

  // --- shared materials -----------------------------------------------------
  const furTexture = surfaceTexture('fur')
  const clothTexture = surfaceTexture('weave')
  const matWhite = new THREE.MeshPhysicalMaterial({ color: 0xe8e2d5, roughness: 0.96, bumpMap: furTexture, bumpScale: 0.004, sheen: 0.65, sheenColor: 0xfff3df })
  const matBlack = new THREE.MeshPhysicalMaterial({ color: 0x16191b, roughness: 0.94, bumpMap: furTexture, bumpScale: 0.004, sheen: 0.5, sheenColor: 0x686b70 })
  const matCream = new THREE.MeshPhysicalMaterial({ color: 0xe3d7c4, roughness: 0.96, bumpMap: furTexture, bumpScale: 0.003, sheen: 0.35 })
  const matShirt = new THREE.MeshStandardMaterial({ color: 0xe6eaf0, roughness: 0.91, bumpMap: clothTexture, bumpScale: 0.008 })
  const matTie = new THREE.MeshPhysicalMaterial({ color: 0x8a2926, roughness: 0.65, bumpMap: clothTexture, bumpScale: 0.005, sheen: 0.4 })
  const matTrouser = new THREE.MeshStandardMaterial({ color: 0x2c3140, roughness: 0.92, bumpMap: clothTexture, bumpScale: 0.01 })
  const matBelt = new THREE.MeshStandardMaterial({ color: 0x1b1d24, roughness: 0.6 })
  const matSclera = new THREE.MeshPhysicalMaterial({ color: 0xd5c9b6, roughness: 0.18, clearcoat: 1 })
  const matIris = new THREE.MeshPhysicalMaterial({ color: 0x392719, roughness: 0.22, clearcoat: 1, clearcoatRoughness: 0.08 })
  const matPupil = new THREE.MeshPhysicalMaterial({ color: 0x060708, roughness: 0.08, clearcoat: 1 })
  const matNose = new THREE.MeshPhysicalMaterial({ color: 0x151315, roughness: 0.3, clearcoat: 0.45, bumpMap: furTexture, bumpScale: 0.004 })
  const matHighlight = new THREE.MeshBasicMaterial({ color: 0xffffff })
  const matMouth = new THREE.MeshStandardMaterial({ color: 0x3a1f26, roughness: 0.6 })
  const matTongue = new THREE.MeshStandardMaterial({ color: 0xd9737f, roughness: 0.5 })
  const matBrow = matBlack

  const ownedMaterials = new Set<THREE.Material>([
    matWhite,
    matBlack,
    matCream,
    matShirt,
    matTie,
    matTrouser,
    matBelt,
    matSclera,
    matIris,
    matNose,
    matPupil,
    matHighlight,
    matMouth,
    matTongue,
  ])

  const coatGeometry = furGeometry()
  ownedGeometries.add(coatGeometry)
  const coats = new Map<THREE.Material, THREE.Material>()
  for (const base of [matWhite, matBlack, matCream]) {
    const coat = new THREE.MeshPhysicalMaterial({
      color: base.color, roughness: 1, vertexColors: true,
      side: THREE.DoubleSide, sheen: 0.65, sheenColor: base.sheenColor,
    })
    coats.set(base, coat)
    ownedMaterials.add(coat)
  }

  // --- helpers --------------------------------------------------------------
  function ellipsoid(
    parent: THREE.Object3D,
    material: THREE.Material,
    px: number,
    py: number,
    pz: number,
    sx: number,
    sy: number,
    sz: number,
  ): THREE.Mesh {
    const mesh = new THREE.Mesh(sphere, material)
    mesh.position.set(px, py, pz)
    mesh.scale.set(sx, sy, sz)
    mesh.castShadow = true
    mesh.receiveShadow = true
    parent.add(mesh)
    const coat = coats.get(material)
    if (coat) {
      const hairs = new THREE.Mesh(coatGeometry, coat)
      hairs.receiveShadow = true
      mesh.add(hairs)
    }
    return mesh
  }

  function box(
    parent: THREE.Object3D,
    material: THREE.Material,
    px: number,
    py: number,
    pz: number,
    sx: number,
    sy: number,
    sz: number,
  ): THREE.Mesh {
    const mesh = new THREE.Mesh(boxGeo, material)
    mesh.position.set(px, py, pz)
    mesh.scale.set(sx, sy, sz)
    mesh.castShadow = true
    mesh.receiveShadow = true
    parent.add(mesh)
    return mesh
  }

  // =========================================================================
  // Rig hierarchy (contract section 1)
  // =========================================================================

  const root = new THREE.Group()
  root.rotation.y = YAW

  const body = new THREE.Group()
  body.position.set(PANDA_DIMS.SIT_SLIDE, PANDA_DIMS.SIT_Y, -0.1)
  root.add(body)

  const pitchGroup = new THREE.Group()
  body.add(pitchGroup)

  const rollGroup = new THREE.Group()
  pitchGroup.add(rollGroup)

  // --- trousers + belt (attached to rollGroup, below the waist) ------------
  const trousers = new THREE.Group()
  rollGroup.add(trousers)
  ellipsoid(trousers, matTrouser, 0, 0.32, 0.02, 0.62, 0.42, 0.5) // seat / hips
  box(trousers, matBelt, 0, 0.56, 0.34, 0.9, 0.09, 0.14) // belt front

  // --- legs (hip pivots) ---------------------------------------------------
  const legs: LegHandle[] = ([-1, 1] as const).map((side) => {
    const hip = new THREE.Group()
    const hp = side < 0 ? PANDA_DIMS.hipL : PANDA_DIMS.hipR
    hip.position.copy(hp)
    rollGroup.add(hip)

    // upper leg (trouser) hangs down from hip
    const upper = new THREE.Mesh(capsuleLeg, matTrouser)
    upper.position.set(0, -PANDA_DIMS.upperLegLength / 2, 0)
    upper.castShadow = true
    upper.receiveShadow = true
    hip.add(upper)

    const knee = new THREE.Group()
    knee.position.set(0, -PANDA_DIMS.upperLegLength, 0)
    hip.add(knee)

    const shin = new THREE.Mesh(capsuleShin, matTrouser)
    shin.position.set(0, -PANDA_DIMS.lowerLegLength / 2, 0)
    shin.castShadow = true
    shin.receiveShadow = true
    knee.add(shin)

    // black foot + paw pad
    const foot = ellipsoid(knee, matBlack, 0, -PANDA_DIMS.lowerLegLength, 0.08, 0.19, 0.13, 0.28)
    void foot
    ellipsoid(knee, matCream, 0, -PANDA_DIMS.lowerLegLength - 0.02, 0.2, 0.09, 0.06, 0.08) // pad

    return { hip, knee, side, flexLag: new Spring(120, 22) }
  })

  // --- spine / waist pivot -------------------------------------------------
  const spine = new THREE.Group()
  spine.position.copy(PANDA_DIMS.spinePivot)
  rollGroup.add(spine)

  // torso / chest (rounded), shirt over it
  ellipsoid(spine, matBlack, 0, 0.55, -0.02, 0.6, 0.66, 0.46) // dark fur torso core
  const shirt = ellipsoid(spine, matShirt, 0, 0.58, 0.08, 0.58, 0.64, 0.44) // white shirt
  void shirt
  // untucked shirt hem / flap poking out below the belt line
  const hem = box(spine, matShirt, 0.12, 0.02, 0.34, 0.4, 0.22, 0.14)
  hem.rotation.z = 0.25

  // open collar (two small angled flaps)
  const collar = new THREE.Group()
  collar.position.set(0, 1.06, 0.43)
  spine.add(collar)
  for (const s of [-1, 1]) {
    const flap = box(collar, matShirt, s * 0.14, 0.02, 0.02, 0.2, 0.16, 0.05)
    flap.rotation.z = s * -0.5
    flap.rotation.y = s * 0.3
  }

  // --- tie pivot -> tie blade ----------------------------------------------
  const tiePivot = new THREE.Group()
  tiePivot.position.copy(PANDA_DIMS.tiePivot)
  spine.add(tiePivot)
  // knot
  ellipsoid(tiePivot, matTie, 0, 0, 0, 0.08, 0.1, 0.06)
  const tieShape = new THREE.Shape()
  tieShape.moveTo(-0.045, PANDA_DIMS.tieBladeLength / 2)
  tieShape.lineTo(0.045, PANDA_DIMS.tieBladeLength / 2)
  tieShape.lineTo(0.085, -PANDA_DIMS.tieBladeLength / 2 + 0.09)
  tieShape.lineTo(0, -PANDA_DIMS.tieBladeLength / 2)
  tieShape.lineTo(-0.085, -PANDA_DIMS.tieBladeLength / 2 + 0.09)
  tieShape.closePath()
  const tieGeometry = new THREE.ExtrudeGeometry(tieShape, { depth: 0.025, bevelEnabled: true, bevelSize: 0.007, bevelThickness: 0.007, bevelSegments: 2, steps: 1 })
  ownedGeometries.add(tieGeometry)
  const tieBlade = new THREE.Mesh(tieGeometry, matTie)
  tieBlade.position.set(0, -PANDA_DIMS.tieBladeLength / 2, 0.01)
  tieBlade.castShadow = true
  tieBlade.receiveShadow = true
  tiePivot.add(tieBlade)
  // a subtle loosened offset so the knot sits below the collar
  tiePivot.rotation.x = 0.12

  // --- neck pivot (YXZ) -> head --------------------------------------------
  const neck = new THREE.Group()
  neck.rotation.order = 'YXZ'
  neck.position.copy(PANDA_DIMS.neckPivot)
  spine.add(neck)

  const head = new THREE.Group()
  head.position.copy(PANDA_DIMS.headCentre)
  neck.add(head)

  // big round white head
  ellipsoid(head, matWhite, 0, 0, 0, PANDA_DIMS.headRadius * 1.06, PANDA_DIMS.headRadius * 0.98, PANDA_DIMS.headRadius * 0.95)
  // Full cheek volumes soften the transition into the short bear muzzle.
  for (const side of [-1, 1]) {
    ellipsoid(head, matWhite, side * 0.34, -0.22, 0.28, 0.28, 0.27, 0.25)
  }

  // ears (pivots)
  const ears: EarHandle[] = ([-1, 1] as const).map((side) => {
    const pivot = new THREE.Group()
    const ep = side < 0 ? PANDA_DIMS.earL : PANDA_DIMS.earR
    pivot.position.copy(ep)
    head.add(pivot)
    ellipsoid(pivot, matBlack, 0, 0.06, 0, 0.22, 0.24, 0.16)
    ellipsoid(pivot, matNose, 0, 0.065, 0.125, 0.125, 0.15, 0.04)
    return { pivot, side, bounce: new Spring(150, 15) }
  })

  // eye groups (droopy black patch + sclera + pupil + highlight + tear anchor)
  const eyes: EyeHandle[] = ([-1, 1] as const).map((side) => {
    const group = new THREE.Group()
    const ep = side < 0 ? PANDA_DIMS.eyeL : PANDA_DIMS.eyeR
    group.position.copy(ep)
    head.add(group)

    // droopy black eye patch (teardrop tilt), inner-top to outer-bottom.
    // The patch stays unscaled; only the eyeball group squeezes.
    const patch = ellipsoid(group, matBlack, side * 0.012, -0.025, -0.035, 0.205, 0.275, 0.105)
    patch.rotation.z = side * 0.45

    const ball = new THREE.Group()
    group.add(ball)
    // white sclera
    ellipsoid(ball, matSclera, 0, 0, 0.065, 0.093, 0.105, 0.065)
    ellipsoid(ball, matIris, 0, -0.003, 0.115, 0.077, 0.086, 0.04)
    // pupil
    const pupil = ellipsoid(ball, matPupil, 0, -0.01, 0.146, 0.047, 0.057, 0.023)
    // highlight
    ellipsoid(ball, matHighlight, 0.023, 0.026, 0.166, 0.014, 0.019, 0.006)
    // lids: a thin black lash line that slides down over the squeezed eye
    const lids = ellipsoid(ball, matBlack, 0, 0.13, 0.12, 0.14, 0.018, 0.05)

    // tear anchor (lower-inner corner)
    const tearAnchor = new THREE.Object3D()
    tearAnchor.position.set(
      side * PANDA_DIMS.tearAnchorLocal.x,
      PANDA_DIMS.tearAnchorLocal.y,
      PANDA_DIMS.tearAnchorLocal.z,
    )
    group.add(tearAnchor)

    return {
      group: ball,
      lids,
      pupil,
      tearAnchor,
      side,
      prevWorld: new THREE.Vector3(),
      velocity: new THREE.Vector3(),
    }
  })

  // brows (pivots)
  const brows: BrowHandle[] = ([-1, 1] as const).map((side) => {
    const pivot = new THREE.Group()
    const bp = side < 0 ? PANDA_DIMS.browL : PANDA_DIMS.browR
    pivot.position.copy(bp)
    head.add(pivot)
    const bar = ellipsoid(pivot, matBrow, 0, 0, 0, 0.13, 0.032, 0.037)
    void bar
    return { pivot, side }
  })

  // cream muzzle + nose
  ellipsoid(head, matCream, -0.095, -0.145, 0.51, 0.17, 0.15, 0.145)
  ellipsoid(head, matCream, 0.095, -0.145, 0.51, 0.17, 0.15, 0.145)
  ellipsoid(head, matNose, 0, -0.065, 0.647, 0.112, 0.074, 0.058)
  for (const side of [-1, 1]) {
    ellipsoid(head, matPupil, side * 0.06, -0.084, 0.692, 0.022, 0.012, 0.008)
  }

  // mouth group: hole + tongue + frown arc
  const mouthGroup = new THREE.Group()
  mouthGroup.position.copy(PANDA_DIMS.mouthGroup)
  head.add(mouthGroup)
  const mouthHole = ellipsoid(mouthGroup, matMouth, 0, 0, 0, 0.16, 0.1, 0.06)
  void mouthHole
  const tongue = ellipsoid(mouthGroup, matTongue, 0, -0.03, 0.04, 0.1, 0.05, 0.04)
  // frown arc: a small upper-half torus (∩) sitting over the mouth hole
  const frownArcGeo = new THREE.TorusGeometry(0.11, 0.022, 6, 18, Math.PI)
  ownedGeometries.add(frownArcGeo)
  const frownArc = new THREE.Mesh(frownArcGeo, matMouth)
  frownArc.position.set(0, 0.0, 0.05)
  mouthGroup.add(frownArc)

  // --- shoulders / arms (pivots) -------------------------------------------
  const arms: ArmHandle[] = ([-1, 1] as const).map((side) => {
    const shoulder = new THREE.Group()
    const sp = side < 0 ? PANDA_DIMS.shoulderL : PANDA_DIMS.shoulderR
    shoulder.position.copy(sp)
    spine.add(shoulder)

    // white shirt sleeve (upper arm) hanging down from shoulder
    const sleeve = new THREE.Mesh(capsuleLimb, matShirt)
    sleeve.position.set(0, -PANDA_DIMS.upperSleeveLength / 2, 0)
    sleeve.castShadow = true
    sleeve.receiveShadow = true
    shoulder.add(sleeve)

    const elbow = new THREE.Group()
    elbow.position.set(0, -PANDA_DIMS.upperSleeveLength, 0)
    shoulder.add(elbow)

    // forearm (shirt) + black paw
    const forearm = new THREE.Mesh(capsuleForearm, matShirt)
    forearm.position.set(0, -PANDA_DIMS.forearmLength / 2, 0)
    forearm.castShadow = true
    forearm.receiveShadow = true
    elbow.add(forearm)
    ellipsoid(elbow, matBlack, 0, -PANDA_DIMS.forearmLength, 0.02, 0.17, 0.16, 0.17) // paw

    return {
      shoulder,
      elbow,
      side,
      raiseLag: new Spring(140, 20),
      spreadLag: new Spring(140, 20),
      elbowLag: new Spring(140, 20),
    }
  })

  // =========================================================================
  // Secondary-motion springs (head lag, tie, squash)
  // =========================================================================

  const headLagX = new Spring(90, 17)
  const headLagY = new Spring(90, 17)
  const headLagZ = new Spring(90, 17)
  const tieSwingX = new Spring(60, 9) // gravity + accel driven, along blade forward/back
  const tieSwingZ = new Spring(60, 9) // lateral
  const squash = new Spring(180, 16) // vertical squash spring (kicked on impact)

  // scratch for tie world-accel estimation
  const tieWorldPrev = new THREE.Vector3()
  const tieWorldVel = new THREE.Vector3()
  const tieWorldNow = new THREE.Vector3()
  let tieHasPrev = false

  // squash "position" spring: value is extra vertical squash; velocity from kick
  // We reuse Spring but drive it toward 0 so a kick decays back to neutral.

  // scratch objects (no per-frame allocation)
  const scratchVec = new THREE.Vector3()

  // =========================================================================
  // Face
  // =========================================================================

  function updateFace(pose: Pose): void {
    // eyes: eyeOpen scales group.y; eyeSquint squeezes further + lowers lids
    const openBase = clamp01(pose.eyeOpen / 1.6) // normalised for lid mapping
    const squeeze = clamp01(pose.eyeSquint)
    for (const eye of eyes) {
      // Only the eyeball (sclera/pupil/highlight/lash) squeezes; the black
      // patch keeps its shape. Wide = taller and a touch wider; crying = slit.
      const sy = Math.max(0.1, Math.min(1.6, pose.eyeOpen) * (1 - 0.75 * squeeze))
      const sx = 1 + 0.25 * Math.max(0, pose.eyeOpen - 1)
      eye.group.scale.set(sx, sy, 1)
      // pupil drops slightly when squinting and hides inside a shut slit
      eye.pupil.position.y = -0.01 - 0.05 * squeeze
      eye.pupil.visible = sy > 0.3
      // lash line rides the top of the squeezed eye; constant visual thickness
      const closed = clamp01(1 - openBase * 1.6 + squeeze)
      eye.lids.visible = closed > 0.15
      eye.lids.scale.y = (0.016 + 0.02 * closed) / sy
    }

    // brows: inner-end raise = side * browInner; whole-brow lower = -browLower
    for (const brow of brows) {
      brow.pivot.rotation.z = brow.side * pose.browInner
      brow.pivot.position.y = (brow.side < 0 ? PANDA_DIMS.browL.y : PANDA_DIMS.browR.y) - pose.browLower
    }

    // mouth: open -> scale.y, wide -> scale.x
    mouthGroup.scale.set(
      1 + 0.4 * pose.mouthWide,
      lerp(0.4, 2.2, clamp01(pose.mouthOpen)),
      1,
    )
    tongue.visible = pose.mouthOpen > 0.35
    // frown arc (∩) reads only while the mouth is mostly closed
    frownArc.visible = pose.mouthOpen < 0.4 && pose.mouthFrown > 0.05
    frownArc.scale.set(1, (0.25 + 0.75 * pose.mouthFrown) / mouthGroup.scale.y, 1)
  }

  // =========================================================================
  // Secondary motion (springs). Substep happens inside Spring.update.
  // =========================================================================

  let motionInitialised = false

  function updateSecondaryMotion(pose: Pose, dt: number): void {
    // Head lag: the head chases the neck target angles a beat behind, so quick
    // rolls/pitches/sways make the head overshoot. Targets are the pose's own
    // head angles; the spring value is the *actual* applied angle.
    const targetX = pose.headDown - pose.headLift
    const targetY = pose.headTurn
    const targetZ = pose.headTilt

    const ax = headLagX.update(targetX, dt)
    const ay = headLagY.update(targetY, dt)
    const az = headLagZ.update(targetZ, dt)
    neck.rotation.x = ax
    neck.rotation.y = ay
    neck.rotation.z = az

    // Ear bounce: kicked by head angular velocity (headLagX/Y velocity).
    for (const ear of ears) {
      const kick = headLagX.velocity * 0.05 + headLagY.velocity * ear.side * 0.05
      // drive bounce toward 0 but inject velocity via a shifting target
      const b = ear.bounce.update(kick * 0.02, dt)
      ear.pivot.rotation.x = b * 0.4
      ear.pivot.rotation.z = ear.side * (0.05 + b * 0.5)
    }

    // Arm/elbow lag: each side chases the pose's symmetric base with a spring so
    // arms trail the torso. Per-side asymmetry lives in the sampled pose; here we
    // only add trailing.
    for (const arm of arms) {
      const raise = arm.raiseLag.update(pose.armRaise, dt)
      const spread = arm.spreadLag.update(pose.armSpread, dt)
      const bend = arm.elbowLag.update(pose.elbowBend, dt)
      arm.shoulder.rotation.x = raise
      arm.shoulder.rotation.z = arm.side * spread
      arm.elbow.rotation.x = bend
    }

    // Leg lag
    for (const leg of legs) {
      const flex = leg.flexLag.update(pose.hipFlex, dt)
      leg.hip.rotation.x = -flex // + hipFlex = knee toward chest (forward)
      leg.hip.rotation.z = leg.side * pose.legSpread
      leg.knee.rotation.x = -pose.kneeBend // shin folds back under the thigh
    }

    // Tie: gravity pulls the blade down toward local -Y; the tie-pivot's world
    // motion (from body/spine motion) throws it. We estimate world velocity from
    // the tiePivot world position, map it into the spine-local frame via the
    // inverse world quaternion, and feed it as a target offset. Clamp so the
    // blade cannot swing back into the belly.
    tiePivot.getWorldPosition(tieWorldNow)
    if (tieHasPrev && dt > 1e-5) {
      tieWorldVel.subVectors(tieWorldNow, tieWorldPrev).multiplyScalar(1 / dt)
    } else {
      tieWorldVel.set(0, 0, 0)
    }
    tieWorldPrev.copy(tieWorldNow)
    tieHasPrev = true

    // Map world velocity into the spine-local frame (rotate by inverse world q).
    spine.getWorldQuaternion(_tieQuat).invert()
    const localVel = _tieLocalVel.copy(tieWorldVel).applyQuaternion(_tieQuat)

    // Targets: gravity keeps the tie roughly hanging (rotation.x small +ve =
    // forward off the chest); lateral velocity sways it in Z, forward/back in X.
    // Lower bound on X keeps the blade off the belly.
    const swingXTarget = clamp(0.12 - localVel.z * 0.12, -0.05, 0.6)
    const swingZTarget = clamp(-localVel.x * 0.14, -0.5, 0.5)
    const sx = tieSwingX.update(swingXTarget, dt)
    const sz = tieSwingZ.update(swingZTarget, dt)
    tiePivot.rotation.x = sx
    tiePivot.rotation.z = sz

    // Squash spring: driven toward 0; kickSquash injects downward velocity.
    const extra = squash.update(0, dt)
    // apply on top of pose body scale (world-vertical squash)
    body.scale.y *= 1 + extra
    body.scale.x *= 1 - extra * 0.5
    body.scale.z *= 1 - extra * 0.5

    // Head floor avoidance when lying: if pitch is near flat and the head world
    // Y dips below a small clearance, tuck the chin (reduce neck.rotation.x) so
    // the face does not clip through the carpet.
    if (pose.pitch < -0.6) {
      head.getWorldPosition(scratchVec)
      const clearance = 0.24
      if (scratchVec.y < clearance) {
        const lift = (clearance - scratchVec.y) * 1.2
        neck.rotation.x -= lift
      }
    }

    motionInitialised = true
  }

  // tie scratch (module-local, no per-frame alloc)
  const _tieLocalVel = new THREE.Vector3()
  const _tieQuat = new THREE.Quaternion()

  function clamp(x: number, lo: number, hi: number): number {
    return x < lo ? lo : x > hi ? hi : x
  }

  // =========================================================================
  // Gross pose mapping
  // =========================================================================

  let currentSlide = PANDA_DIMS.SIT_SLIDE
  let currentHeight = PANDA_DIMS.SIT_Y

  function applyGross(pose: Pose): void {
    // pitch / roll
    pitchGroup.rotation.x = pose.pitch
    rollGroup.rotation.y = pose.roll

    // lieWeight: 0 sitting, 1 flat on back
    const lieWeight = clamp01(-pose.pitch / HALF_PI)

    // no-slip slide: sitting keeps SIT_SLIDE; lying moves along +local x with roll
    const rollSlide = PANDA_DIMS.BODY_ROLL_RADIUS * pose.roll
    currentSlide = lerp(PANDA_DIMS.SIT_SLIDE, rollSlide, lieWeight)

    // height: sitting SIT_Y; lying LIE_Y + bob
    const sinR = Math.sin(pose.roll)
    const lyingHeight = PANDA_DIMS.LIE_Y + PANDA_DIMS.LIE_BOB * sinR * sinR
    currentHeight = lerp(PANDA_DIMS.SIT_Y, lyingHeight, lieWeight)

    body.position.x = currentSlide
    body.position.y = currentHeight

    // spine hunch + sway
    spine.rotation.x = pose.hunch
    spine.rotation.z = pose.sway

    // body world-vertical squash (XZ counter-scale). Secondary motion multiplies
    // this by the squash spring afterwards.
    body.scale.set(pose.bodyScaleXZ, pose.bodyScaleY, pose.bodyScaleXZ)
  }

  // =========================================================================
  // applyPose
  // =========================================================================

  const _eyeWorld = new THREE.Vector3()

  function applyPose(pose: Pose, clock: number, dt: number): void {
    void clock
    // 1. gross channels
    applyGross(pose)
    // 2-4. face + secondary motion (arms/legs/head/tie/ears/squash) via springs
    updateFace(pose)
    updateSecondaryMotion(pose, dt)

    // 5. update eye velocity estimates for tear fling (world space)
    for (const eye of eyes) {
      eye.tearAnchor.getWorldPosition(_eyeWorld)
      if (motionInitialised && dt > 1e-5) {
        eye.velocity.subVectors(_eyeWorld, eye.prevWorld).multiplyScalar(1 / dt)
      } else {
        eye.velocity.set(0, 0, 0)
      }
      eye.prevWorld.copy(_eyeWorld)
    }
  }

  function settle(pose: Pose): void {
    // snap every spring to the pose target so nothing flies in.
    applyGross(pose)

    headLagX.reset(pose.headDown - pose.headLift)
    headLagY.reset(pose.headTurn)
    headLagZ.reset(pose.headTilt)
    neck.rotation.set(pose.headDown - pose.headLift, pose.headTurn, pose.headTilt)

    for (const ear of ears) {
      ear.bounce.reset(0)
      ear.pivot.rotation.set(0, 0, ear.side * 0.05)
    }
    for (const arm of arms) {
      arm.raiseLag.reset(pose.armRaise)
      arm.spreadLag.reset(pose.armSpread)
      arm.elbowLag.reset(pose.elbowBend)
      arm.shoulder.rotation.set(pose.armRaise, 0, arm.side * pose.armSpread)
      arm.elbow.rotation.set(pose.elbowBend, 0, 0)
    }
    for (const leg of legs) {
      leg.flexLag.reset(pose.hipFlex)
      leg.hip.rotation.set(-pose.hipFlex, 0, leg.side * pose.legSpread)
      leg.knee.rotation.set(-pose.kneeBend, 0, 0)
    }
    tieSwingX.reset(0.12)
    tieSwingZ.reset(0)
    tiePivot.rotation.set(0.12, 0, 0)
    squash.reset(0)

    updateFace(pose)

    // prime eye world positions so the first velocity estimate is zero
    root.updateWorldMatrix(true, true)
    for (const eye of eyes) {
      eye.tearAnchor.getWorldPosition(eye.prevWorld)
      eye.velocity.set(0, 0, 0)
    }
    tieHasPrev = false
    motionInitialised = true
  }

  function kickSquash(strength: number): void {
    // inject downward velocity so the body squashes then springs back.
    squash.velocity -= strength * 2.2
  }

  function getBodyWorldPosition(out: THREE.Vector3): THREE.Vector3 {
    return body.getWorldPosition(out)
  }

  function getEyeVelocity(side: 1 | -1, out: THREE.Vector3): THREE.Vector3 {
    const eye = eyes.find((e) => e.side === side) ?? eyes[0]
    return out.copy(eye.velocity)
  }

  function getHeadWorldQuaternion(out: THREE.Quaternion): THREE.Quaternion {
    return head.getWorldQuaternion(out)
  }

  function dispose(): void {
    furTexture.dispose()
    clothTexture.dispose()
    for (const g of ownedGeometries) g.dispose()
    for (const m of ownedMaterials) m.dispose()
  }

  const rig: PandaRig = {
    root,
    slide: currentSlide,
    height: currentHeight,
    leftTearAnchor: eyes[0].tearAnchor,
    rightTearAnchor: eyes[1].tearAnchor,
    applyPose(pose: Pose, clock: number, dt: number) {
      applyPose(pose, clock, dt)
      rig.slide = currentSlide
      rig.height = currentHeight
    },
    settle(pose: Pose) {
      settle(pose)
      rig.slide = currentSlide
      rig.height = currentHeight
    },
    kickSquash,
    getBodyWorldPosition,
    getEyeVelocity,
    getHeadWorldQuaternion,
    dispose,
  }
  return rig
}
