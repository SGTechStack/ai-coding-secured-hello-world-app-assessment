import * as THREE from 'three'
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js'
import { surfaceTexture } from './surfaces'
import { Spring, clamp01, lerp, smoothstep, valueNoise } from './easing'

// ---------------------------------------------------------------------------
// Public API (contract section 7)
// ---------------------------------------------------------------------------

export interface OfficeHandles {
  root: THREE.Group
  papers: THREE.Object3D[]
  crumpled: THREE.Object3D[]
  mug: THREE.Object3D
  chairSwivel: THREE.Object3D
  plantLeaves: THREE.Object3D[]
  monitorScreen: THREE.MeshBasicMaterial
  monitorTexNormal: THREE.CanvasTexture
  monitorTexAlt: THREE.CanvasTexture
  monitorLight: THREE.PointLight
  deskPosition: THREE.Vector3
}

export interface OfficeFrame {
  dt: number
  clock: number
  pandaSlide: number
  pandaHeight: number
  impact: boolean
  punch: boolean
  kickBurst: boolean
  monitorFlash: boolean
  flashProgress: number
}

// ---------------------------------------------------------------------------
// Internal per-prop reactive state. Kept in module-private WeakMaps keyed by
// the Object3D so the public handle arrays stay plain scene objects.
// ---------------------------------------------------------------------------

interface PaperState {
  homeY: number
  worldX: number
  hop: Spring       // vertical hop
  spin: Spring      // rotation.y flick
  primed: boolean   // debounce so a single pass only hops once
}

interface LeafState {
  home: number      // rest rotation.z
  seed: number
  spring: Spring
}

interface OfficeState {
  papers: Map<THREE.Object3D, PaperState>
  crumpled: Map<THREE.Object3D, PaperState>
  leaves: Map<THREE.Object3D, LeafState>
  mugHomeY: number
  mugShake: Spring
  chairVel: number      // angular velocity of the swivel
  chairHome: number     // rest angle
  flashActive: boolean  // whether the alt texture is currently shown
  disposables: { geometries: Set<THREE.BufferGeometry>; materials: Set<THREE.Material> }
}

const STATE = new WeakMap<OfficeHandles, OfficeState>()

// ---------------------------------------------------------------------------
// Shared geometry / material registries so we build the office cheaply and can
// dispose exactly what we created.
// ---------------------------------------------------------------------------

interface Registry {
  geometries: Set<THREE.BufferGeometry>
  materials: Set<THREE.Material>
}

function reg<T extends THREE.BufferGeometry>(r: Registry, g: T): T {
  r.geometries.add(g)
  return g
}

function mat<T extends THREE.Material>(r: Registry, m: T): T {
  r.materials.add(m)
  return m
}

function box(r: Registry, w: number, h: number, d: number, m: THREE.Material): THREE.Mesh {
  const mesh = new THREE.Mesh(reg(r, new RoundedBoxGeometry(w, h, d, 2, Math.min(0.045, w * 0.2, h * 0.2, d * 0.2))), m)
  mesh.castShadow = true
  mesh.receiveShadow = true
  return mesh
}

// ---------------------------------------------------------------------------
// Canvas textures for the monitor screens and the wall poster.
// ---------------------------------------------------------------------------

function makeCanvas(w: number, h: number): { canvas: HTMLCanvasElement; ctx: CanvasRenderingContext2D } {
  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = h
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('2D canvas context unavailable')
  return { canvas, ctx }
}

function texture(canvas: HTMLCanvasElement): THREE.CanvasTexture {
  const tex = new THREE.CanvasTexture(canvas)
  tex.colorSpace = THREE.SRGBColorSpace
  tex.anisotropy = 4
  return tex
}

