# Frontend and API are cross-origin but must be same-site

The React frontend and Spring Boot API are served from separate origins, and the API allows the frontend through an explicit CORS allow-list with credentials enabled (never a wildcard). The session cookie stays `SameSite=Lax` as the standard requires. That only works if both origins belong to the same *site*: `localhost:3000` and `localhost:8080` do, and in production the two must share a registrable domain (e.g. `app.example.gov.sg` and `api.example.gov.sg`). Splitting them across sites would force `SameSite=None`, which the standard forbids. That is a deployment constraint the code cannot enforce, hence this record. The cookie is `Secure=true` by default; only the dev profile may turn it off, through a property.

## Considered Options

- A dev proxy that puts the SPA and API on one origin was rejected: the PRD explicitly asks for cross-origin CORS, and a proxy would leave the CORS and credentials setup untested.
