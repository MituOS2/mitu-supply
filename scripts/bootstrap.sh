#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

REBUILD=0
NO_BROWSER=0
BUILD_ONLY=0
TUNNEL=0
for arg in "$@"; do
  case "$arg" in
    --rebuild) REBUILD=1 ;;
    --no-browser) NO_BROWSER=1 ;;
    --build-only) BUILD_ONLY=1 ;;
    --tunnel) TUNNEL=1 ;;
  esac
done

echo "================================================================"
echo "  TBD.SUPPLY // Instalador y Lanzador Automatico (POSIX)"
echo "================================================================"

install_missing_packages() {
  local need_node="$1"
  local need_java="$2"
  local need_git="$3"

  if [ "$need_node" = "0" ] && [ "$need_java" = "0" ] && [ "$need_git" = "0" ]; then
    return 0
  fi

  echo "[setup] Faltan dependencias: Node>=24=$need_node, JDK>=21=$need_java, Git=$need_git"
  if command -v brew >/dev/null 2>&1; then
    [ "$need_git" = "1" ] && brew install git
    [ "$need_node" = "1" ] && brew install node@24 && brew link --overwrite --force node@24 || true
    [ "$need_java" = "1" ] && brew install openjdk@21
  elif command -v apt-get >/dev/null 2>&1; then
    local SUDO=""
    if [ "$(id -u)" -ne 0 ]; then SUDO="sudo"; fi
    $SUDO apt-get update
    [ "$need_git" = "1" ] && $SUDO apt-get install -y git
    [ "$need_java" = "1" ] && $SUDO apt-get install -y openjdk-21-jdk
    if [ "$need_node" = "1" ]; then
      $SUDO apt-get install -y curl ca-certificates gnupg
      curl -fsSL https://deb.nodesource.com/setup_24.x | $SUDO -E bash -
      $SUDO apt-get install -y nodejs
    fi
  else
    echo "[error] Instala manualmente Node.js >= 24, JDK 21 y Git en tu distribucion."
    exit 1
  fi
}

NEED_NODE=1
if command -v node >/dev/null 2>&1 && command -v npm >/dev/null 2>&1; then
  NODE_MAJOR="$(node -p "parseInt(process.versions.node.split('.')[0], 10)")"
  if [ "$NODE_MAJOR" -ge 24 ]; then
    NEED_NODE=0
  fi
fi

NEED_JAVA=1
if command -v java >/dev/null 2>&1; then
  JAVA_VER="$(java -version 2>&1 | head -n1 | sed -E 's/.*"([0-9]+).*/\1/' || echo 0)"
  if [ "${JAVA_VER:-0}" -ge 21 ] 2>/dev/null; then
    NEED_JAVA=0
  fi
fi

NEED_GIT=0
if [ ! -f "./vendor/miuix/settings.gradle.kts" ]; then
  if ! command -v git >/dev/null 2>&1; then
    NEED_GIT=1
  fi
fi

install_missing_packages "$NEED_NODE" "$NEED_JAVA" "$NEED_GIT"

VENDOR_UI_ROOT="$PROJECT_ROOT/vendor/miuix"
if [ ! -f "$VENDOR_UI_ROOT/settings.gradle.kts" ]; then
  echo "[setup] Descargando motor de interfaz en vendor/miuix..."
  git clone --depth 50 https://github.com/compose-miuix-ui/miuix.git "$VENDOR_UI_ROOT"
  git -C "$VENDOR_UI_ROOT" checkout d8efe187 || true
fi

if [ ! -f "$PROJECT_ROOT/.env" ]; then
  cp "$PROJECT_ROOT/.env.example" "$PROJECT_ROOT/.env"
  PEPPER="$(node -e "console.log(require('node:crypto').randomBytes(32).toString('hex'))")"
  SALT="$(node -e "console.log(require('node:crypto').randomBytes(32).toString('hex'))")"
  node -e "
    const fs = require('node:fs');
    let env = fs.readFileSync('.env', 'utf8');
    env = env.replace(/^SESSION_PEPPER=.*$/m, 'SESSION_PEPPER=' + process.argv[1]);
    env = env.replace(/^IP_HASH_SALT=.*$/m, 'IP_HASH_SALT=' + process.argv[2]);
    fs.writeFileSync('.env', env);
  " "$PEPPER" "$SALT"
  echo "[setup] Archivo .env generado con secretos criptograficos unicos."
