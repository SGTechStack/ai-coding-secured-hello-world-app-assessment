/**
 * tears.ts - reusable tear particle pool for the office-panda-meltdown scene.
 *
 * All live tears live in a single `THREE.InstancedMesh` (one draw call, one
 * shared geometry + material). State (position / velocity / lifetime / scale /
 * opacity) is kept in preallocated `Float32Array`s and instances are recycled
 * from a free list, so `update` performs ZERO allocations after warm-up.
 *
 * Per-instance opacity is not supported by stock materials, so we inject it with
 * `onBeforeCompile`: an instanced float attribute `aTearOpacity` is declared and
 * passed to the fragment stage via a varying at `#include <begin_vertex>`, then
 * multiplied into `diffuseColor.a` right after `#include <alphamap_fragment>`
 * (both chunks exist in three r186; see plan.md sec 6).
 *
 * Behaviour: tears spawn at each eye's world anchor inheriting the eye/head
 * velocity (so head shakes fling them sideways), fall under gravity, and are
 * stretched into a teardrop along their velocity. In `fountain` mode they jet
 * upward + outward at a higher rate (the "WHYYYY" burst). On reaching the floor
 * they flatten into a splat, fade, then recycle. Dead instances are scaled to
 * zero so they never render.
 *
 * Imports easing only (no cross-module coupling); THREE for the mesh itself.
 */
import * as THREE from 'three'

export interface TearEmitter {
  /** World spawn point (an eye's tear anchor). */
  position: THREE.Vector3
  /** World base velocity to inherit (eye/head motion) for lateral fling. */
  velocity: THREE.Vector3
  /** Head orientation, used to bias fountain jets along the current facing. */
  headQuaternion: THREE.Quaternion
  /** Which eye (-1 left, +1 right); biases spawn jitter + jet direction. */
  side: 1 | -1
  /** Tears per second to spawn from this emitter (0 = none). */
  rate: number
  /** 0 = normal weeping .. 1 = fountain burst (upward + outward, faster). */
  fountain: number
}

export interface TearSystem {
  /** Advance the simulation by dt seconds and spawn from the given emitters. */
  update(dt: number, emitters: readonly TearEmitter[]): void
  /** Render one deterministic static frame (reduced-motion): a few hung drops. */
  settleStatic(emitters: readonly TearEmitter[]): void
  /** Remove the InstancedMesh from the scene and free its geometry + material. */
  dispose(): void
}

const GRAVITY = -9.0 // rig-units / s^2 (stylised, snappier than real gravity)
const FLOOR_Y = 0.02 // world y where a tear becomes a splat
const NORMAL_LIFE = 1.4 // seconds a falling tear lives before force-recycle
const SPLAT_LIFE = 0.35 // seconds a floor splat lingers while fading
const BASE_SCALE = 0.05 // base tear radius in rig units
const MAX_STEP = 1 / 120 // physics substep cap for stability at large dt

/**
 * Build the shared teardrop material. A `MeshStandardMaterial` gives the drops
 * soft cartoon shading consistent with the rest of the scene; we patch it for
 * per-instance opacity. `transparent` + `depthWrite=false` avoids sorting halos.
 */
function createTearMaterial(): THREE.MeshStandardMaterial {
  const material = new THREE.MeshStandardMaterial({
    color: 0xbfeaf2,
    roughness: 0.15,
    metalness: 0.0,
    transparent: true,
    depthWrite: false,
  })
  material.onBeforeCompile = (shader) => {
    // Declare the instanced attribute + varying in the vertex program and hand
    // the per-instance opacity through to the fragment stage.
    shader.vertexShader =
      'attribute float aTearOpacity;\nvarying float vTearOpacity;\n' + shader.vertexShader
    shader.vertexShader = shader.vertexShader.replace(
      '#include <begin_vertex>',
      '#include <begin_vertex>\n\tvTearOpacity = aTearOpacity;',
    )
    // Multiply the varying into the diffuse alpha AFTER the (guarded) alphamap
    // chunk, so it applies whether or not USE_ALPHAMAP is defined.
    shader.fragmentShader = 'varying float vTearOpacity;\n' + shader.fragmentShader
    shader.fragmentShader = shader.fragmentShader.replace(
      '#include <alphamap_fragment>',
      '#include <alphamap_fragment>\n\tdiffuseColor.a *= vTearOpacity;',
    )
  }
  return material
}

