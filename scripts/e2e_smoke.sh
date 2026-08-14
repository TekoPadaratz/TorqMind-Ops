#!/usr/bin/env bash
# E2E smoke completo: auth, RBAC, validações, segurança e notificações.
# Uso: BASE=http://localhost:88 ./scripts/e2e_smoke.sh
set -u

BASE="${BASE:-http://localhost:88}"
MASTER_USER="${MASTER_USER:?Informe MASTER_USER}"
MASTER_PASSWORD="${MASTER_PASSWORD:?Informe MASTER_PASSWORD}"
RID=$(date +%s)
MGR_USER="mgr_$RID"
OWNER_USER="owner_$RID"
OPOWN_USER="opowner_$RID"
PASS=0
FAIL=0

jqget() { python3 -c "import sys,json;d=json.load(sys.stdin);print(d$1)"; }

# req METHOD PATH TOKEN JSON  -> escreve status em $STATUS e corpo em $BODY
req() {
  local method="$1" path="$2" token="$3" data="${4:-}"
  local args=(-sS -o /tmp/e2e_body -w "%{http_code}" -X "$method" "$BASE$path")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  if [ -n "$data" ]; then args+=(-H "Content-Type: application/json" -d "$data"); fi
  STATUS=$(curl "${args[@]}")
  BODY=$(cat /tmp/e2e_body)
}

check() {
  local label="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    echo "PASS | $label (HTTP $actual)"; PASS=$((PASS+1))
  else
    echo "FAIL | $label -> esperado $expected, obtido $actual | body: $BODY"; FAIL=$((FAIL+1))
  fi
}

# upload multipart -> escreve status em $STATUS e corpo em $BODY
reqfile() {
  local path="$1" token="$2" file="$3"
  STATUS=$(curl -sS -o /tmp/e2e_body -w "%{http_code}" -X POST "$BASE$path" \
    -H "Authorization: Bearer $token" -F "file=@$file;type=image/png")
  BODY=$(cat /tmp/e2e_body)
}

# PNG 1x1 valido para testes de anexo
python3 -c "import base64;open('/tmp/e2e.png','wb').write(base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=='))"

login() {
  req POST /api/auth/login "" "{\"username\":\"$1\",\"password\":\"$2\"}"
  echo "$BODY" | jqget "['token']" 2>/dev/null || echo ""
}

echo "===================== AUTH ====================="
TEKO=$(login "$MASTER_USER" "$MASTER_PASSWORD")
[ -n "$TEKO" ] && { echo "PASS | login MASTER"; PASS=$((PASS+1)); } || { echo "FAIL | login MASTER"; FAIL=$((FAIL+1)); }

echo "===================== CADASTRO (MASTER) ====================="
req POST /api/admin/companies "$TEKO" '{"name":"Rede Teste E2E"}'
check "MASTER cria empresa" 200 "$STATUS"
COMPANY_ID=$(echo "$BODY" | jqget "['id']")

req POST /api/admin/branches "$TEKO" "{\"companyId\":$COMPANY_ID,\"name\":\"Filial E2E\"}"
check "MASTER cria filial" 200 "$STATUS"
BRANCH_ID=$(echo "$BODY" | jqget "['id']")

req POST /api/admin/sectors "$TEKO" "{\"companyId\":$COMPANY_ID,\"name\":\"Pista E2E\"}"
check "MASTER cria setor" 200 "$STATUS"

req POST /api/admin/users "$TEKO" "{\"username\":\"$MGR_USER\",\"fullName\":\"Gerente E2E\",\"role\":\"MANAGER\",\"password\":\"Manager@123\",\"companyId\":$COMPANY_ID,\"branchId\":$BRANCH_ID}"
check "MASTER cria MANAGER" 200 "$STATUS"

req POST /api/admin/users "$TEKO" "{\"username\":\"$OWNER_USER\",\"fullName\":\"Dono E2E\",\"role\":\"OWNER\",\"password\":\"Owner@1234\",\"companyId\":$COMPANY_ID}"
check "MASTER cria OWNER" 200 "$STATUS"
LUCAS=$(login "$OWNER_USER" 'Owner@1234')
LUCAS_ID=""
req GET /api/admin/users "$TEKO"
LUCAS_ID=$(echo "$BODY" | python3 -c "import sys,json;print(next((u['id'] for u in json.load(sys.stdin) if u['username']=='$OWNER_USER'),''))")

