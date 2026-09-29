/**
 * The "forge" backdrop: one procedural three.js scene rendered behind every page.
 *
 * Layers, back to front:
 * 1. A full-screen shader quad with a domain-warped noise gradient (the Stripe-style flowing
 *    gradient) and a warm glow that follows the gem.
 * 2. A faceted gem with a fresnel rim, a molten seam and a wireframe shell, tilting towards the
 *    pointer with damped inertia. A back-side halo sphere softens its silhouette.
 * 3. Two thin orbit rings, one carrying a small spark.
 * 4. Additive ember particles drifting upwards.
 *
 * Everything is procedural: no textures, models, fonts, workers or remote fetches, so the build's
 * Content-Security-Policy (default-src 'self') needs no changes.
 *
 * Runs only in a browser with WebGL; jsdom can't create a WebGL context, so this module is
 * excluded from unit-test coverage and loaded lazily by ForgeBackdrop.
 */
import {
  AdditiveBlending,
  BackSide,
  BufferAttribute,
  BufferGeometry,
  EdgesGeometry,
  Group,
  IcosahedronGeometry,
  LineBasicMaterial,
  LineSegments,
  MathUtils,
  Mesh,
  MeshBasicMaterial,
  PerspectiveCamera,
  PlaneGeometry,
  Points,
  Scene,
  ShaderMaterial,
  SphereGeometry,
  TorusGeometry,
  Vector2,
  Vector3,
  WebGLRenderer,
} from 'three'

export type SceneMode = 'auth' | 'app'

export interface ForgeScene {
  setMode(mode: SceneMode): void
  dispose(): void
}

interface ForgeSceneOptions {
  /** False when the user prefers reduced motion: render still frames only. */
  animate: boolean
  mode: SceneMode
}

const CAMERA_Z = 7
const CAMERA_FOV = 35
const MAX_PIXEL_RATIO = 1.75
// Frame time that triggers the one-step quality drop (roughly below 45 fps).
const SLOW_FRAME_SECONDS = 1 / 45
const STILL_FRAME_TIME = 14

// 2D simplex noise by Ian McEwan and Stefan Gustavson (MIT), used by the background gradient.
const SIMPLEX_NOISE = /* glsl */ `
  vec3 permute(vec3 x) { return mod(((x * 34.0) + 1.0) * x, 289.0); }
  float snoise(vec2 v) {
    const vec4 C = vec4(0.211324865405187, 0.366025403784439, -0.577350269189626, 0.024390243902439);
    vec2 i = floor(v + dot(v, C.yy));
    vec2 x0 = v - i + dot(i, C.xx);
    vec2 i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
    vec4 x12 = x0.xyxy + C.xxzz;
    x12.xy -= i1;
    i = mod(i, 289.0);
    vec3 p = permute(permute(i.y + vec3(0.0, i1.y, 1.0)) + i.x + vec3(0.0, i1.x, 1.0));
    vec3 m = max(0.5 - vec3(dot(x0, x0), dot(x12.xy, x12.xy), dot(x12.zw, x12.zw)), 0.0);
    m = m * m;
    m = m * m;
    vec3 x = 2.0 * fract(p * C.www) - 1.0;
    vec3 h = abs(x) - 0.5;
    vec3 ox = floor(x + 0.5);
    vec3 a0 = x - ox;
    m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);
    vec3 g;
    g.x = a0.x * x0.x + h.x * x0.y;
    g.yz = a0.yz * x12.xz + h.yz * x12.yw;
    return 130.0 * dot(m, g);
  }
  float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
      value += amplitude * snoise(p);
      p *= 2.02;
      amplitude *= 0.5;
    }
    return value;
  }
`

const BACKGROUND_VERTEX = /* glsl */ `
  varying vec2 vUv;
  void main() {
    vUv = uv;
    gl_Position = vec4(position.xy, 0.0, 1.0);
  }
`

