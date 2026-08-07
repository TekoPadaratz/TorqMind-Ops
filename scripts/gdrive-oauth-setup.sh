#!/usr/bin/env bash
# Gera refresh token OAuth da conta Google PESSOAL (Meu Drive / Google One).
# Uso:
#   1) Coloque o JSON do cliente OAuth (tipo Desktop) em secrets/gdrive-oauth-client.json
#   2) ./scripts/gdrive-oauth-setup.sh
#   3) Abra a URL, autorize com teko94@gmail.com, cole o código
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLIENT="$ROOT/secrets/gdrive-oauth-client.json"
TOKEN="$ROOT/secrets/gdrive-oauth-token.json"
ENV_FILE="$ROOT/.env"

cd "$ROOT"

if [[ ! -f "$CLIENT" ]]; then
  echo "Falta: $CLIENT"
  echo ""
  echo "No Google Cloud Console (mesmo projeto da Drive API):"
  echo "  APIs e serviços → Credenciais → Criar credenciais → ID do cliente OAuth"
  echo "  Tipo: Aplicativo para computador (Desktop)"
  echo "  Baixe o JSON e salve em secrets/gdrive-oauth-client.json"
  echo ""
  echo "Também em 'Tela de consentimento OAuth':"
  echo "  Tipo Externo → adicione seu e-mail como usuário de teste"
  exit 1
fi

echo "Abrindo fluxo OAuth (Docker + Python)..."
docker run --rm -it \
  -v "$ROOT/secrets:/secrets" \
  python:3.12-slim \
  bash -c 'pip install -q google-auth-oauthlib google-auth >/dev/null && python - <<'\''PY'\''
import json
from pathlib import Path
from google_auth_oauthlib.flow import InstalledAppFlow

CLIENT = Path("/secrets/gdrive-oauth-client.json")
TOKEN = Path("/secrets/gdrive-oauth-token.json")
SCOPES = ["https://www.googleapis.com/auth/drive"]

raw = json.loads(CLIENT.read_text(encoding="utf-8"))
# Aceita formato "installed" ou "web"
cfg = raw.get("installed") or raw.get("web") or raw
# Monta dict no formato esperado pela lib
client_config = {"installed": {
    "client_id": cfg["client_id"],
    "client_secret": cfg["client_secret"],
    "auth_uri": cfg.get("auth_uri", "https://accounts.google.com/o/oauth2/auth"),
    "token_uri": cfg.get("token_uri", "https://oauth2.googleapis.com/token"),
    "redirect_uris": cfg.get("redirect_uris", ["http://localhost"]),
}}

flow = InstalledAppFlow.from_client_config(client_config, SCOPES)
print("")
print("=== AUTORIZAÇÃO GOOGLE DRIVE (conta pessoal) ===")
print("1) Abra o link abaixo no navegador")
print("2) Entre com a conta dona do Drive (ex.: teko94@gmail.com)")
print("3) Aceite o acesso")
print("4) Se aparecer um código, cole aqui. Se redirecionar para localhost com erro, copie o ?code= da URL.")
print("")
creds = flow.run_console()

out = {
    "type": "authorized_user",
    "client_id": client_config["installed"]["client_id"],
    "client_secret": client_config["installed"]["client_secret"],
    "refresh_token": creds.refresh_token,
}
if not out["refresh_token"]:
    raise SystemExit("Não veio refresh_token. Revogue o acesso do app e tente de novo.")
TOKEN.write_text(json.dumps(out, indent=2), encoding="utf-8")
TOKEN.chmod(0o600)
print("")
print("Token salvo em /secrets/gdrive-oauth-token.json")
print("Pronto.")
PY'

chmod 600 "$TOKEN" 2>/dev/null || true

# Atualiza .env
touch "$ENV_FILE"
set_env() {
  local key="$1" val="$2"
  if grep -q "^${key}=" "$ENV_FILE" 2>/dev/null; then
    sed -i "s|^${key}=.*|${key}=${val}|" "$ENV_FILE"
  else
    echo "${key}=${val}" >> "$ENV_FILE"
  fi
}
set_env STORAGE_PROVIDER gdrive
set_env GDRIVE_ROOT_FOLDER_ID 1Ab-fynAW0c7Cpx-zjXUIE2xvNCVpWEC9
set_env GDRIVE_OAUTH_TOKEN_FILE /app/secrets/gdrive-oauth-token.json

echo ""
echo "Reiniciando backend ops-saas..."
docker compose up -d --build --force-recreate backend
sleep 10
docker compose logs backend --tail 50 | grep -iE 'drive|storage|Started|ERROR|WARN' || true
echo ""
echo "Se aparecer 'modo=oauth', está usando sua conta pessoal."