export function createTearSystem(scene: THREE.Scene, capacity: number = 96): TearSystem {
  const count = Math.max(1, capacity | 0)

  // Shared unit sphere; per-instance non-uniform scale sculpts the teardrop.
  const geometry = new THREE.SphereGeometry(1, 10, 8)
  const material = createTearMaterial()

  const mesh = new THREE.InstancedMesh(geometry, material, count)
  mesh.frustumCulled = false // anchors move; cheap enough to always draw
  mesh.castShadow = false
  mesh.receiveShadow = false
  mesh.instanceMatrix.setUsage(THREE.DynamicDrawUsage)

  // Per-instance opacity attribute (declared in the patched shader above).
  const opacityArray = new Float32Array(count) // starts all-zero (invisible)
  const opacityAttr = new THREE.InstancedBufferAttribute(opacityArray, 1)
  opacityAttr.setUsage(THREE.DynamicDrawUsage)
  geometry.setAttribute('aTearOpacity', opacityAttr)

  // Simulation state in flat typed arrays (position/velocity are 3 per tear).
  const px = new Float32Array(count)
  const py = new Float32Array(count)
  const pz = new Float32Array(count)
  const vx = new Float32Array(count)
  const vy = new Float32Array(count)
  const vz = new Float32Array(count)
  const life = new Float32Array(count) // remaining seconds; <=0 means free
  const maxLife = new Float32Array(count)
  const scale = new Float32Array(count) // base radius for this tear
  const splat = new Uint8Array(count) // 1 once the tear hit the floor

  // Free list: indices of dead instances available for spawning.
  const freeList = new Int32Array(count)
  let freeCount = count
  for (let i = 0; i < count; i++) freeList[i] = i

  // Per-emitter fractional spawn accumulators (reused, never reallocated).
  const spawnAccum = new Float32Array(64)

  // Scratch objects reused every frame (no per-frame allocation).
  const mtx = new THREE.Matrix4()
  const pos = new THREE.Vector3()
  const quat = new THREE.Quaternion()
  const scl = new THREE.Vector3()
  const up = new THREE.Vector3(0, 1, 0)
  const velDir = new THREE.Vector3()
  const jitter = new THREE.Vector3()
  const jetDir = new THREE.Vector3()

  // Deterministic tiny PRNG for spawn jitter (no Math.random surprises in tests).
  let seed = 0x1234abcd
  function rand(): number {
    // xorshift32 -> [0, 1)
    seed ^= seed << 13
    seed ^= seed >>> 17
    seed ^= seed << 5
    return ((seed >>> 0) % 100000) / 100000
  }

  /** Take a free instance index, or -1 if the pool is exhausted. */
  function acquire(): number {
    if (freeCount === 0) return -1
    freeCount -= 1
    return freeList[freeCount]
  }

  /** Return an instance to the free list and hide it. */
  function release(i: number): void {
    life[i] = 0
    splat[i] = 0
    opacityArray[i] = 0
    freeList[freeCount] = i
    freeCount += 1
  }

  /** Spawn one tear at emitter e (falling or fountain depending on e.fountain). */
  function spawn(e: TearEmitter): void {
    const i = acquire()
    if (i < 0) return
    // Small spawn jitter around the eye anchor so drops don't stack perfectly.
    jitter.set((rand() - 0.5) * 0.03, (rand() - 0.5) * 0.02, (rand() - 0.5) * 0.03)
    px[i] = e.position.x + jitter.x
    py[i] = e.position.y + jitter.y
    pz[i] = e.position.z + jitter.z

    // Inherit the eye/head velocity so head shakes fling tears sideways.
    vx[i] = e.velocity.x
    vy[i] = e.velocity.y
    vz[i] = e.velocity.z

    if (e.fountain > 0) {
      // Fountain jet: up + outward along the head facing, scaled by intensity.
      jetDir.copy(up)
      jetDir.x += e.side * 0.35 + (rand() - 0.5) * 0.4
      jetDir.z += 0.15 + (rand() - 0.5) * 0.3
      jetDir.applyQuaternion(e.headQuaternion).normalize()
      const power = 2.2 + e.fountain * 3.8 + rand() * 1.2
      vx[i] += jetDir.x * power
      vy[i] += jetDir.y * power
      vz[i] += jetDir.z * power
    } else {
      // Gentle initial dribble downward + slight outward drift.
      vx[i] += e.side * 0.15 + (rand() - 0.5) * 0.2
      vy[i] += -0.2 - rand() * 0.2
      vz[i] += 0.1 + (rand() - 0.5) * 0.15
    }

    scale[i] = BASE_SCALE * (0.8 + rand() * 0.5)
    maxLife[i] = NORMAL_LIFE * (0.85 + rand() * 0.4)
    life[i] = maxLife[i]
    splat[i] = 0
    opacityArray[i] = 1
  }

  /**
   * Write instance i's transform + opacity into the buffers. `t01` is remaining
   * life fraction in [0,1]; opacity fades as it approaches 0.
   */
  function writeInstance(i: number): void {
    if (life[i] <= 0) {
      // Dead: collapse to zero scale so it never renders.
      scl.set(0, 0, 0)
      pos.set(0, 0, 0)
      quat.identity()
      mtx.compose(pos, quat, scl)
      mesh.setMatrixAt(i, mtx)
      opacityArray[i] = 0
      return
    }
    pos.set(px[i], py[i], pz[i])
    const base = scale[i]
    if (splat[i] === 1) {
      // Floor splat: flat disc, fading over its short remaining life.
      const s01 = clamp01Local(life[i] / SPLAT_LIFE)
      const spread = base * (1.6 + (1 - s01) * 1.2)
      scl.set(spread, base * 0.18, spread)
      quat.identity()
      opacityArray[i] = 0.85 * s01
    } else {
      // Airborne teardrop: stretch along velocity, taper the trailing tip.
      velDir.set(vx[i], vy[i], vz[i])
      const speed = velDir.length()
      const stretch = 1 + Math.min(speed * 0.12, 1.4)
      scl.set(base / Math.sqrt(stretch), base * stretch, base / Math.sqrt(stretch))
      if (speed > 1e-4) {
        velDir.multiplyScalar(1 / speed)
        // Local +Y (long axis) aligns to the direction of travel.
        quat.setFromUnitVectors(up, velDir)
      } else {
        quat.identity()
      }
      const fade = clamp01Local(life[i] / (maxLife[i] * 0.5))
      opacityArray[i] = fade
    }
    mtx.compose(pos, quat, scl)
    mesh.setMatrixAt(i, mtx)
  }

  // Local clamp to avoid an import cycle cost; identical to easing.clamp01.
  function clamp01Local(x: number): number {
    return x < 0 ? 0 : x > 1 ? 1 : x
  }

  /** Integrate all live tears by a single substep of length h seconds. */
  function integrate(h: number): void {
    for (let i = 0; i < count; i++) {
      if (life[i] <= 0) continue
      if (splat[i] === 1) {
        life[i] -= h
        if (life[i] <= 0) release(i)
        continue
      }
      // Symplectic Euler: gravity then position.
      vy[i] += GRAVITY * h
      px[i] += vx[i] * h
      py[i] += vy[i] * h
      pz[i] += vz[i] * h
      life[i] -= h
      if (py[i] <= FLOOR_Y) {
        // Touch down: convert to a splat pinned to the floor.
        py[i] = FLOOR_Y
        vx[i] = 0
        vy[i] = 0
        vz[i] = 0
        splat[i] = 1
        life[i] = SPLAT_LIFE
      } else if (life[i] <= 0) {
        release(i)
      }
    }
  }

  function update(dt: number, emitters: readonly TearEmitter[]): void {
    if (dt > 0) {
      // Physics: substep for stability under large frame gaps.
      let remaining = dt
      while (remaining > 0) {
        const h = remaining > MAX_STEP ? MAX_STEP : remaining
        integrate(h)
        remaining -= h
      }
      // Spawning: accumulate fractional tears per emitter and emit whole ones.
      const n = Math.min(emitters.length, spawnAccum.length)
      for (let e = 0; e < n; e++) {
        const emitter = emitters[e]
        const rate = emitter.fountain > 0 ? emitter.rate * (1 + emitter.fountain * 4) : emitter.rate
        if (rate <= 0) {
          spawnAccum[e] = 0
          continue
        }
        spawnAccum[e] += rate * dt
        // Cap bursts so a huge dt can't drain the whole pool in one frame.
        let budget = 8
        while (spawnAccum[e] >= 1 && budget > 0) {
          spawnAccum[e] -= 1
          spawn(emitter)
          budget -= 1
        }
        if (spawnAccum[e] > 4) spawnAccum[e] = 4
      }
    }
    // Push transforms + opacity to the GPU.
    for (let i = 0; i < count; i++) writeInstance(i)
    mesh.instanceMatrix.needsUpdate = true
    opacityAttr.needsUpdate = true
  }

  function settleStatic(emitters: readonly TearEmitter[]): void {
    // Recycle everything, then hang a couple of static drops under each active
    // emitter so the reduced-motion frame reads as "crying" without animating.
    for (let i = 0; i < count; i++) release(i)
    for (let e = 0; e < emitters.length; e++) {
      const emitter = emitters[e]
      if (emitter.rate <= 0) continue
      for (let d = 0; d < 2; d++) {
        const i = acquire()
        if (i < 0) break
        px[i] = emitter.position.x + emitter.side * 0.01
        py[i] = emitter.position.y - 0.12 - d * 0.16
        pz[i] = emitter.position.z + 0.02
        vx[i] = 0
        vy[i] = -0.6 // gives the static drop a downward teardrop stretch
        vz[i] = 0
        scale[i] = BASE_SCALE
        maxLife[i] = NORMAL_LIFE
        life[i] = NORMAL_LIFE
        splat[i] = 0
        opacityArray[i] = 1 - d * 0.35
      }
    }
    for (let i = 0; i < count; i++) writeInstance(i)
    mesh.instanceMatrix.needsUpdate = true
    opacityAttr.needsUpdate = true
  }

  function dispose(): void {
    if (mesh.parent) mesh.parent.remove(mesh)
    scene.remove(mesh)
    geometry.dispose()
    material.dispose()
    mesh.dispose()
  }

  // Start hidden: every instance collapsed to zero scale, opacity 0.
  for (let i = 0; i < count; i++) {
    scl.set(0, 0, 0)
    pos.set(0, 0, 0)
    quat.identity()
    mtx.compose(pos, quat, scl)
    mesh.setMatrixAt(i, mtx)
  }
  mesh.instanceMatrix.needsUpdate = true
  opacityAttr.needsUpdate = true

  scene.add(mesh)

  return { update, settleStatic, dispose }
}
