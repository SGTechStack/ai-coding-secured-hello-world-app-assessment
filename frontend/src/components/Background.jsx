import { useEffect, useRef } from "react";
import * as THREE from "three";
import { EffectComposer } from "three/examples/jsm/postprocessing/EffectComposer.js";
import { RenderPass } from "three/examples/jsm/postprocessing/RenderPass.js";
import { ShaderPass } from "three/examples/jsm/postprocessing/ShaderPass.js";

/**
 * Abstract Three.js background — a flat, 2D-facing indigo "signal grid": a
 * subdivided plane rippled by traveling sine waves, drawn as a glowing
 * wireframe with a drifting point layer, composited through a cursor-focused
 * chromatic-aberration pass. Fixed behind the app at low opacity so the cards
 * keep full contrast. Decorative, aria-hidden.
 *
 * Performance: the ripple runs entirely in a GPU vertex shader driven by
 * uniforms (uTime/uCursor/uPulse), so the geometry is uploaded ONCE and the
 * per-frame CPU cost is near zero — no geometry rebuild, no vertex loop, no
 * per-frame allocation. The loop is capped to ~40fps and the pixel ratio to
 * 1.5. Respects prefers-reduced-motion (static frame, faint fixed fringe) and
 * pauses when the tab is hidden. Accent hue comes from `--accent-hue`.
 */

const AberrationShader = {
  uniforms: {
    tDiffuse: { value: null },
    amount: { value: 0.0 },
    focus: { value: new THREE.Vector2(0.5, 0.5) },
  },
  vertexShader: /* glsl */ `
    varying vec2 vUv;
    void main() {
      vUv = uv;
      gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
    }
  `,
  fragmentShader: /* glsl */ `
    uniform sampler2D tDiffuse;
    uniform float amount;
    uniform vec2 focus;
    varying vec2 vUv;
    void main() {
      vec2 dir = vUv - focus;
      float dist = length(dir);
      vec2 offset = dir * amount * (0.25 + dist);
      float r = texture2D(tDiffuse, vUv + offset).r;
      float g = texture2D(tDiffuse, vUv).g;
      float b = texture2D(tDiffuse, vUv - offset).b;
      float a = texture2D(tDiffuse, vUv).a;
      gl_FragColor = vec4(r, g, b, a);
    }
  `,
};

// GPU displacement shared by the grid lines and points: the exact ripple that
// used to run on the CPU, now evaluated per-vertex on the GPU.
const gridVertexShader = /* glsl */ `
  uniform float uTime;
  uniform vec2 uCursor;   // ripple centre in plane space
  uniform float uPulse;   // click burst 0..1
  uniform float uSize;    // plane extent
  void main() {
    vec3 p = position;
    float bx = position.x;
    float by = position.y;
    float wave =
      sin(bx * 0.6 + uTime * 1.1) * 0.5 +
      sin(by * 0.7 - uTime * 0.9) * 0.5 +
      sin((bx + by) * 0.4 + uTime * 0.6) * 0.4;
    float d = distance(vec2(bx, by), uCursor);
    float ripple = sin(d * 1.4 - uTime * 3.0) * exp(-d * 0.35) * 0.9;
    float burst = 1.0 + uPulse * 1.6;
    p.z += (wave + ripple) * burst * 0.5;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(p, 1.0);
    gl_PointSize = 2.0;
  }
`;

