import { useEffect, useRef, useState } from 'react'
import { Pause, Play } from 'lucide-react'
import './crying-panda.css'

export function CryingPanda() {
  const host = useRef<HTMLDivElement>(null)
  const scene = useRef<{ setPaused(value: boolean): void; dispose(): void } | null>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'unavailable'>('loading')
  const [paused, setPaused] = useState(false)

  useEffect(() => {
    let cancelled = false
    void import('./crying-panda-scene').then(({ createPandaScene }) => {
      if (cancelled || !host.current) return
      try {
        scene.current = createPandaScene(host.current)
        setStatus('ready')
      } catch {
        setStatus('unavailable')
      }
    }).catch(() => { if (!cancelled) setStatus('unavailable') })
    return () => { cancelled = true; scene.current?.dispose(); scene.current = null }
  }, [])

  return (
    <section className="panda-stage" aria-label="Animated 3D cartoon panda having an office meltdown — crying at its desk, collapsing and rolling on the floor before slumping in exhaustion" data-paused={paused}>
      <div className="panda-words" lang="zh-Hans" aria-label="不玩了，把 token 还我~">
        <span className="panda-word panda-word-first">不玩了</span>
        <span className="panda-word panda-word-second">把 token 还我~</span>
      </div>
      <div ref={host} className="panda-canvas" aria-hidden="true" />
      {status !== 'ready' && <p className="panda-status" role="status">{status === 'loading' ? 'Waking up the panda…' : 'The panda needs a browser with 3D graphics enabled.'}</p>}
      {status === 'ready' && (
        <button type="button" className="panda-motion" aria-pressed={paused} onClick={() => {
          const next = !paused
          scene.current?.setPaused(next)
          setPaused(next)
        }}>
          {paused ? <Play size={14} /> : <Pause size={14} />}
          {paused ? 'Resume animation' : 'Pause animation'}
        </button>
      )}
    </section>
  )
}
