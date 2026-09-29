import { useEffect, useRef, useState } from 'react'
import type { ForgeScene, SceneMode } from './forgeScene.ts'
import './ForgeBackdrop.css'

interface ForgeBackdropProps {
  /** 'auth' puts the gem beside the sign-in card; 'app' moves it aside for page content. */
  mode: SceneMode
}

/**
 * Decorative full-viewport three.js backdrop. A CSS gradient shows first and stays as the
 * fallback; three.js is code-split and only fetched after the page has rendered. Environments
 * without matchMedia (jsdom, very old browsers) or WebGL keep the CSS gradient.
 */
export function ForgeBackdrop({ mode }: ForgeBackdropProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const sceneRef = useRef<ForgeScene | undefined>(undefined)
  const modeRef = useRef(mode)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas || typeof window.matchMedia !== 'function') return
    const animate = !window.matchMedia('(prefers-reduced-motion: reduce)').matches
    let cancelled = false

    import('./forgeScene.ts')
      .then(({ createForgeScene }) => {
        if (cancelled) return
        sceneRef.current = createForgeScene(canvas, { animate, mode: modeRef.current })
        setReady(true)
      })
      // No WebGL or the chunk failed to load: the CSS gradient stays.
      .catch(() => undefined)

    return () => {
      cancelled = true
      sceneRef.current?.dispose()
      sceneRef.current = undefined
    }
  }, [])

  useEffect(() => {
    modeRef.current = mode
    sceneRef.current?.setMode(mode)
  }, [mode])

  return (
    <div className="forge-backdrop" aria-hidden="true">
      <canvas ref={canvasRef} className={ready ? 'forge-canvas is-ready' : 'forge-canvas'} />
    </div>
  )
}
