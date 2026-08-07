#!/usr/bin/env bash
# UAT: cenario real de aceitacao ponta a ponta (rotina + ocorrencia) com rastreabilidade.
# Uso: BASE=http://localhost:88 ./scripts/uat_scenario.sh
set -u
BASE="${BASE:-http://localhost:88}"
PASS=0; FAIL=0
GER_PWD='Gerente@123'; OP_PWD='Operador@123'

jqget() { python3 -c "import sys,json;d=json.load(sys.stdin);print(d$1)"; }
req() {
  local method="$1" path="$2" token="$3" data="${4:-}"
  local args=(-sS -o /tmp/uat_body -w "%{http_code}" -X "$method" "$BASE$path")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$data" ] && args+=(-H "Content-Type: application/json" -d "$data")
  STATUS=$(curl "${args[@]}"); BODY=$(cat /tmp/uat_body)
}
reqfile() {
  STATUS=$(curl -sS -o /tmp/uat_body -w "%{http_code}" -X POST "$BASE$1" -H "Authorization: Bearer $2" -F "file=@$3;type=image/png"); BODY=$(cat /tmp/uat_body)
}
check() {
  if [ "$2" = "$3" ]; then echo "PASS | $1"; PASS=$((PASS+1)); else echo "FAIL | $1 (esperado $2, obtido $3) | $BODY"; FAIL=$((FAIL+1)); fi
}
check2() {
  if [ "$4" = "$2" ] || [ "$4" = "$3" ]; then echo "PASS | $1"; PASS=$((PASS+1)); else echo "FAIL | $1 (obtido $4) | $BODY"; FAIL=$((FAIL+1)); fi
}
login() { req POST /api/auth/login "" "{\"username\":\"$1\",\"password\":\"$2\"}"; echo "$BODY" | jqget "['token']" 2>/dev/null || echo ""; }
notif_count() { req GET /api/notifications "$1"; echo "$BODY" | python3 -c "import sys,json;print(sum(1 for n in json.load(sys.stdin) if n['title']=='$2'))"; }

python3 -c "import base64;open('/tmp/uat.png','wb').write(base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=='))"

echo "===================== SETUP (dono cadastra equipe) ====================="
TEKO=$(login teko '@Crmjr105')
[ -n "$TEKO" ] && { echo "PASS | dono (teko) autenticado"; PASS=$((PASS+1)); } || { echo "FAIL | login teko"; FAIL=$((FAIL+1)); }
req POST /api/admin/users "$TEKO" "{\"username\":\"carlos.gerente\",\"fullName\":\"Carlos Gerente\",\"role\":\"MANAGER\",\"password\":\"$GER_PWD\",\"companyId\":1}"; check2 "cadastrar gerente Carlos" 200 422 "$STATUS"
req POST /api/admin/users "$TEKO" "{\"username\":\"ana.gerente\",\"fullName\":\"Ana Gerente\",\"role\":\"MANAGER\",\"password\":\"$GER_PWD\",\"companyId\":1}"; check2 "cadastrar gerente Ana" 200 422 "$STATUS"
req POST /api/admin/users "$TEKO" "{\"username\":\"roberto.manutencao\",\"fullName\":\"Roberto Manutencao\",\"role\":\"OPERATOR\",\"password\":\"$OP_PWD\",\"companyId\":1}"; check2 "cadastrar operador Roberto" 200 422 "$STATUS"
req GET /api/admin/users "$TEKO"
CARLOS_ID=$(echo "$BODY" | python3 -c "import sys,json;print(next(u['id'] for u in json.load(sys.stdin) if u['username']=='carlos.gerente'))")
ROBERTO_ID=$(echo "$BODY" | python3 -c "import sys,json;print(next(u['id'] for u in json.load(sys.stdin) if u['username']=='roberto.manutencao'))")

