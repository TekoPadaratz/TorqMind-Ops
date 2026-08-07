#!/usr/bin/env bash
# Ativa Google Drive no torqmind-ops-saas (somente este compose).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JSON="$ROOT/secrets/gdrive-service-account.json"
ENV_FILE="$ROOT/.env"

cd "$ROOT"

if [[ ! -f "$JSON" ]]; then
  echo "Arquivo não encontrado: $JSON"
  echo "Coloque o JSON da conta de serviço nesse caminho e rode de novo."
  exit 1
fi

if ! python3 - "$JSON" <<'PY'
import json, sys
path = sys.argv[1]
with open(path, encoding="utf-8") as f:
    data = json.load(f)
email = data.get("client_email")
if not email or not data.get("private_key"):
    print("JSON inválido: precisa de client_email e private_key.")
    sys.exit(2)
print(email)
PY
then
  exit 2
fi

EMAIL="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1],encoding="utf-8"))["client_email"])' "$JSON")"

# Atualiza .env sem apagar outras chaves
touch "$ENV_FILE"
grep -q '^STORAGE_PROVIDER=' "$ENV_FILE" 2>/dev/null && sed -i 's/^STORAGE_PROVIDER=.*/STORAGE_PROVIDER=gdrive/' "$ENV_FILE" || echo 'STORAGE_PROVIDER=gdrive' >> "$ENV_FILE"
grep -q '^GDRIVE_ROOT_FOLDER_ID=' "$ENV_FILE" 2>/dev/null && sed -i 's/^GDRIVE_ROOT_FOLDER_ID=.*/GDRIVE_ROOT_FOLDER_ID=1Ab-fynAW0c7Cpx-zjXUIE2xvNCVpWEC9/' "$ENV_FILE" || echo 'GDRIVE_ROOT_FOLDER_ID=1Ab-fynAW0c7Cpx-zjXUIE2xvNCVpWEC9' >> "$ENV_FILE"
grep -q '^GDRIVE_CREDENTIALS_FILE=' "$ENV_FILE" 2>/dev/null && sed -i 's|^GDRIVE_CREDENTIALS_FILE=.*|GDRIVE_CREDENTIALS_FILE=/app/secrets/gdrive-service-account.json|' "$ENV_FILE" || echo 'GDRIVE_CREDENTIALS_FILE=/app/secrets/gdrive-service-account.json' >> "$ENV_FILE"

echo ""
echo "Conta de serviço: $EMAIL"
echo "Confirme que a pasta do Drive está compartilhada com esse e-mail (Editor)."
echo "Pasta: https://drive.google.com/drive/folders/1Ab-fynAW0c7Cpx-zjXUIE2xvNCVpWEC9"
echo ""
echo "Reiniciando apenas backend do torqmind-ops-saas..."
docker compose up -d --force-recreate backend
echo "Aguardando health..."
sleep 8
docker compose ps backend
docker compose logs backend --tail 40 | grep -iE 'drive|storage|Started|ERROR|WARN' || true
echo ""
echo "Se aparecer 'Google Drive storage ativo', está ok."