const BACKGROUND_FRAGMENT = /* glsl */ `
  uniform float uTime;
  uniform float uAspect;
  uniform float uHeat;
  uniform vec2 uFocus;
  uniform vec2 uPointer;
  varying vec2 vUv;
  ${SIMPLEX_NOISE}
  float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }

  void main() {
    vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0);
    float t = uTime * 0.035;
    vec2 warp = vec2(fbm(p * 1.1 + vec2(t, -t)), fbm(p * 1.1 + vec2(-t, t) + 4.7));
    float n = fbm(p * 1.4 + warp * 1.3 + uPointer * 0.08) * 0.5 + 0.5;

    vec3 ink = vec3(0.022, 0.020, 0.040);
    vec3 dusk = vec3(0.085, 0.060, 0.210);
    vec3 violet = vec3(0.300, 0.200, 0.720);
    vec3 ember = vec3(1.000, 0.420, 0.140);

    vec3 color = mix(ink, dusk, smoothstep(0.25, 0.85, n));
    color = mix(color, violet, smoothstep(0.62, 1.0, n) * 0.30);

    // Thin silk bands drifting through the gradient.
    float band = sin((p.x * 1.6 + p.y * 0.8 + warp.x * 2.0) * 3.0 + uTime * 0.15);
    color += violet * smoothstep(0.93, 1.0, band) * 0.07;

    // Forge glow behind the gem.
    float d = length(p - uFocus);
    color += ember * exp(-d * d * 3.2) * (0.20 + 0.18 * n) * uHeat;
    color += vec3(1.0, 0.75, 0.45) * exp(-d * d * 18.0) * 0.10 * uHeat;

    vec2 v = vUv - 0.5;
    color *= 1.0 - dot(v, v) * 1.1;
    // Film grain hides gradient banding on 8-bit displays.
    color += (hash(gl_FragCoord.xy + fract(uTime) * 91.0) - 0.5) * 0.018;
    gl_FragColor = vec4(color, 1.0);
  }
`

const GEM_VERTEX = /* glsl */ `
  varying vec3 vViewPosition;
  varying vec3 vLocalPosition;
  void main() {
    vec4 mvPosition = modelViewMatrix * vec4(position, 1.0);
    vViewPosition = mvPosition.xyz;
    vLocalPosition = position;
    gl_Position = projectionMatrix * mvPosition;
  }
`

const GEM_FRAGMENT = /* glsl */ `
  uniform float uTime;
  uniform float uHeat;
  varying vec3 vViewPosition;
  varying vec3 vLocalPosition;
  void main() {
    // Flat facet normals from screen-space derivatives.
    vec3 normal = normalize(cross(dFdx(vViewPosition), dFdy(vViewPosition)));
    vec3 viewDir = normalize(-vViewPosition);
    float fresnel = pow(1.0 - clamp(dot(normal, viewDir), 0.0, 1.0), 2.2);
    float key = clamp(dot(normal, normalize(vec3(-0.5, 0.7, 0.6))), 0.0, 1.0);
    float rim = clamp(dot(normal, normalize(vec3(0.8, -0.3, 0.4))), 0.0, 1.0);

    vec3 color = vec3(0.050, 0.035, 0.090);
    color += vec3(0.36, 0.28, 0.85) * pow(key, 3.0) * 0.55;
    color += vec3(1.00, 0.45, 0.16) * pow(rim, 2.0) * 0.60 * uHeat;

    float seam = sin(vLocalPosition.y * 5.0 + vLocalPosition.x * 2.0 - uTime * 0.9);
    color += vec3(1.0, 0.55, 0.2) * smoothstep(0.96, 1.0, seam) * 0.5 * uHeat;
    color += mix(vec3(0.45, 0.35, 1.0), vec3(1.0, 0.6, 0.3), 0.5 + 0.5 * normal.y) * fresnel * 0.9;
    gl_FragColor = vec4(color, 1.0);
  }
`

// Back-side sphere halo, the same trick GitHub's globe uses to soften the silhouette edge.
const HALO_VERTEX = /* glsl */ `
  varying vec3 vNormal;
  void main() {
    vNormal = normalize(normalMatrix * normal);
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }
`

const HALO_FRAGMENT = /* glsl */ `
  uniform float uHeat;
  varying vec3 vNormal;
  void main() {
    // Back faces point away from the camera: strongest behind the gem, fading to the rim.
    float facing = max(-dot(vNormal, vec3(0.0, 0.0, 1.0)), 0.0);
    float intensity = pow(facing, 4.0);
    gl_FragColor = vec4(vec3(1.0, 0.45, 0.18) * intensity * 0.55 * uHeat, 1.0);
  }
`