echo "===================== ROTINA (afericao com evidencia) ====================="
req POST /api/routines/tasks "$TEKO" '{"companyId":1,"title":"Afericao das bombas","description":"Conferir pressao e vazao das bombas 1 a 4.","recurrence":"DAILY","targetType":"MANAGERS","startTime":"10:00","dueTime":"12:00","reminderBeforeMinutes":30,"requiresPhoto":true,"requiresComment":true}'
check "dono cria rotina diaria p/ gerentes (10:00-12:00, exige foto+comentario)" 200 "$STATUS"
TPL=$(echo "$BODY" | jqget "['id']")
req GET /api/routines/runs "$TEKO"
NRUNS=$(echo "$BODY" | python3 -c "import sys,json;print(sum(1 for r in json.load(sys.stdin) if r['templateId']==$TPL))")
if [ "${NRUNS:-0}" = "0" ]; then
  req POST "/api/routines/templates/$TPL/generate" "$TEKO" '{}'; check "gerar tarefas agora (facilitador)" 200 "$STATUS"
  req GET /api/routines/runs "$TEKO"
  NRUNS=$(echo "$BODY" | python3 -c "import sys,json;print(sum(1 for r in json.load(sys.stdin) if r['templateId']==$TPL))")
else
  echo "INFO | tarefas geradas automaticamente na criacao (horario de inicio ja passou)"
fi
[ "${NRUNS:-0}" = "2" ] && { echo "PASS | 2 tarefas geradas (Carlos e Ana)"; PASS=$((PASS+1)); } || { echo "FAIL | esperado 2 tarefas, obtido $NRUNS"; FAIL=$((FAIL+1)); }
[ "$(notif_count "$TEKO" 'Nova tarefa')" = "0" ] && { echo "PASS | dono NAO recebeu auto-notificacao"; PASS=$((PASS+1)); } || { echo "FAIL | dono recebeu auto-notificacao"; FAIL=$((FAIL+1)); }

CARLOS=$(login carlos.gerente "$GER_PWD")
[ -n "$CARLOS" ] && { echo "PASS | gerente Carlos autenticado"; PASS=$((PASS+1)); } || { echo "FAIL | login Carlos"; FAIL=$((FAIL+1)); }
[ "$(notif_count "$CARLOS" 'Nova tarefa')" -ge 1 ] && { echo "PASS | Carlos recebeu 'Nova tarefa'"; PASS=$((PASS+1)); } || { echo "FAIL | Carlos sem 'Nova tarefa'"; FAIL=$((FAIL+1)); }
req GET /api/routines/runs "$CARLOS"
CRUN=$(echo "$BODY" | python3 -c "import sys,json;rs=[r for r in json.load(sys.stdin) if r['templateId']==$TPL and r['assignedUserId']=='$CARLOS_ID'];print(rs[0]['id'] if rs else '')")
echo "tarefa do Carlos: run #$CRUN"
req GET "/api/routines/runs/$CRUN" "$CARLOS"; check "Carlos abre a tarefa (panorama)" 200 "$STATUS"
req POST "/api/routines/runs/$CRUN/transition" "$CARLOS" '{"status":"EM_ANDAMENTO"}'; check "Carlos inicia a tarefa" 200 "$STATUS"
req POST "/api/routines/runs/$CRUN/transition" "$CARLOS" '{"status":"CONCLUIDA"}'; check "concluir SEM evidencia e bloqueado (exige foto/comentario)" 422 "$STATUS"
req POST "/api/routines/runs/$CRUN/comments" "$CARLOS" '{"body":"Bombas 1 a 4 aferidas, pressao dentro do padrao."}'; check "Carlos adiciona comentario" 200 "$STATUS"
reqfile "/api/routines/runs/$CRUN/attachments" "$CARLOS" /tmp/uat.png; check "Carlos anexa foto (evidencia)" 200 "$STATUS"
req POST "/api/routines/runs/$CRUN/transition" "$CARLOS" '{"status":"CONCLUIDA"}'; check "Carlos conclui (com evidencia)" 200 "$STATUS"
[ "$(notif_count "$TEKO" 'Rotina atualizada')" -ge 1 ] && { echo "PASS | dono notificado da conclusao"; PASS=$((PASS+1)); } || { echo "FAIL | dono nao notificado da conclusao"; FAIL=$((FAIL+1)); }
req GET "/api/routines/runs/$CRUN" "$CARLOS"
CN=$(echo "$BODY" | python3 -c "import sys,json;d=json.load(sys.stdin);print(len(d['comments']))")
AN=$(echo "$BODY" | python3 -c "import sys,json;d=json.load(sys.stdin);print(len(d['attachments']))")
ACN=$(echo "$BODY" | python3 -c "import sys,json;d=json.load(sys.stdin);print(len(d['activities']))")
FS=$(echo "$BODY" | python3 -c "import sys,json;print(json.load(sys.stdin)['summary']['status'])")
{ [ "${CN:-0}" -ge 1 ] && [ "${AN:-0}" -ge 1 ] && [ "${ACN:-0}" -ge 4 ] && [ "$FS" = "CONCLUIDA" ]; } && { echo "PASS | historico rastreavel completo (com=$CN anexo=$AN atividades=$ACN status=$FS)"; PASS=$((PASS+1)); } || { echo "FAIL | historico incompleto (com=$CN anexo=$AN atividades=$ACN status=$FS)"; FAIL=$((FAIL+1)); }

