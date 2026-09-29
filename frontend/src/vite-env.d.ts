/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Origin of the Spring Boot API, e.g. https://api.example.com. Defaults to http://localhost:8080. */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