echo "===================== RBAC (MANAGER negado) ====================="
MGR=$(login "$MGR_USER" 'Manager@123')
req POST /api/admin/companies "$MGR" '{"name":"Hacker Co"}'
check "MANAGER NAO cria empresa" 403 "$STATUS"
req POST /api/admin/users "$MGR" '{"username":"x_mgr","fullName":"X","role":"OPERATOR","password":"Xyz@1234"}'
check "MANAGER NAO cria usuario" 403 "$STATUS"
req POST /api/admin/sectors "$MGR" "{\"companyId\":$COMPANY_ID,\"name\":\"Zzz\"}"
check "MANAGER NAO cria setor" 403 "$STATUS"

echo "===================== RBAC (OWNER) ====================="
OWNER=$(login "$OWNER_USER" 'Owner@1234')
req POST /api/admin/sectors "$OWNER" "{\"companyId\":$COMPANY_ID,\"name\":\"Setor do Dono\"}"
check "OWNER NAO cria setor" 403 "$STATUS"
req POST /api/admin/users "$OWNER" "{\"username\":\"$OPOWN_USER\",\"fullName\":\"Operador\",\"role\":\"OPERATOR\",\"password\":\"Oper@1234\"}"
check "OWNER NAO cria OPERATOR" 403 "$STATUS"
req POST /api/admin/companies "$OWNER" '{"name":"Owner Co"}'
check "OWNER NAO cria empresa" 403 "$STATUS"
req POST /api/admin/branches "$OWNER" "{\"companyId\":$COMPANY_ID,\"name\":\"F\"}"
check "OWNER NAO cria filial" 403 "$STATUS"
req POST /api/admin/users "$OWNER" '{"username":"m_by_owner","fullName":"M","role":"MASTER","password":"Master@123"}'
check "OWNER NAO cria MASTER (regra de papel)" 403 "$STATUS"

echo "===================== VALIDACOES ====================="
req POST /api/admin/users "$TEKO" '{"username":"weakpass","fullName":"Weak","role":"OPERATOR","password":"123"}'
check "senha fraca rejeitada" 422 "$STATUS"
req POST /api/admin/users "$TEKO" '{"username":"lucas","fullName":"Dup","role":"OPERATOR","password":"Dup@1234"}'
check "username duplicado rejeitado" 422 "$STATUS"
req POST /api/admin/users "$TEKO" '{"username":"NoFields"}'
check "payload incompleto rejeitado" 422 "$STATUS"
req POST /api/admin/users "$TEKO" '{"username":"badrole","fullName":"BR","role":"HACKER","password":"Good@1234"}'
check "papel invalido rejeitado" 422 "$STATUS"

echo "===================== SEGURANCA ====================="
req GET /api/admin/users "" ""
check "sem token -> 401" 401 "$STATUS"
req GET /api/admin/users "abc.invalid.token" ""
check "token invalido -> 401" 401 "$STATUS"
req POST /api/auth/login "" "{\"username\":\"' OR '1'='1\",\"password\":\"x\"}"
check "SQL injection no login -> 401 (parametrizado)" 401 "$STATUS"
req GET /api/admin/users "$TEKO"
if echo "$BODY" | grep -qi 'password'; then
  echo "FAIL | resposta de usuarios NAO deve conter senha/hash"; FAIL=$((FAIL+1))
else
  echo "PASS | resposta de usuarios sem senha/hash"; PASS=$((PASS+1))
fi

echo "===================== NOTIFICACOES (regra: nao auto-notificar) ====================="
req POST /api/routines/templates "$TEKO" "{\"companyId\":$COMPANY_ID,\"title\":\"Checklist de abertura\",\"recurrenceRule\":\"DAILY\",\"requiresComment\":true}"
check "criar template rotina" 200 "$STATUS"
TPL_ID=$(echo "$BODY" | jqget "['id']")

req POST /api/routines/runs "$TEKO" "{\"templateId\":$TPL_ID,\"assignedUserId\":\"$LUCAS_ID\",\"scheduledFor\":\"2026-08-03T10:00:00Z\",\"dueAt\":\"2026-08-04T10:00:00Z\"}"
check "gerar execucao atribuida a Lucas" 200 "$STATUS"
RUN_ID=$(echo "$BODY" | jqget "['id']")

