// =============================================================================
// easing.ts - pure scalar math for the office-panda-meltdown scene.
//
// THREE-free: every function operates on plain numbers so the timeline, rig,
// office and tear modules can share the same easing/window/noise helpers and a
// single damped Spring without pulling in any rendering types.
//
// Conventions (contract section 3):
//   - easing fns take t in [0,1] (callers pass seg(...) results);
//   - windowRamp/bump take ABSOLUTE seconds and are EXACTLY 0 at/outside edges,
//     so they can gate procedural overlays to zero at phase boundaries;
//   - valueNoise is C1-smooth and deterministic; the seed selects an
//     independent stream.
//
// TypeScript rules: strict, noUnusedLocals, noUnusedParameters,
// erasableSyntaxOnly. No enums, no namespaces, no constructor parameter
// properties (Spring uses plain fields + an explicit constructor body).
// =============================================================================

/** Clamp x into [0,1]. */
export function clamp01(x: number): number {
  return x < 0 ? 0 : x > 1 ? 1 : x
}

/** Linear interpolate from a to b by t (t is not clamped). */
export function lerp(a: number, b: number, t: number): number {
  return a + (b - a) * t
}

/** Smooth Hermite interpolation; returns 0 for x<=edge0, 1 for x>=edge1. */
export function smoothstep(edge0: number, edge1: number, x: number): number {
  if (edge0 === edge1) return x < edge0 ? 0 : 1
  const t = clamp01((x - edge0) / (edge1 - edge0))
  return t * t * (3 - 2 * t)
}

/**
 * Normalised progress of t within [start,end], clamped to [0,1].
 * A degenerate span (start===end) returns 1 for t>=start else 0, never NaN.
 */
export function seg(t: number, start: number, end: number): number {
  if (start === end) return t >= start ? 1 : 0
  return clamp01((t - start) / (end - start))
}

/**
 * Smoothed trapezoid window over [start,end]: 0 at/outside the edges, ramps up
 * over `rise` seconds, holds at 1, ramps down over `fall` seconds. Exactly 0 at
 * the boundaries so overlays vanish at phase seams.
 */
export function windowRamp(t: number, start: number, end: number, rise: number, fall: number): number {
  if (t <= start || t >= end) return 0
  const up = rise > 0 ? smoothstep(start, start + rise, t) : 1
  const down = fall > 0 ? 1 - smoothstep(end - fall, end, t) : 1
  return clamp01(up) * clamp01(down)
}

/** Smooth 0->1->0 hump over [start,end] (peak at centre); exactly 0 at edges. */
export function bump(t: number, start: number, end: number): number {
  if (t <= start || t >= end || start === end) return 0
  const u = (t - start) / (end - start) // (0,1)
  const s = Math.sin(Math.PI * u)
  return s * s
}

/** Ease-in cubic; t in [0,1]. */
export function easeInCubic(t: number): number {
  return t * t * t
}

/** Ease-out cubic; t in [0,1]. */
export function easeOutCubic(t: number): number {
  const u = 1 - t
  return 1 - u * u * u
}

/** Ease-in-out cubic; t in [0,1]. */
export function easeInOutCubic(t: number): number {
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2
}

/** Ease-out with a slight overshoot past 1 then settle (t in [0,1]). */
export function easeOutBack(t: number, overshoot: number = 1.70158): number {
  const c1 = overshoot
  const c3 = c1 + 1
  const u = t - 1
  return 1 + c3 * u * u * u + c1 * u * u
}

/** Bouncy ease-out; t in [0,1]. */
export function easeOutBounce(t: number): number {
  const n1 = 7.5625
  const d1 = 2.75
  if (t < 1 / d1) {
    return n1 * t * t
  } else if (t < 2 / d1) {
    const u = t - 1.5 / d1
    return n1 * u * u + 0.75
  } else if (t < 2.5 / d1) {
    const u = t - 2.25 / d1
    return n1 * u * u + 0.9375
  }
  const u = t - 2.625 / d1
  return n1 * u * u + 0.984375
}

// -----------------------------------------------------------------------------
// 1D value noise in [-1,1]: C1-smooth (smoothstep-blended lattice), deterministic.
// `seed` selects a decorrelated stream via a hash of the integer lattice index.
// -----------------------------------------------------------------------------
function hash01(i: number, seed: number): number {
  // Integer hash -> [0,1). Mixes the lattice index with the seed stream.
  let h = (i | 0) ^ ((seed | 0) * 0x9e3779b1)
  h = Math.imul(h ^ (h >>> 15), 0x85ebca6b)
  h = Math.imul(h ^ (h >>> 13), 0xc2b2ae35)
  h ^= h >>> 16
  // >>> 0 -> unsigned; divide by 2^32 for [0,1).
  return (h >>> 0) / 4294967296
}

export function valueNoise(x: number, seed: number = 0): number {
  const i0 = Math.floor(x)
  const f = x - i0
  const a = hash01(i0, seed) * 2 - 1
  const b = hash01(i0 + 1, seed) * 2 - 1
  const u = f * f * (3 - 2 * f) // smoothstep blend for C1 continuity
  return a + (b - a) * u
}

// -----------------------------------------------------------------------------
// Spring: a stable, near-critically-damped 1D spring. Plain public fields
// (value, velocity) so callers can nudge velocity directly (impulse kicks);
// explicit constructor body (no parameter properties). Snaps to the target on
// the first update after construction/reset to avoid a fly-in.
// -----------------------------------------------------------------------------
export class Spring {
  value: number
  velocity: number
  private target: number
  private stiffness: number
  private damping: number
  private initialised: boolean

  constructor(stiffness: number, damping: number) {
    this.stiffness = stiffness
    this.damping = damping
    this.value = 0
    this.velocity = 0
    this.target = 0
    this.initialised = false
  }

  /** Set the spring's target without advancing time. */
  setTarget(target: number): void {
    this.target = target
  }

  /**
   * Advance toward `target` by dt seconds. Substeps internally to <= 1/120 s
   * for stability at large dt. On the FIRST call after construction/reset it
   * snaps value=target, velocity=0 (no fly-in).
   */
  update(target: number, dt: number): number {
    this.target = target
    if (!this.initialised) {
      this.initialised = true
      this.value = target
      this.velocity = 0
      return this.value
    }
    if (dt <= 0) return this.value
    const maxStep = 1 / 120
    let remaining = dt
    const k = this.stiffness
    const c = this.damping
    while (remaining > 0) {
      const h = remaining > maxStep ? maxStep : remaining
      // Semi-implicit (symplectic) Euler: update velocity, then position.
      const accel = -k * (this.value - this.target) - c * this.velocity
      this.velocity += accel * h
      this.value += this.velocity * h
      remaining -= h
    }
    return this.value
  }

  /** Snap immediately to `value`, clear velocity, mark initialised. */
  reset(value: number): void {
    this.value = value
    this.target = value
    this.velocity = 0
    this.initialised = true
  }
}