function drawErrorScreen(ctx: CanvasRenderingContext2D, w: number, h: number, alt: boolean): void {
  // A friendly "blue screen of despair" style crash dialog.
  ctx.fillStyle = alt ? '#7a1520' : '#12457a'
  ctx.fillRect(0, 0, w, h)
  // Sad face
  ctx.fillStyle = '#ffffff'
  ctx.font = `${Math.round(h * 0.22)}px sans-serif`
  ctx.textBaseline = 'top'
  ctx.fillText(alt ? '>_<' : ':(', w * 0.08, h * 0.08)
  // Headline
  ctx.font = `bold ${Math.round(h * 0.075)}px sans-serif`
  ctx.fillText(alt ? 'PRODUCTION IS DOWN' : 'BUILD FAILED', w * 0.08, h * 0.42)
  // Body lines
  ctx.font = `${Math.round(h * 0.05)}px monospace`
  const lines = alt
    ? ['500 INTERNAL SERVER ERROR', 'stack trace: 12,048 lines', 'on-call paged. good luck.']
    : ['Error: 137 tests failed', 'Exit code 1', 'Deployment blocked']
  lines.forEach((line, i) => {
    ctx.fillText(line, w * 0.08, h * (0.56 + i * 0.11))
  })
  // Blinking cursor bar
  ctx.fillStyle = alt ? '#ffd0d0' : '#bfe0ff'
  ctx.fillRect(w * 0.08, h * 0.9, w * 0.5, h * 0.03)
}

function drawPaperLabel(ctx: CanvasRenderingContext2D, w: number, h: number, label: string): void {
  ctx.fillStyle = '#fbfbf7'
  ctx.fillRect(0, 0, w, h)
  // faint printed lines
  ctx.strokeStyle = '#e2e2dc'
  ctx.lineWidth = 2
  for (let y = h * 0.3; y < h; y += h * 0.09) {
    ctx.beginPath()
    ctx.moveTo(w * 0.12, y)
    ctx.lineTo(w * 0.88, y)
    ctx.stroke()
  }
  if (label) {
    ctx.fillStyle = label === 'URGENT' || label === 'PRODUCTION ISSUE' ? '#c02020' : '#222'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    const size = label.length > 8 ? h * 0.13 : h * 0.18
    ctx.font = `bold ${Math.round(size)}px sans-serif`
    ctx.fillText(label, w * 0.5, h * 0.16)
  }
}

// ---------------------------------------------------------------------------
// Builder
// ---------------------------------------------------------------------------