echo "===================== OCORRENCIA (problema reportado) ====================="
req POST /api/occurrences "$CARLOS" "{\"companyId\":1,\"title\":\"Vazamento na conexao da bomba 3\",\"description\":\"Pingando combustivel na base da bomba 3.\",\"priority\":\"ALTA\",\"assigneeUserId\":\"$ROBERTO_ID\"}"
check "Carlos abre ocorrencia p/ manutencao (Roberto)" 200 "$STATUS"
OCC=$(echo "$BODY" | jqget "['id']")
ROBERTO=$(login roberto.manutencao "$OP_PWD")
[ "$(notif_count "$ROBERTO" 'Nova ocorrência')" -ge 1 ] && { echo "PASS | Roberto recebeu 'Nova ocorrencia'"; PASS=$((PASS+1)); } || { echo "FAIL | Roberto sem 'Nova ocorrencia'"; FAIL=$((FAIL+1)); }
req POST "/api/occurrences/$OCC/transition" "$ROBERTO" '{"status":"EM_ATENDIMENTO"}'; check "Roberto assume o atendimento" 200 "$STATUS"
req POST "/api/occurrences/$OCC/comments" "$ROBERTO" '{"body":"Troquei a vedacao da conexao. Sem vazamento agora."}'; check "Roberto registra o servico (comentario)" 200 "$STATUS"
reqfile "/api/occurrences/$OCC/attachments" "$ROBERTO" /tmp/uat.png; check "Roberto anexa foto do reparo" 200 "$STATUS"
req POST "/api/occurrences/$OCC/transition" "$ROBERTO" '{"status":"AGUARDANDO_VALIDACAO"}'; check "Roberto envia p/ validacao" 200 "$STATUS"
[ "$(notif_count "$CARLOS" 'Ocorrência atualizada')" -ge 1 ] && { echo "PASS | Carlos (abriu) notificado p/ validar"; PASS=$((PASS+1)); } || { echo "FAIL | Carlos nao notificado"; FAIL=$((FAIL+1)); }
req POST "/api/occurrences/$OCC/transition" "$CARLOS" '{"status":"ENCERRADA"}'; check "Carlos valida e encerra" 200 "$STATUS"
req GET "/api/occurrences/$OCC" "$CARLOS"
OCN=$(echo "$BODY" | python3 -c "import sys,json;d=json.load(sys.stdin);print(len(d['comments']))")
OAN=$(echo "$BODY" | python3 -c "import sys,json;d=json.load(sys.stdin);print(len(d['attachments']))")
OACN=$(echo "$BODY" | python3 -c "import sys,json;d=json.load(sys.stdin);print(len(d['activities']))")
OFS=$(echo "$BODY" | python3 -c "import sys,json;print(json.load(sys.stdin)['summary']['status'])")
{ [ "${OCN:-0}" -ge 1 ] && [ "${OAN:-0}" -ge 1 ] && [ "${OACN:-0}" -ge 3 ] && [ "$OFS" = "ENCERRADA" ]; } && { echo "PASS | ocorrencia rastreavel e encerrada (com=$OCN anexo=$OAN atividades=$OACN status=$OFS)"; PASS=$((PASS+1)); } || { echo "FAIL | ocorrencia incompleta (com=$OCN anexo=$OAN atividades=$OACN status=$OFS)"; FAIL=$((FAIL+1)); }

echo "==================================================="
echo "RESULTADO UAT: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ] && echo "STATUS FINAL: OK" || echo "STATUS FINAL: FALHAS"