const EMBER_VERTEX = /* glsl */ `
  uniform float uTime;
  uniform float uPixelRatio;
  attribute float aSeed;
  varying float vAlpha;
  varying float vSeed;
  void main() {
    vec3 p = position;
    p.y = mod(p.y + 6.0 + uTime * (0.15 + aSeed * 0.35), 12.0) - 6.0;
    p.x += sin(uTime * 0.4 + aSeed * 40.0) * 0.35;
    p.z += cos(uTime * 0.3 + aSeed * 23.0) * 0.2;
    vec4 mvPosition = modelViewMatrix * vec4(p, 1.0);
    gl_Position = projectionMatrix * mvPosition;
    gl_PointSize = mix(2.0, 6.0, aSeed * aSeed) * uPixelRatio * (6.0 / -mvPosition.z);
    float life = smoothstep(-6.0, -3.5, p.y) * (1.0 - smoothstep(3.0, 6.0, p.y));
    vAlpha = life * (0.6 + 0.4 * sin(uTime * (1.5 + aSeed * 3.0) + aSeed * 50.0));
    vSeed = aSeed;
  }
`

const EMBER_FRAGMENT = /* glsl */ `
  varying float vAlpha;
  varying float vSeed;
  void main() {
    float d = length(gl_PointCoord - 0.5);
    float a = smoothstep(0.5, 0.0, d);
    vec3 color = mix(vec3(1.0, 0.45, 0.15), vec3(1.0, 0.82, 0.55), vSeed);
    gl_FragColor = vec4(color, a * a * vAlpha);
  }
`

interface Layout {
  x: number
  y: number
  scale: number
  heat: number
}

function emberGeometry(count: number): BufferGeometry {
  const positions = new Float32Array(count * 3)
  const seeds = new Float32Array(count)
  for (let i = 0; i < count; i++) {
    positions[i * 3] = MathUtils.randFloatSpread(18)
    positions[i * 3 + 1] = MathUtils.randFloatSpread(12)
    positions[i * 3 + 2] = MathUtils.randFloat(-6, 2)
    seeds[i] = Math.random()
  }
  const geometry = new BufferGeometry()
  geometry.setAttribute('position', new BufferAttribute(positions, 3))
  geometry.setAttribute('aSeed', new BufferAttribute(seeds, 1))
  return geometry
}

function ring(radius: number, color: number, opacity: number): Mesh {
  return new Mesh(
    new TorusGeometry(radius, 0.006, 8, 220),
    new MeshBasicMaterial({
      color,
      transparent: true,
      opacity,
      blending: AdditiveBlending,
      depthWrite: false,
    }),
  )
}