export function createOffice(scene: THREE.Scene): OfficeHandles {
  const r: Registry = { geometries: new Set(), materials: new Set() }
  const root = new THREE.Group()
  root.name = 'office'
  scene.add(root)

  // Shared materials -------------------------------------------------------
  const wallMat = mat(r, new THREE.MeshStandardMaterial({ color: 0xdfe1e4, roughness: 0.98 }))
  const baseboardMat = mat(r, new THREE.MeshStandardMaterial({ color: 0xc4c6c9, roughness: 0.9 }))
  const woodTexture = surfaceTexture('wood')
  const deskTopMat = mat(r, new THREE.MeshStandardMaterial({ color: 0x8a6a4a, map: woodTexture, bumpMap: woodTexture, bumpScale: 0.012, roughness: 0.62 }))
  const deskLegMat = mat(r, new THREE.MeshStandardMaterial({ color: 0x3b3d40, roughness: 0.6, metalness: 0.3 }))
  const darkPlasticMat = mat(r, new THREE.MeshStandardMaterial({ color: 0x24262a, roughness: 0.65 }))
  const metalMat = mat(r, new THREE.MeshStandardMaterial({ color: 0x9a9da0, roughness: 0.4, metalness: 0.6 }))
  const cabinetMat = mat(r, new THREE.MeshStandardMaterial({ color: 0x5b6068, roughness: 0.5, metalness: 0.4 }))
  const paperMatBase = mat(r, new THREE.MeshStandardMaterial({ color: 0xffffff, roughness: 0.95 }))
  const mugMat = mat(r, new THREE.MeshStandardMaterial({ color: 0xd2452f, roughness: 0.5 }))
  const cupMat = mat(r, new THREE.MeshStandardMaterial({ color: 0xf3f2ee, roughness: 0.85 }))
  const potMat = mat(r, new THREE.MeshStandardMaterial({ color: 0xb5643c, roughness: 0.8 }))
  const leafMat = mat(r, new THREE.MeshStandardMaterial({ color: 0x3f8a4a, roughness: 0.7, side: THREE.DoubleSide }))
  const chairMat = mat(r, new THREE.MeshStandardMaterial({ color: 0x2b2d31, roughness: 0.6 }))
  const lampMat = mat(r, new THREE.MeshStandardMaterial({ color: 0x2a2c30, roughness: 0.5, metalness: 0.4 }))

  // ------------------------------------------------------------------ carpet
  const carpetTex = (() => {
    const { canvas, ctx } = makeCanvas(256, 256)
    ctx.fillStyle = '#6f7a70'
    ctx.fillRect(0, 0, 256, 256)
    for (let i = 0; i < 2400; i++) {
      const g = 96 + Math.floor(Math.random() * 40)
      ctx.fillStyle = `rgb(${g - 12},${g},${g - 8})`
      ctx.fillRect(Math.random() * 256, Math.random() * 256, 1.5, 1.5)
    }
    const t = texture(canvas)
    t.wrapS = THREE.RepeatWrapping
    t.wrapT = THREE.RepeatWrapping
    t.repeat.set(6, 6)
    return t
  })()
  const carpetMat = mat(r, new THREE.MeshStandardMaterial({ map: carpetTex, roughness: 1 }))
  const carpet = new THREE.Mesh(reg(r, new THREE.PlaneGeometry(16, 16)), carpetMat)
  carpet.rotation.x = -Math.PI / 2
  carpet.receiveShadow = true
  root.add(carpet)

  // ------------------------------------------------------------------- walls
  const backWall = box(r, 12, 6, 0.2, wallMat)
  backWall.position.set(-1.5, 3, -3.2)
  backWall.castShadow = false
  root.add(backWall)
  const leftWall = box(r, 0.2, 6, 12, wallMat)
  leftWall.position.set(-4, 3, 0.8)
  leftWall.castShadow = false
  root.add(leftWall)
  // baseboards (share geometry between the two walls via separate meshes)
  const baseBack = box(r, 12, 0.22, 0.24, baseboardMat)
  baseBack.position.set(-1.5, 0.11, -3.1)
  root.add(baseBack)
  const baseLeft = new THREE.Mesh(reg(r, new THREE.BoxGeometry(0.24, 0.22, 12)), baseboardMat)
  baseLeft.position.set(-3.9, 0.11, 0.8)
  baseLeft.castShadow = true
  baseLeft.receiveShadow = true
  root.add(baseLeft)

  // -------------------------------------------------------------------- desk
  const deskPosition = new THREE.Vector3(-2.5, 0, -0.5)
  const desk = new THREE.Group()
  desk.position.copy(deskPosition)
  desk.rotation.y = 0.75
  root.add(desk)

  const deskTop = box(r, 2.6, 0.1, 1.2, deskTopMat)
  deskTop.position.set(0, 1.05, 0)
  desk.add(deskTop)
  const legGeo = reg(r, new THREE.BoxGeometry(0.1, 1.05, 0.1))
  for (const lx of [-1.15, 1.15]) {
    for (const lz of [-0.5, 0.5]) {
      const leg = new THREE.Mesh(legGeo, deskLegMat)
      leg.position.set(lx, 0.525, lz)
      leg.castShadow = true
      leg.receiveShadow = true
      desk.add(leg)
    }
  }

  // ----------------------------------------------------------------- monitor
  const monitorTexNormal = (() => {
    const { canvas, ctx } = makeCanvas(320, 200)
    drawErrorScreen(ctx, 320, 200, false)
    return texture(canvas)
  })()
  const monitorTexAlt = (() => {
    const { canvas, ctx } = makeCanvas(320, 200)
    drawErrorScreen(ctx, 320, 200, true)
    return texture(canvas)
  })()
  const monitorScreen = mat(r, new THREE.MeshBasicMaterial({ map: monitorTexNormal, toneMapped: false }))
  const monitorBody = box(r, 1.15, 0.72, 0.06, darkPlasticMat)
  monitorBody.position.set(0, 1.68, -0.35)
  desk.add(monitorBody)
  const screen = new THREE.Mesh(reg(r, new THREE.PlaneGeometry(1.02, 0.62)), monitorScreen)
  screen.position.set(0, 1.68, -0.315)
  desk.add(screen)
  const monitorStand = box(r, 0.12, 0.28, 0.12, darkPlasticMat)
  monitorStand.position.set(0, 1.24, -0.35)
  desk.add(monitorStand)
  const monitorBase = box(r, 0.42, 0.04, 0.28, darkPlasticMat)
  monitorBase.position.set(0, 1.11, -0.35)
  desk.add(monitorBase)

  // ------------------------------------------- displaced keyboard (hanging)
  const keyboard = box(r, 0.9, 0.05, 0.34, darkPlasticMat)
  // hang it off the front-right edge of the desk, tilted down
  keyboard.position.set(0.95, 0.92, 0.62)
  keyboard.rotation.set(-0.5, 0.2, 0.25)
  desk.add(keyboard)

  // ------------------------------------------------- desk lamp + warm light
  const lampBase = new THREE.Mesh(reg(r, new THREE.CylinderGeometry(0.12, 0.15, 0.05, 16)), lampMat)
  lampBase.position.set(-1.0, 1.13, -0.35)
  lampBase.castShadow = true
  desk.add(lampBase)
  const lampArm = new THREE.Mesh(reg(r, new THREE.CylinderGeometry(0.02, 0.02, 0.6, 8)), lampMat)
  lampArm.position.set(-1.0, 1.42, -0.3)
  lampArm.rotation.z = 0.4
  desk.add(lampArm)
  const lampHead = new THREE.Mesh(reg(r, new THREE.ConeGeometry(0.13, 0.18, 16, 1, true)), lampMat)
  lampHead.position.set(-0.85, 1.68, -0.2)
  lampHead.rotation.set(Math.PI * 0.62, 0, 0.3)
  desk.add(lampHead)

  // Monitor / desk warm point light. Office owns its tint on flash.
  const monitorLight = new THREE.PointLight(0xffd9a8, 12, 6, 2)
  monitorLight.position.set(0, 1.7, 0.1)
  monitorLight.castShadow = false
  desk.add(monitorLight)

  // ------------------------------------------------------------ mug + cups
  const mug = new THREE.Group()
  const mugBody = new THREE.Mesh(reg(r, new THREE.CylinderGeometry(0.11, 0.09, 0.2, 20)), mugMat)
  mugBody.castShadow = true
  mugBody.receiveShadow = true
  mug.add(mugBody)
  const mugHandle = new THREE.Mesh(reg(r, new THREE.TorusGeometry(0.07, 0.02, 8, 16)), mugMat)
  mugHandle.position.set(0.12, 0, 0)
  mugHandle.rotation.y = Math.PI / 2
  mug.add(mugHandle)
  mug.position.set(0.55, 1.2, 0.2)
  desk.add(mug)

  // two empty paper cups, one tipped over on the desk
  const cupGeo = reg(r, new THREE.CylinderGeometry(0.08, 0.055, 0.16, 16))
  const cupUpright = new THREE.Mesh(cupGeo, cupMat)
  cupUpright.position.set(0.78, 1.18, -0.15)
  cupUpright.castShadow = true
  cupUpright.receiveShadow = true
  desk.add(cupUpright)
  const cupTipped = new THREE.Mesh(cupGeo, cupMat)
  cupTipped.position.set(0.2, 1.145, 0.35)
  cupTipped.rotation.z = Math.PI / 2
  cupTipped.castShadow = true
  cupTipped.receiveShadow = true
  desk.add(cupTipped)

  // ------------------------------------------------------- office chair
  const chair = new THREE.Group()
  chair.position.set(-2.0, 0, 1.35)
  root.add(chair)
  // fixed base + castors, then a swiveling seat/back group on top
  const chairPost = new THREE.Mesh(reg(r, new THREE.CylinderGeometry(0.05, 0.05, 0.5, 12)), chairMat)
  chairPost.position.set(0, 0.5, 0)
  chairPost.castShadow = true
  chair.add(chairPost)
  const starGeo = reg(r, new THREE.BoxGeometry(0.5, 0.06, 0.1))
  for (let i = 0; i < 5; i++) {
    const spoke = new THREE.Mesh(starGeo, chairMat)
    spoke.position.set(0, 0.08, 0)
    spoke.rotation.y = (i / 5) * Math.PI * 2
    spoke.castShadow = true
    chair.add(spoke)
  }
  const chairSwivel = new THREE.Group()
  chairSwivel.position.set(0, 0.75, 0)
  chair.add(chairSwivel)
  const seat = box(r, 0.55, 0.12, 0.55, chairMat)
  seat.position.set(0, 0, 0)
  chairSwivel.add(seat)
  const backrest = box(r, 0.55, 0.7, 0.12, chairMat)
  backrest.position.set(0, 0.4, -0.24)
  backrest.rotation.x = -0.12
  chairSwivel.add(backrest)

  // ------------------------------------------------------- filing cabinet
  const cabinet = new THREE.Group()
  cabinet.position.set(2.6, 0, -2.7)
  root.add(cabinet)
  const cabinetBody = box(r, 0.9, 1.5, 0.7, cabinetMat)
  cabinetBody.position.set(0, 0.75, 0)
  cabinet.add(cabinetBody)
  const drawerGeo = reg(r, new THREE.BoxGeometry(0.82, 0.4, 0.08))
  const handleGeo = reg(r, new THREE.BoxGeometry(0.28, 0.05, 0.05))
  for (let i = 0; i < 3; i++) {
    // top drawer slightly open
    const open = i === 0 ? 0.28 : 0
    const drawerFace = new THREE.Mesh(drawerGeo, cabinetMat)
    drawerFace.position.set(0, 1.18 - i * 0.46, 0.35 + open)
    drawerFace.castShadow = true
    drawerFace.receiveShadow = true
    cabinet.add(drawerFace)
    const handle = new THREE.Mesh(handleGeo, metalMat)
    handle.position.set(0, 1.18 - i * 0.46, 0.4 + open)
    cabinet.add(handle)
  }

  // -------------------------------------------------------------- plant
  const plant = new THREE.Group()
  plant.position.set(2.6, 1.5, -2.7) // on top of the cabinet
  root.add(plant)
  const pot = new THREE.Mesh(reg(r, new THREE.CylinderGeometry(0.16, 0.12, 0.24, 16)), potMat)
  pot.position.set(0, 0.12, 0)
  pot.castShadow = true
  pot.receiveShadow = true
  plant.add(pot)
  const leafGeo = reg(r, new THREE.SphereGeometry(0.18, 20, 16))
  const plantLeaves: THREE.Object3D[] = []
  const leafCount = 6
  for (let i = 0; i < leafCount; i++) {
    const pivot = new THREE.Group()
    pivot.position.set(0, 0.24, 0)
    pivot.rotation.y = (i / leafCount) * Math.PI * 2
    const leaf = new THREE.Mesh(leafGeo, leafMat)
    leaf.scale.set(0.5, 1.6, 0.28)
    leaf.position.set(0.1, 0.22, 0)
    leaf.rotation.z = -0.5
    leaf.castShadow = true
    pivot.add(leaf)
    plant.add(pivot)
    plantLeaves.push(pivot)
  }

  // -------------------------------------------------- pendant ceiling lamp
  const pendant = new THREE.Group()
  pendant.position.set(-1.0, 4.6, -0.3)
  root.add(pendant)
  const cord = new THREE.Mesh(reg(r, new THREE.CylinderGeometry(0.015, 0.015, 1.2, 6)), lampMat)
  cord.position.set(0, 0.6, 0)
  pendant.add(cord)
  const shade = new THREE.Mesh(reg(r, new THREE.ConeGeometry(0.4, 0.35, 20, 1, true)), lampMat)
  shade.position.set(0, 0, 0)
  pendant.add(shade)

  // ----------------------------------------------------- floor papers
  const paperLabels = ['BUG', 'FAILED BUILD', 'URGENT', 'PRODUCTION ISSUE', '', '']
  const paperGeo = reg(r, new THREE.PlaneGeometry(0.42, 0.55))
  const paperPositions: Array<[number, number, number]> = [
    [-0.6, 0.35, 1.2],
    [0.9, 0.2, 1.4],
    [1.6, 0.6, 0.3],
    [-1.4, 0.15, 1.6],
    [0.3, 0.55, 2.0],
    [-2.0, 0.4, 1.1],
  ]
  const papers: THREE.Object3D[] = []
  for (let i = 0; i < paperLabels.length; i++) {
    const { canvas, ctx } = makeCanvas(160, 208)
    drawPaperLabel(ctx, 160, 208, paperLabels[i])
    const tex = texture(canvas)
    const pm = mat(r, new THREE.MeshStandardMaterial({ map: tex, roughness: 0.95, side: THREE.DoubleSide }))
    const paper = new THREE.Mesh(paperGeo, pm)
    const [px, ry, pz] = paperPositions[i]
    paper.position.set(px, 0.012, pz)
    paper.rotation.set(-Math.PI / 2, 0, ry)
    paper.receiveShadow = true
    root.add(paper)
    papers.push(paper)
  }

  // ----------------------------------------------------- crumpled balls
  const crumpledGeo = reg(r, new THREE.IcosahedronGeometry(0.11, 0))
  const crumpled: THREE.Object3D[] = []
  const crumpledPositions: Array<[number, number]> = [
    [-0.2, 1.9],
    [1.2, 0.9],
    [-1.1, 0.7],
    [0.7, 1.7],
  ]
  for (const [cx, cz] of crumpledPositions) {
    const ball = new THREE.Mesh(crumpledGeo, paperMatBase)
    ball.position.set(cx, 0.11, cz)
    ball.rotation.set(Math.random(), Math.random(), Math.random())
    ball.castShadow = true
    ball.receiveShadow = true
    root.add(ball)
    crumpled.push(ball)
  }

  const handles: OfficeHandles = {
    root,
    papers,
    crumpled,
    mug,
    chairSwivel,
    plantLeaves,
    monitorScreen,
    monitorTexNormal,
    monitorTexAlt,
    monitorLight,
    deskPosition,
  }

  // Reactive state ---------------------------------------------------------
  const state: OfficeState = {
    papers: new Map(),
    crumpled: new Map(),
    leaves: new Map(),
    mugHomeY: mug.position.y,
    mugShake: new Spring(220, 22),
    chairVel: 0,
    chairHome: chairSwivel.rotation.y,
    flashActive: false,
    disposables: { geometries: r.geometries, materials: r.materials },
  }
  // also collect the canvas textures (poster/carpet/papers) for disposal;
  // monitor textures are disposed explicitly in disposeOffice.
  r.materials.forEach(m => {
    const map = (m as THREE.MeshStandardMaterial).map
    if (map && map !== monitorTexNormal && map !== monitorTexAlt) state.disposables.materials.add(m)
  })

  const registerPaper = (map: Map<THREE.Object3D, PaperState>, obj: THREE.Object3D) => {
    map.set(obj, {
      homeY: obj.position.y,
      worldX: obj.position.x,
      hop: new Spring(180, 16),
      spin: new Spring(60, 9),
      primed: true,
    })
  }
  papers.forEach(p => registerPaper(state.papers, p))
  crumpled.forEach(c => registerPaper(state.crumpled, c))
  plantLeaves.forEach((leaf, i) => {
    state.leaves.set(leaf, { home: 0, seed: i * 13.7, spring: new Spring(90, 12) })
  })
  // snap all springs to rest on first evaluation
  state.mugShake.reset(0)
  state.papers.forEach(s => { s.hop.reset(0); s.spin.reset(0) })
  state.crumpled.forEach(s => { s.hop.reset(0); s.spin.reset(0) })
  state.leaves.forEach(s => s.spring.reset(0))

  STATE.set(handles, state)
  return handles
}

