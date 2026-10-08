#!/usr/bin/env bash
# One-command deployment of CareerPilot on a fresh Ubuntu 22.04/24.04 VM (Docker Compose).
#
#   curl -fsSL https://raw.githubusercontent.com/Darshiniml/CareerPilot-OS/<branch>/deployment/vm/deploy.sh -o deploy.sh
#   sudo DOMAIN=careerpilot.example.com bash deploy.sh          # HTTPS (DNS A record must point here)
#   sudo bash deploy.sh                                         # HTTP only, http://<server-ip>
#
# AI: Anthropic (Claude) for generation; embeddings run locally in an Ollama container
# (nomic-embed-text, CPU is fine for embeddings). The Anthropic key is read from ANTHROPIC_API_KEY or
# prompted for; it is written only to /opt/careerpilot/.env (mode 600), never to the repository.
# Re-running the script updates the code and restarts; existing secrets and data are kept.
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/Darshiniml/CareerPilot-OS.git}"
BRANCH="${BRANCH:-feat/milestone-22.3-22.4-classification-timeline}"
APP_DIR="${APP_DIR:-/opt/careerpilot}"
DOMAIN="${DOMAIN:-}"
LLM_MODEL="${LLM_MODEL:-}"

log() { printf '\n==> %s\n' "$*"; }
[ "$(id -u)" -eq 0 ] || { echo "Run as root (sudo)." >&2; exit 1; }

log "Docker"
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
fi
docker compose version >/dev/null

if [ "$(free -m | awk '/^Mem:/{print $2}')" -lt 3500 ] && ! swapon --show | grep -q .; then
  log "Less than 4 GB RAM: adding a 4 GB swap file"
  fallocate -l 4G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

log "Code ($BRANCH)"
if [ -d "$APP_DIR/.git" ]; then
  git -C "$APP_DIR" fetch --depth 1 origin "$BRANCH"
  git -C "$APP_DIR" checkout -B "$BRANCH" FETCH_HEAD
else
  git clone --depth 1 --branch "$BRANCH" "$REPO_URL" "$APP_DIR"
fi
cd "$APP_DIR"

secret() { openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c "${1:-40}"; }
setenv() {  # setenv KEY VALUE  -> replace or append in .env
  if grep -q "^$1=" .env; then sed -i "s|^$1=.*|$1=$2|" .env; else echo "$1=$2" >> .env; fi
}

log "Configuration (.env)"
if [ ! -f .env ]; then
  umask 077
  cat > .env <<CONF
JWT_SECRET_KEY=$(secret 64)
AI_SERVICE_TOKEN=$(secret 40)
DB_PASSWORD=$(secret 32)
REDIS_PASSWORD=$(secret 32)
S3_ACCESS_KEY=careerpilot
S3_SECRET_KEY=$(secret 32)
GRAFANA_ADMIN_PASSWORD=$(secret 24)
EMAIL_TOKEN_ENCRYPTION_KEY=$(openssl rand -base64 32)
LLM_PROVIDER=anthropic
LLM_TIMEOUT_SECONDS=180
CAREERPILOT_AI_TIMEOUT_MS=240000
EMBEDDING_PROVIDER=ollama
OLLAMA_BASE_URL=http://ollama:11434
OLLAMA_MODELS=nomic-embed-text
CONF
fi
chmod 600 .env

if ! grep -q '^ANTHROPIC_API_KEY=.\+' .env; then
  key="${ANTHROPIC_API_KEY:-}"
  if [ -z "$key" ]; then
    read -r -s -p "Anthropic API key (input hidden): " key; echo
  fi
  [ -n "$key" ] || { echo "An Anthropic API key is required." >&2; exit 1; }
  setenv ANTHROPIC_API_KEY "$key"
fi
[ -n "$LLM_MODEL" ] && setenv LLM_MODEL "$LLM_MODEL"

profiles=(--profile ollama)
if [ -n "$DOMAIN" ]; then
  setenv DOMAIN "$DOMAIN"
  setenv PUBLIC_URL "https://$DOMAIN"
  setenv HTTP_BIND "127.0.0.1:8081"
  profiles+=(--profile tls)
  url="https://$DOMAIN"
else
  ip="$(curl -fsS https://api.ipify.org || hostname -I | awk '{print $1}')"
  setenv PUBLIC_URL "http://$ip"
  setenv HTTP_BIND "80"
  url="http://$ip"
fi
pub="$(grep '^PUBLIC_URL=' .env | cut -d= -f2)"
setenv GMAIL_OAUTH_REDIRECT_URI "$pub/api/v1/email/oauth/gmail/callback"
setenv OUTLOOK_OAUTH_REDIRECT_URI "$pub/api/v1/email/oauth/outlook/callback"

if command -v ufw >/dev/null 2>&1 && ufw status | grep -q active; then
  ufw allow 80/tcp; ufw allow 443/tcp
fi

log "Building and starting (first build takes 5-15 minutes)"
docker compose "${profiles[@]}" up -d --build

log "Waiting for the backend to become healthy"
for i in $(seq 1 60); do
  status="$(docker inspect -f '{{.State.Health.Status}}' "$(docker compose ps -q backend)" 2>/dev/null || true)"
  [ "$status" = "healthy" ] && break
  sleep 10
done
docker compose "${profiles[@]}" ps
[ "${status:-}" = "healthy" ] || { echo "Backend did not become healthy; see: docker compose logs backend" >&2; exit 1; }

log "AI provider check"
docker compose exec -T ai-service python -c "import os,json,urllib.request as u; r=u.Request('http://localhost:8000/api/v1/ai/providers', headers={'Authorization': 'Bearer '+os.environ['AI_SERVICE_TOKEN']}); print(json.dumps(json.load(u.urlopen(r)), indent=2))" || true

log "CareerPilot is running at $url"
echo "Admin-only dashboards (via SSH tunnel): Grafana 127.0.0.1:3000, Prometheus 127.0.0.1:9090, MinIO 127.0.0.1:9001"
echo "Secrets: $APP_DIR/.env (keep it private; back it up together with the docker volumes)."
