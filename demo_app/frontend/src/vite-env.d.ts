/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** The API's origin, e.g. `https://api.example.com`. Defaults to `http://localhost:8080`. */
  readonly VITE_API_BASE_URL?: string;
}
