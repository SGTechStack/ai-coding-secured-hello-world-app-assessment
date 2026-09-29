interface ImportMetaEnv {
  /** Origin of the backend API, e.g. `http://localhost:8080`. Required for production builds. */
  readonly VITE_API_ORIGIN?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