export function createForgeScene(
  canvas: HTMLCanvasElement,
  options: ForgeSceneOptions,
): ForgeScene {
  // Throws when WebGL is unavailable; the caller keeps its CSS fallback in that case.
  const renderer = new WebGLRenderer({
    canvas,
    antialias: true,
    powerPreference: 'high-performance',
  })
  let pixelRatio = Math.min(window.devicePixelRatio, MAX_PIXEL_RATIO)
  renderer.setPixelRatio(pixelRatio)
  renderer.setClearColor(0x060510)

  const scene = new Scene()
  const camera = new PerspectiveCamera(CAMERA_FOV, 1, 0.1, 50)
  camera.position.set(0, 0, CAMERA_Z)

  const time = { value: 0 }
  const heat = { value: 0 }
  const pixelRatioUniform = { value: pixelRatio }

  const backgroundUniforms = {
    uTime: time,
    uHeat: heat,
    uAspect: { value: 1 },
    uFocus: { value: new Vector2() },
    uPointer: { value: new Vector2() },
  }
  const background = new Mesh(
    new PlaneGeometry(2, 2),
    new ShaderMaterial({
      uniforms: backgroundUniforms,
      vertexShader: BACKGROUND_VERTEX,
      fragmentShader: BACKGROUND_FRAGMENT,
      depthTest: false,
      depthWrite: false,
    }),
  )
  background.frustumCulled = false
  background.renderOrder = -1
  scene.add(background)

  // `rig` carries layout (position/scale); `tilt` follows the pointer; `spin` rotates freely.
  const rig = new Group()
  const tilt = new Group()
  const spin = new Group()
  rig.add(tilt)
  tilt.add(spin)
  scene.add(rig)

  const gem = new Mesh(
    new IcosahedronGeometry(1, 1),
    new ShaderMaterial({
      uniforms: { uTime: time, uHeat: heat },
      vertexShader: GEM_VERTEX,
      fragmentShader: GEM_FRAGMENT,
    }),
  )
  const shellSource = new IcosahedronGeometry(1.45, 1)
  const shellEdges = new EdgesGeometry(shellSource)
  shellSource.dispose()
  const shell = new LineSegments(
    shellEdges,
    new LineBasicMaterial({
      color: 0xffa25c,
      transparent: true,
      opacity: 0.16,
      blending: AdditiveBlending,
      depthWrite: false,
    }),
  )
  spin.add(gem, shell)

  const halo = new Mesh(
    new SphereGeometry(1, 48, 48),
    new ShaderMaterial({
      uniforms: { uHeat: heat },
      vertexShader: HALO_VERTEX,
      fragmentShader: HALO_FRAGMENT,
      side: BackSide,
      transparent: true,
      blending: AdditiveBlending,
      depthWrite: false,
    }),
  )
  halo.scale.setScalar(1.9)
  tilt.add(halo)

  const innerRing = ring(2.25, 0x8f7bff, 0.35)
  innerRing.rotation.set(Math.PI * 0.42, Math.PI * 0.08, 0)
  const outerRing = ring(2.85, 0xffa25c, 0.16)
  outerRing.rotation.set(Math.PI * 0.55, -Math.PI * 0.12, 0)
  const spark = new Mesh(
    new SphereGeometry(0.035, 12, 12),
    new MeshBasicMaterial({ color: 0xffd29a }),
  )
  innerRing.add(spark)
  tilt.add(innerRing, outerRing)

  const embers = new Points(
    emberGeometry(window.innerWidth < 720 ? 260 : 620),
    new ShaderMaterial({
      uniforms: { uTime: time, uPixelRatio: pixelRatioUniform },
      vertexShader: EMBER_VERTEX,
      fragmentShader: EMBER_FRAGMENT,
      transparent: true,
      blending: AdditiveBlending,
      depthWrite: false,
    }),
  )
  scene.add(embers)

  let mode = options.mode
  let target: Layout = { x: 0, y: 0, scale: 1, heat: 1 }
  // The intro grows the gem from nothing and warms the glow up.
  const current: Layout = { x: 0, y: 0, scale: 0, heat: 0 }
  const pointerTarget = new Vector2()
  const pointer = new Vector2()
  const focus = new Vector3()

  function layoutFor(nextMode: SceneMode, aspect: number): Layout {
    const halfHeight = Math.tan(MathUtils.degToRad(CAMERA_FOV / 2)) * CAMERA_Z
    const halfWidth = halfHeight * aspect
    if (aspect < 1.1) {
      // Narrow screens: the gem sits above the content, smaller and dimmer.
      return { x: 0, y: halfHeight * 0.55, scale: 0.42, heat: 0.65 }
    }
    // Auth: upper part of the left column, clear of the hero copy below it.
    return nextMode === 'auth'
      ? { x: -halfWidth * 0.5, y: halfHeight * 0.34, scale: 0.55, heat: 1 }
      : { x: halfWidth * 0.62, y: -halfHeight * 0.18, scale: 0.75, heat: 0.55 }
  }

  function resize() {
    const width = window.innerWidth
    const height = window.innerHeight
    renderer.setSize(width, height, false)
    camera.aspect = width / height
    camera.updateProjectionMatrix()
    backgroundUniforms.uAspect.value = camera.aspect
    target = layoutFor(mode, camera.aspect)
  }

  function place(layout: Layout) {
    rig.position.set(layout.x, layout.y, 0)
    rig.scale.setScalar(layout.scale)
    heat.value = layout.heat
    // Project the gem's centre into the background's aspect-corrected space for the glow.
    focus.set(layout.x, layout.y, 0).project(camera)
    backgroundUniforms.uFocus.value.set((focus.x * camera.aspect) / 2, focus.y / 2)
  }

  function render() {
    renderer.render(scene, camera)
  }

  function step(delta: number) {
    time.value += delta
    const t = time.value
    for (const key of ['x', 'y', 'scale', 'heat'] as const) {
      current[key] = MathUtils.damp(current[key], target[key], 2.4, delta)
    }
    pointer.x = MathUtils.damp(pointer.x, pointerTarget.x, 3, delta)
    pointer.y = MathUtils.damp(pointer.y, pointerTarget.y, 3, delta)

    place(current)
    rig.position.y += Math.sin(t * 0.8) * 0.06
    tilt.rotation.x = -pointer.y * 0.35
    tilt.rotation.y = pointer.x * 0.45
    spin.rotation.y = t * 0.18
    spin.rotation.x = t * 0.07
    shell.rotation.y = -t * 0.1
    innerRing.rotation.z = t * 0.25
    outerRing.rotation.z = -t * 0.12
    spark.position.set(Math.cos(t * 0.9) * 2.25, Math.sin(t * 0.9) * 2.25, 0)
    camera.position.x = pointer.x * 0.3
    camera.position.y = pointer.y * 0.18
    camera.lookAt(0, 0, 0)
    backgroundUniforms.uPointer.value.copy(pointer)
  }

  function renderStill() {
    // Reduced motion: jump straight to the resting layout and draw a single frame.
    Object.assign(current, target)
    time.value = STILL_FRAME_TIME
    step(0)
    render()
  }

  let frame = 0
  let last = 0
  let slowFrames = 0
  let sampledFrames = 0

  function tick(now: number) {
    frame = requestAnimationFrame(tick)
    const delta = last === 0 ? 1 / 60 : Math.min((now - last) / 1000, 0.1)
    last = now
    // Graceful degradation: after 90 sampled frames, drop to 1x pixels if most were slow.
    if (sampledFrames < 90) {
      sampledFrames += 1
      if (delta > SLOW_FRAME_SECONDS) slowFrames += 1
      if (sampledFrames === 90 && slowFrames > 45 && pixelRatio > 1) {
        pixelRatio = 1
        pixelRatioUniform.value = 1
        renderer.setPixelRatio(1)
        resize()
      }
    }
    step(delta)
    render()
  }

  function start() {
    if (frame === 0) {
      last = 0
      frame = requestAnimationFrame(tick)
    }
  }

  function stop() {
    cancelAnimationFrame(frame)
    frame = 0
  }

  function onResize() {
    resize()
    if (!options.animate) renderStill()
  }

  function onPointerMove(event: PointerEvent) {
    pointerTarget.set(
      (event.clientX / window.innerWidth) * 2 - 1,
      -((event.clientY / window.innerHeight) * 2 - 1),
    )
  }

  function onVisibilityChange() {
    if (document.hidden) stop()
    else start()
  }

  function onContextLost(event: Event) {
    event.preventDefault()
    stop()
  }

  resize()
  window.addEventListener('resize', onResize)
  canvas.addEventListener('webglcontextlost', onContextLost)
  if (options.animate) {
    window.addEventListener('pointermove', onPointerMove, { passive: true })
    document.addEventListener('visibilitychange', onVisibilityChange)
    start()
  } else {
    renderStill()
  }

  return {
    setMode(nextMode) {
      mode = nextMode
      target = layoutFor(mode, camera.aspect)
      if (!options.animate) renderStill()
    },
    dispose() {
      stop()
      window.removeEventListener('resize', onResize)
      window.removeEventListener('pointermove', onPointerMove)
      document.removeEventListener('visibilitychange', onVisibilityChange)
      canvas.removeEventListener('webglcontextlost', onContextLost)
      scene.traverse((object) => {
        if (object instanceof Mesh || object instanceof Points || object instanceof LineSegments) {
          object.geometry.dispose()
          const material = object.material as { dispose(): void }
          material.dispose()
        }
      })
      renderer.dispose()
    },
  }
}