# Lucas recebe "Nova tarefa"
req GET /api/notifications "$LUCAS"
LUCAS_NEW=$(echo "$BODY" | python3 -c "import sys,json;print(sum(1 for n in json.load(sys.stdin) if n['title']=='Nova tarefa'))")
[ "${LUCAS_NEW:-0}" -ge 1 ] && { echo "PASS | Lucas recebeu 'Nova tarefa'"; PASS=$((PASS+1)); } || { echo "FAIL | Lucas NAO recebeu 'Nova tarefa'"; FAIL=$((FAIL+1)); }

# teko (autor) NAO recebe auto-notificacao dessa acao
req GET /api/notifications "$TEKO"
TEKO_SELF=$(echo "$BODY" | python3 -c "import sys,json;print(sum(1 for n in json.load(sys.stdin) if n['title']=='Nova tarefa'))")
[ "${TEKO_SELF:-0}" -eq 0 ] && { echo "PASS | teko NAO recebeu auto-notificacao"; PASS=$((PASS+1)); } || { echo "FAIL | teko recebeu auto-notificacao indevida ($TEKO_SELF)"; FAIL=$((FAIL+1)); }

# Lucas inicia e conclui (com comentario) -> autor teko recebe "Rotina atualizada"
req POST "/api/routines/runs/$RUN_ID/transition" "$LUCAS" '{"status":"EM_ANDAMENTO"}'
check "Lucas inicia rotina" 200 "$STATUS"
req POST "/api/routines/runs/$RUN_ID/transition" "$LUCAS" '{"status":"CONCLUIDA","comment":"Bombas aferidas e conferidas."}'
check "Lucas conclui rotina (com comentario)" 200 "$STATUS"

req GET /api/notifications "$TEKO"
TEKO_UPD=$(echo "$BODY" | python3 -c "import sys,json;print(sum(1 for n in json.load(sys.stdin) if n['title']=='Rotina atualizada'))")
[ "${TEKO_UPD:-0}" -ge 1 ] && { echo "PASS | teko (dono) notificado da conclusao"; PASS=$((PASS+1)); } || { echo "FAIL | teko NAO notificado da conclusao"; FAIL=$((FAIL+1)); }

# Conclusao sem comentario em rotina que exige -> 422
req POST /api/routines/runs "$TEKO" "{\"templateId\":$TPL_ID,\"assignedUserId\":\"$LUCAS_ID\",\"scheduledFor\":\"2026-08-03T10:00:00Z\",\"dueAt\":\"2026-08-04T10:00:00Z\"}"
RUN2=$(echo "$BODY" | jqget "['id']")
req POST "/api/routines/runs/$RUN2/transition" "$LUCAS" '{"status":"EM_ANDAMENTO"}'
req POST "/api/routines/runs/$RUN2/transition" "$LUCAS" '{"status":"CONCLUIDA"}'
check "conclusao sem comentario obrigatorio -> 422" 422 "$STATUS"

# Transicao invalida -> 422
req POST "/api/routines/runs/$RUN_ID/transition" "$LUCAS" '{"status":"PENDENTE"}'
check "transicao invalida de rotina -> 422" 422 "$STATUS"

echo "===================== NOTIFICACOES (ocorrencias) ====================="
req POST /api/occurrences "$TEKO" "{\"companyId\":$COMPANY_ID,\"title\":\"Equipamento com falha\",\"description\":\"Equipamento nao liga.\",\"priority\":\"ALTA\",\"assigneeUserId\":\"$LUCAS_ID\"}"
check "abrir ocorrencia atribuida a Lucas" 200 "$STATUS"
OCC_ID=$(echo "$BODY" | jqget "['id']")
req GET /api/notifications "$LUCAS"
LUCAS_OCC=$(echo "$BODY" | python3 -c "import sys,json;print(sum(1 for n in json.load(sys.stdin) if n['title']=='Nova ocorrência'))")
[ "${LUCAS_OCC:-0}" -ge 1 ] && { echo "PASS | Lucas notificado de nova ocorrencia"; PASS=$((PASS+1)); } || { echo "FAIL | Lucas NAO notificado de nova ocorrencia"; FAIL=$((FAIL+1)); }