// ---------------------------------------------------------------------------
// Per-frame reactions
// ---------------------------------------------------------------------------

function kickPaper(s: PaperState, strength: number): void {
  s.hop.velocity += 3.2 * strength
  s.spin.velocity += (Math.random() * 2 - 1) * 6 * strength
}

export function updateOfficeReactions(office: OfficeHandles, frame: OfficeFrame): void {
  const state = STATE.get(office)
  if (!state) return
  const dt = frame.dt

  // ---- papers & crumpled: hop when the panda rolls past, or on events -----
  const reactPapers = (
    map: Map<THREE.Object3D, PaperState>,
    objs: THREE.Object3D[],
    eventKick: number,
  ) => {
    for (const obj of objs) {
      const s = map.get(obj)
      if (!s) continue
      // proximity: the panda slides along +x near the papers; treat |slide - worldX|.
      const near = Math.abs(frame.pandaSlide - s.worldX)
      const rolling = frame.pandaHeight < 0.7 // lying/rolling, close to floor
      if (rolling && near < 0.55) {
        if (s.primed) {
          const closeness = 1 - near / 0.55
          kickPaper(s, 0.6 + closeness * 0.8)
          s.primed = false
        }
      } else if (near > 0.9) {
        s.primed = true
      }
      if (eventKick > 0) {
        // attenuate an event kick by distance from the panda's current x
        const atten = clamp01(1 - near / 3.5)
        if (atten > 0.05) kickPaper(s, eventKick * atten)
      }
      // integrate springs toward rest (0)
      s.hop.update(0, dt)
      s.spin.update(0, dt)
      obj.position.y = s.homeY + Math.max(0, s.hop.value) * 0.5
    }
  }

  // Determine event kick strengths this frame.
  let paperEventKick = 0
  if (frame.impact) paperEventKick = Math.max(paperEventKick, 1.0)
  if (frame.punch) paperEventKick = Math.max(paperEventKick, 0.5)
  if (frame.kickBurst) paperEventKick = Math.max(paperEventKick, 0.4)

  reactPapers(state.papers, office.papers, paperEventKick)
  reactPapers(state.crumpled, office.crumpled, paperEventKick)
  // apply spin to crumpled balls (rolling look) and flat papers (yaw flick)
  office.papers.forEach(p => {
    const s = state.papers.get(p)
    if (s) p.rotation.y = s.spin.value * 0.4
  })
  office.crumpled.forEach(c => {
    const s = state.crumpled.get(c)
    if (s) {
      c.rotation.x += s.spin.value * dt
      c.rotation.y = s.spin.value * 0.5
    }
  })

  // ---- mug shake on impact ------------------------------------------------
  if (frame.impact) state.mugShake.velocity += 5
  state.mugShake.update(0, dt)
  const jitter = valueNoise(frame.clock * 40, 7) * state.mugShake.value * 0.04
  office.mug.position.y = state.mugHomeY + Math.abs(state.mugShake.value) * 0.03
  office.mug.rotation.z = jitter
  office.mug.rotation.x = valueNoise(frame.clock * 37, 3) * state.mugShake.value * 0.06

  // ---- chair swivel: kicked on impact, friction + weak return home --------
  if (frame.impact) state.chairVel += 3.2
  if (frame.punch) state.chairVel += 0.6
  const chair = office.chairSwivel
  const toHome = state.chairHome - chair.rotation.y
  // weak spring back home so the loop stays stable, plus velocity friction
  state.chairVel += toHome * 0.6 * dt
  state.chairVel *= Math.exp(-1.4 * dt) // friction
  chair.rotation.y += state.chairVel * dt
  // clamp so it never accumulates wildly
  if (chair.rotation.y > Math.PI) chair.rotation.y -= Math.PI * 2
  if (chair.rotation.y < -Math.PI) chair.rotation.y += Math.PI * 2

  // ---- plant leaves: idle wobble + kick when panda rolls near -------------
  const plantWorldX = office.deskPosition.x + 5.1 // cabinet is at x≈2.6; approximate reactive x
  const nearPlant = Math.abs(frame.pandaSlide - plantWorldX)
  state.leaves.forEach((s, leaf) => {
    const idle = valueNoise(frame.clock * 1.6 + s.seed, 5) * 0.12
    if (frame.impact) s.spring.velocity += 2 * clamp01(1 - nearPlant / 4)
    if (frame.pandaHeight < 0.7 && nearPlant < 1.2) s.spring.velocity += 0.6 * dt * 30
    s.spring.update(idle, dt)
    leaf.rotation.z = s.home + s.spring.value
  })

  // ---- monitor flash: swap texture + tint the point light -----------------
  if (frame.monitorFlash && !state.flashActive) {
    office.monitorScreen.map = office.monitorTexAlt
    office.monitorScreen.needsUpdate = true
    state.flashActive = true
  }
  // fade back once the flash tint has decayed
  if (state.flashActive && frame.flashProgress <= 0.02 && !frame.monitorFlash) {
    office.monitorScreen.map = office.monitorTexNormal
    office.monitorScreen.needsUpdate = true
    state.flashActive = false
  }
  // tint the monitor light: warm normally, redder while flashing.
  const flash = clamp01(frame.flashProgress)
  const warm = new THREE.Color(0xffd9a8)
  const alarm = new THREE.Color(0xff5a3c)
  office.monitorLight.color.copy(warm).lerp(alarm, flash)
  office.monitorLight.intensity = lerp(12, 20, flash * smoothstep(0, 1, flash))
}

// ---------------------------------------------------------------------------
// Disposal
// ---------------------------------------------------------------------------

export function disposeOffice(office: OfficeHandles): void {
  const state = STATE.get(office)
  office.monitorTexNormal.dispose()
  office.monitorTexAlt.dispose()
  if (state) {
    state.disposables.geometries.forEach(g => g.dispose())
    state.disposables.materials.forEach(m => {
      const map = (m as THREE.MeshStandardMaterial).map
      if (map) map.dispose()
      m.dispose()
    })
    STATE.delete(office)
  }
  office.root.removeFromParent()
}
