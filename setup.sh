#!/usr/bin/env bash
# SquadRelay local setup (Linux / macOS). Does not modify tracked Gradle files.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT/backend"
BACKEND_ENV="$BACKEND_DIR/.env"
LOCAL_PROPS_ROOT="$ROOT/local.properties"
LOCAL_PROPS_ANDROID="$ROOT/mobile-android/local.properties"

random_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 48 | tr -d '/+=' | head -c 64
  else
    LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 64
  fi
}

set_local_property() {
  local file="$1" key="$2" value="$3"
  local dir
  dir="$(dirname "$file")"
  mkdir -p "$dir"
  if [[ -f "$file" ]]; then
    grep -v "^[[:space:]]*${key}[[:space:]]*=" "$file" >"${file}.tmp" || true
    mv "${file}.tmp" "$file"
  fi
  printf '%s=%s\n' "$key" "$value" >>"$file"
}

echo ""
echo "=== SquadRelay: local setup ==="
echo ""

read -r -p "MONGODB_URI (MongoDB Atlas): " MONGODB_URI
read -r -p "API URL (e.g. https://your-app.onrender.com/): " API_BASE_URL

if [[ -z "${MONGODB_URI:-}" || -z "${API_BASE_URL:-}" ]]; then
  echo "MONGODB_URI and API URL are required." >&2
  exit 1
fi

[[ "$API_BASE_URL" == */ ]] || API_BASE_URL="${API_BASE_URL}/"

JWT_SECRET="$(random_secret)"
JWT_REFRESH_SECRET="$(random_secret)"

cat >"$BACKEND_ENV" <<EOF
PORT=3000
MONGODB_URI=$MONGODB_URI
MONGODB_DB_NAME=last_asylum
JWT_SECRET=$JWT_SECRET
JWT_EXPIRES_IN=7d
JWT_REFRESH_SECRET=$JWT_REFRESH_SECRET
JWT_REFRESH_EXPIRES_IN=30d
EOF

echo "Created backend/.env (do not commit)"

set_local_property "$LOCAL_PROPS_ROOT" "squadrelay.api.baseUrl" "$API_BASE_URL"
set_local_property "$LOCAL_PROPS_ANDROID" "squadrelay.api.baseUrl" "$API_BASE_URL"
echo "Wrote squadrelay.api.baseUrl to local.properties (do not commit)"

echo ""
echo "Installing backend dependencies..."
(cd "$BACKEND_DIR" && npm install)
echo "Running lint, test, build..."
(cd "$BACKEND_DIR" && npm run lint && npm test && npm run build)

echo ""
echo "Done."
echo "  cd backend && npm run start:dev"
echo "  Open mobile-android in Android Studio and run the dev variant."