export default function Background() {
  const mountRef = useRef(null);

  useEffect(() => {
    const mount = mountRef.current;
    if (!mount) return;
    if (typeof window === "undefined" || !window.WebGLRenderingContext) return;

    const reduceMotion = window.matchMedia?.(
      "(prefers-reduced-motion: reduce)",
    )?.matches;

    const hueDeg =
      parseFloat(
        getComputedStyle(document.documentElement).getPropertyValue("--accent-hue"),
      ) || 264.376;
    const hueUnit = (((hueDeg % 360) + 360) / 360) % 1;
    const accent = new THREE.Color().setHSL(hueUnit, 0.7, 0.62);
    const accentBright = new THREE.Color().setHSL(hueUnit, 0.8, 0.72);

    let renderer;
    try {
      renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    } catch {
      return;
    }
    const dpr = Math.min(window.devicePixelRatio || 1, 1.5);
    renderer.setPixelRatio(dpr);
    renderer.setSize(window.innerWidth, window.innerHeight);
    mount.appendChild(renderer.domElement);

    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(
      35,
      window.innerWidth / window.innerHeight,
      0.1,
      100,
    );
    camera.position.z = 6;

    const group = new THREE.Group();
    group.rotation.x = -0.32;
    scene.add(group);

    const SEG = 72;
    const SIZE = 14;

    // Shared uniforms drive both the grid lines and the points on the GPU.
    const uniforms = {
      uTime: { value: 0 },
      uCursor: { value: new THREE.Vector2(0, 0) },
      uPulse: { value: 0 },
      uSize: { value: SIZE },
    };

    // --- Flat rippling grid, displaced on the GPU ------------------------
    // Build the wireframe line geometry ONCE from a plane, then never touch it
    // again — the shader moves the vertices each frame.
    const planeGeo = new THREE.PlaneGeometry(SIZE, SIZE, SEG, SEG);
    const wire = new THREE.WireframeGeometry(planeGeo);
    planeGeo.dispose();
    const lineMat = new THREE.ShaderMaterial({
      uniforms: { ...uniforms, uColor: { value: accent }, uOpacity: { value: 0.5 } },
      vertexShader: gridVertexShader,
      fragmentShader: /* glsl */ `
        uniform vec3 uColor;
        uniform float uOpacity;
        void main() { gl_FragColor = vec4(uColor, uOpacity); }
      `,
      transparent: true,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    });
    const mesh = new THREE.LineSegments(wire, lineMat);
    group.add(mesh);

    // --- Drifting point layer (same GPU displacement) --------------------
    const COUNT = 700;
    const pts = new Float32Array(COUNT * 3);
    for (let i = 0; i < COUNT; i++) {
      pts[i * 3] = (Math.random() - 0.5) * SIZE;
      pts[i * 3 + 1] = (Math.random() - 0.5) * SIZE;
      pts[i * 3 + 2] = 0;
    }
    const pGeo = new THREE.BufferGeometry();
    pGeo.setAttribute("position", new THREE.BufferAttribute(pts, 3));
    const pMat = new THREE.ShaderMaterial({
      uniforms: { ...uniforms, uColor: { value: accentBright }, uOpacity: { value: 0.85 } },
      vertexShader: gridVertexShader,
      fragmentShader: /* glsl */ `
        uniform vec3 uColor;
        uniform float uOpacity;
        void main() {
          // round, soft point sprite
          vec2 c = gl_PointCoord - 0.5;
          if (dot(c, c) > 0.25) discard;
          gl_FragColor = vec4(uColor, uOpacity);
        }
      `,
      transparent: true,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    });
    const points = new THREE.Points(pGeo, pMat);
    group.add(points);

    // --- Chromatic aberration post-process -------------------------------
    const composer = new EffectComposer(renderer);
    composer.setPixelRatio(dpr);
    composer.setSize(window.innerWidth, window.innerHeight);
    composer.addPass(new RenderPass(scene, camera));
    const aberration = new ShaderPass(AberrationShader);
    composer.addPass(aberration);

    // --- Cursor interaction ----------------------------------------------
    const targetPtr = { x: 0, y: 0 };
    const smooth = { x: 0, y: 0 };
    const focusUv = { x: 0.5, y: 0.5 };
    let pulse = 0;

    const onPointer = (e) => {
      targetPtr.x = (e.clientX / window.innerWidth - 0.5) * 2;
      targetPtr.y = (e.clientY / window.innerHeight - 0.5) * 2;
      focusUv.x = e.clientX / window.innerWidth;
      focusUv.y = 1 - e.clientY / window.innerHeight;
    };
    const onDown = () => {
      pulse = 1;
    };
    window.addEventListener("pointermove", onPointer);
    window.addEventListener("pointerdown", onDown);

    const onResize = () => {
      camera.aspect = window.innerWidth / window.innerHeight;
      camera.updateProjectionMatrix();
      renderer.setSize(window.innerWidth, window.innerHeight);
      composer.setSize(window.innerWidth, window.innerHeight);
    };
    window.addEventListener("resize", onResize);

    let raf = 0;
    let running = true;
    const clock = new THREE.Clock();

    // Frame-rate cap: a background does not need 60fps. ~40fps halves the work.
    const FRAME_MS = 1000 / 40;
    let lastFrame = 0;

    const renderFrame = () => {
      const t = clock.getElapsedTime();
      smooth.x += (targetPtr.x - smooth.x) * 0.06;
      smooth.y += (targetPtr.y - smooth.y) * 0.06;
      pulse *= 0.93;

      // Push animation state to the GPU (cheap uniform writes).
      uniforms.uTime.value = t;
      uniforms.uCursor.value.set(smooth.x * (SIZE * 0.5), -smooth.y * (SIZE * 0.5));
      uniforms.uPulse.value = pulse;

      group.position.x = smooth.x * 0.8;
      group.position.y = -smooth.y * 0.5;
      group.rotation.z = smooth.x * 0.05;
      lineMat.uniforms.uOpacity.value = 0.5 + pulse * 0.35;

      const speed = Math.hypot(targetPtr.x - smooth.x, targetPtr.y - smooth.y);
      aberration.uniforms.amount.value = 0.0016 + speed * 0.02 + pulse * 0.02;
      aberration.uniforms.focus.value.set(focusUv.x, focusUv.y);

      composer.render();
    };

    const loop = (now) => {
      if (!running) return;
      raf = requestAnimationFrame(loop);
      if (now - lastFrame < FRAME_MS) return; // throttle to the cap
      lastFrame = now;
      renderFrame();
    };

    const onVisibility = () => {
      if (document.hidden) {
        running = false;
        cancelAnimationFrame(raf);
      } else if (!reduceMotion) {
        running = true;
        lastFrame = 0;
        raf = requestAnimationFrame(loop);
      }
    };
    document.addEventListener("visibilitychange", onVisibility);

    if (reduceMotion) {
      aberration.uniforms.amount.value = 0.0016;
      renderFrame();
    } else {
      raf = requestAnimationFrame(loop);
    }

    return () => {
      running = false;
      cancelAnimationFrame(raf);
      window.removeEventListener("pointermove", onPointer);
      window.removeEventListener("pointerdown", onDown);
      window.removeEventListener("resize", onResize);
      document.removeEventListener("visibilitychange", onVisibility);
      composer.dispose();
      renderer.dispose();
      wire.dispose();
      lineMat.dispose();
      pGeo.dispose();
      pMat.dispose();
      if (renderer.domElement.parentNode === mount) {
        mount.removeChild(renderer.domElement);
      }
    };
  }, []);

  return (
    <div
      ref={mountRef}
      aria-hidden="true"
      data-testid="app-background"
      className="pointer-events-none fixed inset-0 -z-10 opacity-70 [mask-image:radial-gradient(ellipse_at_center,black_55%,transparent_100%)]"
    />
  );
}
