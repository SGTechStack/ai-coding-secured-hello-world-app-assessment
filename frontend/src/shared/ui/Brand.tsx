import './Brand.css'

/** The "Eitri Login" wordmark with its faceted-gem logo. Decorative text, not a heading. */
export function Brand() {
  return (
    <span className="brand">
      <svg className="brand__mark" viewBox="0 0 32 32" aria-hidden="true" focusable="false">
        <defs>
          <linearGradient id="brand-molten" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stopColor="#ffcf8a" />
            <stop offset="0.5" stopColor="#ff8a3d" />
            <stop offset="1" stopColor="#ff5d73" />
          </linearGradient>
        </defs>
        <path d="M16 2 28 9v14l-12 7L4 23V9Z" fill="url(#brand-molten)" />
        <path d="M16 2v28M4 9l12 7 12-7M4 23l12-7 12 7" stroke="#1b0d05" strokeOpacity="0.35" />
      </svg>
      <span className="brand__name">Eitri Login</span>
    </span>
  )
}
