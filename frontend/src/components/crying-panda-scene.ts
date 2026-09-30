import * as THREE from 'three'
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js'
import {
  CYCLE,
  EVENTS,
  samplePose,
  sampleCamera,
  SIT,
  clonePose,
  type Pose,
  type PhaseName,
} from './panda-scene/choreography'
import { createPanda } from './panda-scene/panda'
import { createTearSystem, type TearEmitter } from './panda-scene/tears'
import { createOffice, updateOfficeReactions, disposeOffice, type OfficeFrame } from './panda-scene/office'

/**
 * Office panda meltdown — the login-page hero scene.
 *
 * This module is the integrator/orchestrator. It owns the renderer, camera,
 * lighting and lifecycle (pause / prefers-reduced-motion / visibility / resize /
 * pointer parallax / dispose) and the per-frame update pipeline. The rig, the
 * timeline/poses, the tear particle pool and the office props each live in their
 * own module under ./panda-scene and are wired together here.
 *
 * Public API is UNCHANGED: createPandaScene(host) -> { setPaused, dispose }.
 */
export function createPandaScene(host: HTMLElement) {
  // --- Renderer (preserved settings + r186 shadow-map change) ---------------
  const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.75))
  renderer.shadowMap.enabled = true
  // r186 removed PCFSoftShadowMap (it warns); PCFShadowMap + shadow.radius gives
  // the soft edge instead.
  renderer.shadowMap.type = THREE.PCFShadowMap
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.05
  host.appendChild(renderer.domElement)

  const scene = new THREE.Scene()
  // Soft studio reflections give eyes, ceramic and metal a physical response.
  const environmentRoom = new RoomEnvironment()
  const environmentGenerator = new THREE.PMREMGenerator(renderer)
  const environment = environmentGenerator.fromScene(environmentRoom, 0.04)
  scene.environment = environment.texture
  scene.environmentIntensity = 0.3
  environmentRoom.dispose()
  environmentGenerator.dispose()

  // --- Camera ---------------------------------------------------------------
  // Base framing comes from choreography.sampleCamera; the integrator adds
  // follow / push-in / handheld / impact-shake / pointer-parallax on top.
  const camera = new THREE.PerspectiveCamera(38, 1, 0.1, 60)
  camera.position.set(1.6, 2.7, 7.4)
  camera.lookAt(-0.25, 0.8, -0.5)

  // --- Lighting -------------------------------------------------------------
  const hemi = new THREE.HemisphereLight(0xeef3ff, 0x6a6a78, 1.25)
  scene.add(hemi)

  const key = new THREE.DirectionalLight(0xfff2e0, 2.6)
  key.position.set(-4, 7.5, 4.5)
  key.castShadow = true
  key.shadow.mapSize.set(2048, 2048)
  key.shadow.radius = 3.5 // soft edge (replaces PCFSoftShadowMap)
  key.shadow.normalBias = 0.03
  key.shadow.bias = -0.0004
  // Tight shadow camera around the panda and desk.
  const sc = key.shadow.camera
  sc.near = 1
  sc.far = 22
  sc.left = -6
  sc.right = 6
  sc.top = 6
  sc.bottom = -6
  sc.updateProjectionMatrix()
  scene.add(key)
  scene.add(key.target)
  key.target.position.set(-0.4, 0.4, -0.4)
  key.target.updateMatrixWorld()

  const rim = new THREE.DirectionalLight(0xdcecff, 1.7)
  rim.position.set(3, 5, -2)
  scene.add(rim)

  const faceFill = new THREE.DirectionalLight(0xe5efff, 0.65)
  faceFill.position.set(1, 2, 5)
  scene.add(faceFill)

  // Warm fill near the desk (the office owns the monitor-tinted point light).
  const deskFill = new THREE.PointLight(0xffd9a8, 12, 9, 2)
  deskFill.position.set(-2.2, 1.8, 0.2)
  scene.add(deskFill)

  // --- Scene content --------------------------------------------------------
  const office = createOffice(scene)
  const panda = createPanda()
  scene.add(panda.root)
  const tears = createTearSystem(scene)

  // --- Frame-persistent scratch (no per-frame allocation) -------------------
  const poseScratch: Pose = clonePose(SIT)
  const headQuat = new THREE.Quaternion()
  const bodyWorld = new THREE.Vector3()
  const faceWorld = new THREE.Vector3()
  const leftEyeVel = new THREE.Vector3()
  const rightEyeVel = new THREE.Vector3()

  // A fixed pool of two emitters (one per eye), reused every frame.
  const emitterL: TearEmitter = {
    position: new THREE.Vector3(),
    velocity: new THREE.Vector3(),
    headQuaternion: new THREE.Quaternion(),
    side: -1,
    rate: 0,
    fountain: 0,
  }
  const emitterR: TearEmitter = {
    position: new THREE.Vector3(),
    velocity: new THREE.Vector3(),
    headQuaternion: new THREE.Quaternion(),
    side: 1,
    rate: 0,
    fountain: 0,
  }
  const emitters: TearEmitter[] = [emitterL, emitterR]

  // Camera runtime state (smoothed offsets so shake/return are continuous).
  const camPos = new THREE.Vector3()
  const camTarget = new THREE.Vector3()
  const camPosCurrent = camera.position.clone()
  const camTargetCurrent = new THREE.Vector3(-0.25, 0.8, -0.5)
  let shake = 0 // decaying impact-shake amplitude
  let pushInHold = 0 // extra dolly kicked by the WHY event, decays

  // Office flash state owned by the integrator (per contract).
  let flashProgress = 0
  let flashActive = false

  const animationState: { time: number; phase: PhaseName } = { time: 0, phase: 'realisation' }

  let paused = false
  let last = 0
  let prevTc = 0
  let pointer = 0
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)')

  // --- Tear emitter rates per phase -----------------------------------------
  function tearRateForPhase(phase: PhaseName, fountain: number): number {
    if (fountain > 0.01) return 34 // fountain burst rate
    switch (phase) {
      case 'crying':
        return 10
      case 'collapse':
        return 6
      case 'rollingA':
      case 'rollingB':
        return 7
      case 'despair':
        return 8
      case 'exhaustion':
        return 2
      default:
        return 0
    }
  }

  function buildEmitters(phase: PhaseName, tc: number): void {
    panda.getHeadWorldQuaternion(headQuat)
    panda.getEyeVelocity(-1, leftEyeVel)
    panda.getEyeVelocity(1, rightEyeVel)

    // Fountain window centred on the WHY beat (6.95) during despair.
    const fountain = phase === 'despair' ? Math.max(0, 1 - Math.abs(tc - 7.05) / 0.55) : 0
    const rate = tearRateForPhase(phase, fountain)

    emitterL.position.copy(panda.leftTearAnchor.getWorldPosition(emitterL.position))
    emitterL.velocity.copy(leftEyeVel)
    emitterL.headQuaternion.copy(headQuat)
    emitterL.rate = rate
    emitterL.fountain = fountain

    emitterR.position.copy(panda.rightTearAnchor.getWorldPosition(emitterR.position))
    emitterR.velocity.copy(rightEyeVel)
    emitterR.headQuaternion.copy(headQuat)
    emitterR.rate = rate
    emitterR.fountain = fountain
  }

  // --- Event firing (wrap-around edge detection) ----------------------------
  // Returns per-frame flags, and applies squash/camera side effects.
  const frameFlags = { impact: false, punch: false, kickBurst: false, monitorFlash: false }
  function fireEvents(prev: number, curr: number, dt: number): void {
    frameFlags.impact = false
    frameFlags.punch = false
    frameFlags.kickBurst = false
    frameFlags.monitorFlash = false
    if (dt <= 0) return
    const wrapped = curr < prev
    for (const ev of EVENTS) {
      // Fire when the event time is crossed this frame, handling wrap-around.
      const crossed = wrapped ? ev.at > prev || ev.at <= curr : ev.at > prev && ev.at <= curr
      if (!crossed) continue
      switch (ev.name) {
        case 'impact':
          frameFlags.impact = true
          panda.kickSquash(ev.strength)
          shake = Math.max(shake, 0.22 * ev.strength)
          break
        case 'punch':
          frameFlags.punch = true
          shake = Math.max(shake, 0.08 * ev.strength)
          break
        case 'kickBurst':
          frameFlags.kickBurst = true
          break
        case 'why':
          pushInHold = 1
          shake = Math.max(shake, 0.1 * ev.strength)
          break
        case 'monitorFlash':
          frameFlags.monitorFlash = true
          flashActive = true
          flashProgress = 1
          break
      }
    }
  }

  // --- Camera update --------------------------------------------------------
  function updateCamera(tc: number, dt: number): void {
    const shot = sampleCamera(tc)

    camPos.copy(shot.basePos)
    camTarget.copy(shot.baseTarget)

    // Horizontal follow of the panda slide (softened).
    panda.getBodyWorldPosition(bodyWorld)
    const followAmt = shot.followX
    camTarget.x += (bodyWorld.x - camTarget.x) * followAmt * 0.5
    camPos.x += (bodyWorld.x - shot.baseTarget.x) * followAmt * 0.25

    // Push-in: dolly toward the target during WHY (choreography + kicked hold).
    const push = Math.max(shot.pushIn, pushInHold)
    if (push > 0) {
      // Rise and aim at the face so the "WHYYYY" reads over the belly while
      // the panda lies on its back with its head pointing away from camera.
      panda.leftTearAnchor.getWorldPosition(faceWorld)
      camTarget.lerp(faceWorld, push * 0.6)
      camPos.lerp(camTarget, push * 0.28)
      camPos.y += push * 1.3
    }

    // Handheld sway + impact shake driven by ACCUMULATED time so nothing jumps
    // at the loop seam.
    const t = animationState.time
    const handheld = shot.handheld
    if (handheld > 0) {
      camPos.x += Math.sin(t * 5.3) * 0.05 * handheld
      camPos.y += Math.sin(t * 6.7 + 1.3) * 0.04 * handheld
    }
    if (shake > 0.0001) {
      camPos.x += Math.sin(t * 61) * shake
      camPos.y += Math.sin(t * 73 + 2) * shake * 0.8
      camTarget.x += Math.sin(t * 67 + 1) * shake * 0.4
    }

    // Pointer parallax (small).
    camPos.x += pointer * 0.6

    // Smooth return via critically-damped-ish lerp (frame-rate aware).
    const k = dt > 0 ? 1 - Math.pow(0.0015, dt) : 1
    camPosCurrent.lerp(camPos, k)
    camTargetCurrent.lerp(camTarget, k)

    camera.position.copy(camPosCurrent)
    camera.lookAt(camTargetCurrent)
    if (camera.fov !== shot.fov) {
      camera.fov = shot.fov
      camera.updateProjectionMatrix()
    }
  }

  // --- Office flash decay + texture swap ------------------------------------
  function updateOffice(dt: number): void {
    if (flashProgress > 0) flashProgress = Math.max(0, flashProgress - dt * 1.6)
    if (flashActive) {
      office.monitorScreen.map = office.monitorTexAlt
      office.monitorScreen.needsUpdate = true
      if (flashProgress <= 0.02) {
        flashActive = false
        office.monitorScreen.map = office.monitorTexNormal
        office.monitorScreen.needsUpdate = true
      }
    }
    const frame: OfficeFrame = {
      dt,
      clock: animationState.time,
      pandaSlide: panda.slide,
      pandaHeight: panda.height,
      impact: frameFlags.impact,
      punch: frameFlags.punch,
      kickBurst: frameFlags.kickBurst,
      monitorFlash: frameFlags.monitorFlash,
      flashProgress,
    }
    updateOfficeReactions(office, frame)
  }

  // --- Per-frame render -----------------------------------------------------
  function render(now: number) {
    const dt = last ? Math.min((now - last) / 1000, 0.05) : 0
    last = now
    const animate = !paused && !reduced.matches && !document.hidden
    if (animate) animationState.time += dt

    const tc = ((animationState.time % CYCLE) + CYCLE) % CYCLE

    // 1. Pose + rig.
    animationState.phase = samplePose(tc, poseScratch)
    panda.applyPose(poseScratch, animationState.time, dt)

    // 2. Events (skip when dt=0 so a resize re-render does not double-fire).
    fireEvents(prevTc, tc, dt)
    prevTc = tc

    // Push-in hold decays after the WHY event.
    if (pushInHold > 0) pushInHold = Math.max(0, pushInHold - dt * 1.4)
    if (shake > 0) shake = Math.max(0, shake - dt * 1.1)

    // 3. Tears.
    buildEmitters(animationState.phase, tc)
    tears.update(dt, emitters)

    // 4. Office reactions + monitor flash.
    updateOffice(dt)

    // 5. Camera.
    updateCamera(tc, dt)

    renderer.render(scene, camera)
  }

  // --- Reduced-motion static frame ------------------------------------------
  function renderStaticFrame(): void {
    const tc = 2.35
    animationState.phase = samplePose(tc, poseScratch)
    panda.settle(poseScratch)
    // Prime derived state (slide/height/anchors) for the static composition.
    panda.applyPose(poseScratch, tc, 0)
    buildEmitters(animationState.phase, tc)
    tears.settleStatic(emitters)
    // One office update at rest so props sit still.
    updateOfficeReactions(office, {
      dt: 0,
      clock: tc,
      pandaSlide: panda.slide,
      pandaHeight: panda.height,
      impact: false,
      punch: false,
      kickBurst: false,
      monitorFlash: false,
      flashProgress: 0,
    })
    updateCamera(tc, 0)
    // Snap the smoothed camera exactly to target for the still frame.
    camPosCurrent.copy(camPos)
    camTargetCurrent.copy(camTarget)
    camera.position.copy(camPosCurrent)
    camera.lookAt(camTargetCurrent)
    renderer.render(scene, camera)
  }

  function resize() {
    const { width, height } = host.getBoundingClientRect()
    camera.aspect = width / Math.max(height, 1)
    camera.updateProjectionMatrix()
    renderer.setSize(width, height)
    if (paused || reduced.matches || document.hidden) renderStaticFrame()
    else render(performance.now())
  }
  const observer = new ResizeObserver(resize)
  observer.observe(host)

  function syncMotion() {
    last = 0
    const still = paused || reduced.matches || document.hidden
    renderer.setAnimationLoop(still ? null : render)
    if (still) renderStaticFrame()
    else render(performance.now())
  }

  function move(event: PointerEvent) {
    if (paused || reduced.matches) return
    const rect = host.getBoundingClientRect()
    pointer = ((event.clientX - rect.left) / rect.width - 0.5) * 0.28
  }
  function leave() { pointer = 0 }

  host.addEventListener('pointermove', move)
  host.addEventListener('pointerleave', leave)
  reduced.addEventListener('change', syncMotion)
  document.addEventListener('visibilitychange', syncMotion)


  resize()
  syncMotion()

  return {
    setPaused(value: boolean) { paused = value; syncMotion() },
    dispose() {
      renderer.setAnimationLoop(null)
      observer.disconnect()
      host.removeEventListener('pointermove', move)
      host.removeEventListener('pointerleave', leave)
      reduced.removeEventListener('change', syncMotion)
      document.removeEventListener('visibilitychange', syncMotion)

      // Module-owned assets (geometries, materials, CanvasTextures, InstancedMesh).
      tears.dispose()
      disposeOffice(office)
      panda.dispose()
      environment.dispose()

      // Sweep anything left in the scene (lights, stray meshes) defensively.
      const geometries = new Set<THREE.BufferGeometry>()
      const materials = new Set<THREE.Material>()
      scene.traverse(object => {
        if (object instanceof THREE.Mesh) {
          geometries.add(object.geometry)
          const list = Array.isArray(object.material) ? object.material : [object.material]
          list.forEach(material => materials.add(material))
        }
      })
      geometries.forEach(geometry => geometry.dispose())
      materials.forEach(material => material.dispose())

      renderer.dispose()
      renderer.domElement.remove()
    },
  }
}
