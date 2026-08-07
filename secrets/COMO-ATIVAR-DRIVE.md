# Google Drive — conta pessoal (Gmail / Google One)

Conta de serviço **não grava** em “Meu Drive” (cota zero).
Para conta pessoal usamos **OAuth** (você autoriza uma vez; arquivos usam seus 5 TB).

Pasta raiz: https://drive.google.com/drive/folders/1Ab-fynAW0c7Cpx-zjXUIE2xvNCVpWEC9

## Passo a passo

### 1) Tela de consentimento OAuth
1. https://console.cloud.google.com/apis/credentials/consent?project=semiotic-bloom-455618-d0
2. Tipo **Externo**
3. Em **Usuários de teste**, adicione: `teko94@gmail.com`
4. Escopos: pode deixar básico; o script pede `drive`

### 2) Criar cliente OAuth (Desktop)
1. https://console.cloud.google.com/apis/credentials?project=semiotic-bloom-455618-d0
2. **Criar credenciais → ID do cliente OAuth**
3. Tipo: **Aplicativo para computador**
4. Baixe o JSON e salve no servidor como:

```text
/home/tm/torqmind-ops-saas/secrets/gdrive-oauth-client.json
```

(pode arrastar no Cursor para a pasta `secrets/`)

### 3) Autorizar e ativar

```bash
cd /home/tm/torqmind-ops-saas
chmod +x scripts/gdrive-oauth-setup.sh
./scripts/gdrive-oauth-setup.sh
```

Abra o link, entre com **teko94@gmail.com**, aceite, cole o código.
O script gera `gdrive-oauth-token.json` e reinicia só o backend ops-saas.

### 4) Teste
Depois diga “autorizado” — eu gravo `teste-torqmind-ops.txt` na pasta Task-Ops.
