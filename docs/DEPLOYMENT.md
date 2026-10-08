# Deploying CareerPilot to a cloud VM (Docker Compose + Claude)

## 1. Server

- Ubuntu 22.04 / 24.04, **2 vCPU / 4 GB RAM minimum** (4 vCPU / 8 GB recommended), 30 GB disk.
  Any provider: AWS EC2 (t3.medium+), Azure B2s+, DigitalOcean, Hetzner CX22+ …
- Firewall / security group: allow **22** (SSH), **80** and **443**. Nothing else needs to be public.
- For HTTPS: a domain name with an **A record pointing at the server's public IP**.

## 2. Deploy (one command)

SSH into the server, then:

```bash
curl -fsSL https://raw.githubusercontent.com/Darshiniml/CareerPilot-OS/feat/milestone-22.3-22.4-classification-timeline/deployment/vm/deploy.sh -o deploy.sh
sudo DOMAIN=careerpilot.example.com bash deploy.sh      # HTTPS via Let's Encrypt
# or, without a domain:  sudo bash deploy.sh             # http://<server-ip>
```

The script installs Docker, clones the branch to `/opt/careerpilot`, generates all secrets into
`/opt/careerpilot/.env` (mode 600), asks for your **Anthropic API key** (hidden input; or pass
`ANTHROPIC_API_KEY=...`), builds the images and waits until the backend is healthy.

- Generation: Claude (`LLM_PROVIDER=anthropic`, default model `claude-opus-5-5`; set
  `LLM_MODEL=claude-sonnet-5-5` when running the script for lower cost).
- Embeddings: `nomic-embed-text` in a local Ollama container (pulled automatically; CPU is fine).
- Data lives in Docker volumes (PostgreSQL, Qdrant, MinIO). Back up the volumes **and** `.env`.

Re-run the same command to update to the latest commit on the branch (secrets and data are kept).

## 3. After deploying

- Open the URL, register an account, upload a resume.
- Admin dashboards are bound to localhost; reach them with an SSH tunnel, e.g.
  `ssh -L 3000:127.0.0.1:3000 user@server` → Grafana on http://localhost:3000
  (password: `GRAFANA_ADMIN_PASSWORD` in `.env`).
- Logs: `cd /opt/careerpilot && docker compose logs -f backend ai-service`.
- Optional integrations (Gmail/Outlook sending, Adzuna, Jooble, n8n): add the keys to `.env`,
  then `docker compose --profile ollama [--profile tls] up -d`. OAuth redirect URIs are already set
  to `<PUBLIC_URL>/api/v1/email/oauth/{gmail|outlook}/callback`.

## Operations

| Task | Command (in /opt/careerpilot) |
|---|---|
| Status | `docker compose --profile ollama --profile tls ps` |
| Restart | `docker compose --profile ollama --profile tls restart backend` |
| Stop | `docker compose --profile ollama --profile tls down` (volumes are kept) |
| DB backup | `docker compose exec -T postgres pg_dump -U careerpilot careerpilot > backup.sql` |
