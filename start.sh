#!/usr/bin/env bash
# Build and start the Hello World Auth app (backend :8080 + frontend :3000).
#
#   ./start.sh            # build both, then run backend (dev profile) + Vite dev server
#   PROFILE=prod ./start.sh   # run the backend under the prod profile
#   ./start.sh --skip-build   # skip the build steps, just start
#
# Maven behind a TLS-inspecting proxy (Zscaler etc.):
#   MAVEN_OPTS="-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT" ./start.sh
set -euo pipefail
cd "$(dirname "$0")"

PROFILE="${PROFILE:-dev}"
SKIP_BUILD=false
[ "${1:-}" = "--skip-build" ] && SKIP_BUILD=true

if [ "$SKIP_BUILD" = false ]; then
    echo "==> Building backend (mvn package, tests skipped)"
    (cd backend && mvn -q -DskipTests package)

    echo "==> Building frontend (npm ci + vite build)"
    (cd frontend && npm ci --no-fund --no-audit && npm run build)
fi

echo "==> Starting backend on :8080 (profile: $PROFILE)"
java -jar backend/target/hello-auth-backend-0.0.1-SNAPSHOT.jar \
    --spring.profiles.active="$PROFILE" &
BACKEND_PID=$!

echo "==> Starting frontend on :3000 (vite dev server)"
(cd frontend && npm run dev) &
FRONTEND_PID=$!

trap 'kill $BACKEND_PID $FRONTEND_PID 2>/dev/null || true' EXIT INT TERM

echo "==> Both starting. SPA: http://localhost:3000  API: http://localhost:8080"
echo "    dev admin login: admin / admin-local-dev-password (dev profile only)"
wait
