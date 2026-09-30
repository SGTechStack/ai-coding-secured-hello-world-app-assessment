import * as THREE from 'three'

/** Seeded surface detail, generated once and owned by the scene. */
export function surfaceTexture(kind: 'fur' | 'weave' | 'wood'): THREE.DataTexture {
  const size = 256
  const data = new Uint8Array(size * size * 4)
  let seed = 817
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0
      const noise = seed / 4294967296
      const grain = Math.sin(x * 0.35 + Math.sin(y * 0.024) * 3)
      const value = kind === 'wood'
        ? 175 + grain * 24 + noise * 16
        : kind === 'weave'
          ? 170 + ((x % 4 < 2) !== (y % 4 < 2) ? 38 : 0) + noise * 16
          : 155 + noise * 95
      const i = (y * size + x) * 4
      data[i] = data[i + 1] = data[i + 2] = value
      data[i + 3] = 255
    }
  }
  const texture = new THREE.DataTexture(data, size, size)
  texture.wrapS = texture.wrapT = THREE.RepeatWrapping
  texture.magFilter = THREE.LinearFilter
  texture.minFilter = THREE.LinearMipmapLinearFilter
  texture.generateMipmaps = true
  texture.repeat.set(kind === 'wood' ? 2 : 5, kind === 'wood' ? 2 : 3)
  texture.needsUpdate = true
  return texture
}

/** Tapered guard hairs on a unit sphere, shared by the rig's fur ellipsoids.
 * Geometry follows its parent mesh, so it needs no per-frame CPU work.
 */
export function furGeometry(): THREE.BufferGeometry {
  const positions: number[] = []
  const normals: number[] = []
  const colors: number[] = []
  const normal = new THREE.Vector3()
  const tangent = new THREE.Vector3()
  const root = new THREE.Vector3()
  const tip = new THREE.Vector3()
  const up = new THREE.Vector3(0, 1, 0)
  const count = 24000
  for (let i = 0; i < count; i++) {
    const y = 1 - 2 * (i + 0.5) / count
    const radius = Math.sqrt(1 - y * y)
    const angle = i * 2.399963229728653
    normal.set(Math.cos(angle) * radius, y, Math.sin(angle) * radius)
    tangent.crossVectors(normal, up).normalize()
    root.copy(normal).multiplyScalar(0.997)
    const variation = (Math.sin(i * 127.1) * 43758.5453) % 1
    const length = 0.035 + Math.abs(variation) * 0.04
    tip.copy(normal).multiplyScalar(1 + length)
    tip.y -= length * 0.3
    for (const side of [-1, 1, 0]) {
      const point = side === 0 ? tip : root.clone().addScaledVector(tangent, side * 0.0016)
      positions.push(point.x, point.y, point.z)
      normals.push(normal.x, normal.y, normal.z)
      const shade = side === 0 ? 1 : 0.72 + Math.abs(variation) * 0.18
      colors.push(shade, shade, shade)
    }
  }
  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3))
  geometry.setAttribute('normal', new THREE.Float32BufferAttribute(normals, 3))
  geometry.setAttribute('color', new THREE.Float32BufferAttribute(colors, 3))
  geometry.computeBoundingSphere()
  return geometry
}