fi

DIST_DIR="$PROJECT_ROOT/server/dist-web"
STAMP_FILE="$DIST_DIR/.build-stamp"
SOURCE_HASH="$(find "$PROJECT_ROOT/webApp/src" "$PROJECT_ROOT/webApp/build.gradle.kts" "$PROJECT_ROOT/settings.gradle.kts" -type f -printf '%T@:%p\n' 2>/dev/null | sort | sha256sum | cut -d' ' -f1 || echo rebuild)"

if [ "$REBUILD" = "1" ] || [ ! -f "$DIST_DIR/composeApp.js" ] || [ ! -f "$STAMP_FILE" ] || [ "$(cat "$STAMP_FILE")" != "$SOURCE_HASH" ]; then
  echo "[build] Compilando interfaz web..."
  export GIT_DIR="$VENDOR_UI_ROOT/.git"
  export GIT_WORK_TREE="$VENDOR_UI_ROOT"
  chmod +x "$PROJECT_ROOT/gradlew"
  "$PROJECT_ROOT/gradlew" :webApp:wasmJsBrowserDistribution -Prelease --no-configure-on-demand --console=plain
  rm -rf "$DIST_DIR"
  mkdir -p "$DIST_DIR"
  cp -R "$PROJECT_ROOT/webApp/build/dist/wasmJs/productionExecutable/." "$DIST_DIR/"
  echo "$SOURCE_HASH" > "$STAMP_FILE"
else
  echo "[build] Interfaz web actualizada (cache hit)."
fi

if [ "$BUILD_ONLY" = "1" ]; then
  echo "[build-only] Compilacion finalizada con exito."
  exit 0
fi

PORT="$(grep -E '^PORT=' "$PROJECT_ROOT/.env" | cut -d= -f2 | tr -d '\r' || echo 3000)"
PORT="${PORT:-3000}"

node "$PROJECT_ROOT/server/src/index.js" &
SERVER_PID=$!
CF_PID=""
cleanup() {
  [ -n "$CF_PID" ] && kill "$CF_PID" 2>/dev/null || true
  kill "$SERVER_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

for _ in $(seq 1 40); do
  if node -e "fetch('http://127.0.0.1:' + process.argv[1] + '/api/health').then(r => process.exit(r.ok ? 0 : 1)).catch(() => process.exit(1))" "$PORT"; then
    break
  fi
  sleep 0.3
done

if [ "$TUNNEL" = "1" ]; then
  if command -v cloudflared >/dev/null 2>&1; then
    CF_LOG="$PROJECT_ROOT/.tools/cloudflared.log"
    mkdir -p "$PROJECT_ROOT/.tools"
    cloudflared tunnel --url "http://127.0.0.1:$PORT" --no-autoupdate 2>"$CF_LOG" &
    CF_PID=$!
    for _ in $(seq 1 40); do
      PUB_URL="$(grep -oE 'https://[a-zA-Z0-9-]+\.trycloudflare\.com' "$CF_LOG" 2>/dev/null | head -n1 || true)"
      if [ -n "$PUB_URL" ]; then
        echo ""
        echo "================================================================"
        echo "  ENLACE PUBLICO CLOUDFLARE LISTO:"
        echo "  $PUB_URL"
        echo "================================================================"
        echo ""
        break
      fi
      sleep 0.5
    done
  else
    echo "[tunnel] Instala cloudflared para usar --tunnel en Linux/macOS."
  fi
fi

if [ "$NO_BROWSER" = "0" ]; then
  URL="http://127.0.0.1:$PORT"
  if command -v xdg-open >/dev/null 2>&1; then xdg-open "$URL" >/dev/null 2>&1 &
  elif command -v open >/dev/null 2>&1; then open "$URL" >/dev/null 2>&1 &
  fi
fi

wait "$SERVER_PID"