req POST "/api/occurrences/$OCC_ID/transition" "$LUCAS" '{"status":"EM_ATENDIMENTO"}'
check "Lucas atende ocorrencia" 200 "$STATUS"
req GET /api/notifications "$TEKO"
TEKO_OCC=$(echo "$BODY" | python3 -c "import sys,json;print(sum(1 for n in json.load(sys.stdin) if n['title']=='Ocorrência atualizada'))")
[ "${TEKO_OCC:-0}" -ge 1 ] && { echo "PASS | teko (abriu) notificado do atendimento"; PASS=$((PASS+1)); } || { echo "FAIL | teko NAO notificado do atendimento"; FAIL=$((FAIL+1)); }

echo "===================== DETALHE / EVIDENCIAS / ANEXOS ====================="
req POST /api/routines/templates "$TEKO" "{\"companyId\":$COMPANY_ID,\"title\":\"Checklist com foto\",\"recurrenceRule\":\"DAILY\",\"requiresPhoto\":true}"
check "criar template exige-foto" 200 "$STATUS"
PTPL=$(echo "$BODY" | jqget "['id']")
req POST /api/routines/runs "$TEKO" "{\"templateId\":$PTPL,\"assignedUserId\":\"$LUCAS_ID\",\"scheduledFor\":\"2026-08-03T10:00:00Z\",\"dueAt\":\"2026-08-04T10:00:00Z\"}"
PRUN=$(echo "$BODY" | jqget "['id']")
req POST "/api/routines/runs/$PRUN/transition" "$LUCAS" '{"status":"EM_ANDAMENTO"}'
req POST "/api/routines/runs/$PRUN/transition" "$LUCAS" '{"status":"CONCLUIDA"}'
check "conclusao sem foto obrigatoria -> 422" 422 "$STATUS"
req POST "/api/routines/runs/$PRUN/comments" "$LUCAS" '{"body":"Iniciando, segue evidencia em anexo."}'
check "adicionar comentario (sem mudar status)" 200 "$STATUS"
reqfile "/api/routines/runs/$PRUN/attachments" "$LUCAS" /tmp/e2e.png
check "upload de foto (evidencia)" 200 "$STATUS"
ATT=$(echo "$BODY" | jqget "['id']")
req GET "/api/attachments/$ATT" "$LUCAS"
check "servir/baixar anexo" 200 "$STATUS"
req GET "/api/routines/runs/$PRUN" "$LUCAS"
CN=$(echo "$BODY" | python3 -c "import sys,json;print(len(json.load(sys.stdin)['comments']))")
AN=$(echo "$BODY" | python3 -c "import sys,json;print(len(json.load(sys.stdin)['attachments']))")
ACN=$(echo "$BODY" | python3 -c "import sys,json;print(len(json.load(sys.stdin)['activities']))")
{ [ "${CN:-0}" -ge 1 ] && [ "${AN:-0}" -ge 1 ] && [ "${ACN:-0}" -ge 1 ]; } && { echo "PASS | detalhe agrega comentario+anexo+atividades"; PASS=$((PASS+1)); } || { echo "FAIL | detalhe nao agrega (c=$CN a=$AN act=$ACN)"; FAIL=$((FAIL+1)); }
req POST "/api/routines/runs/$PRUN/transition" "$LUCAS" '{"status":"CONCLUIDA","comment":"Concluido com evidencia."}'
check "conclusao COM foto -> 200" 200 "$STATUS"
req POST "/api/routines/runs/$PRUN/comments" "$LUCAS" '{"body":""}'
check "comentario vazio -> 422" 422 "$STATUS"
req DELETE "/api/routines/templates/$PTPL" "$TEKO"
check "MASTER exclui (desativa) template" 200 "$STATUS"
req POST /api/routines/templates "$TEKO" "{\"companyId\":$COMPANY_ID,\"title\":\"Temp delete\",\"recurrenceRule\":\"DAILY\"}"
DTPL=$(echo "$BODY" | jqget "['id']")
req DELETE "/api/routines/templates/$DTPL" "$MGR"
check "MANAGER NAO exclui template -> 403" 403 "$STATUS"

echo "==================================================="
echo "RESULTADO: PASS=$PASS FAIL=$FAIL"
if [ "$FAIL" -eq 0 ]; then
  echo "STATUS FINAL: OK"
  exit 0
fi
echo "STATUS FINAL: FALHAS DETECTADAS"
exit 1
