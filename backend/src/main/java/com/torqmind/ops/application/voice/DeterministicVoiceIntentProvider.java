package com.torqmind.ops.application.voice;

import com.torqmind.ops.domain.occurrence.FuelKind;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpretador determinístico em PT-BR para testes e homologação sem LLM.
 * Não inventa IDs; só extrai referências faladas.
 */
public class DeterministicVoiceIntentProvider implements VoiceIntentProvider {

    private final VoiceReadyPhraseCatalog readyPhrases;

    public DeterministicVoiceIntentProvider(VoiceReadyPhraseCatalog readyPhrases) {
        this.readyPhrases = readyPhrases;
    }

    /** Parser regex sem catálogo (testes legados). */
    public DeterministicVoiceIntentProvider() {
        this(null);
    }

    private static final Pattern AFTER_COMMENT = Pattern.compile("coment[aá]rio[:\\s]+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REJECT_REASON = Pattern.compile("porque\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern OCC_DESC = Pattern.compile("informando que\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TASK_TITLE = Pattern.compile("(?:tarefa|rotina) (?:de |d[aeo] )?(.+?)(?: para | amanh| hoje| toda| nos dias| exig| venc|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FOR_USER = Pattern.compile("(?:para|pra|pro)\\s+(?:os?\\s+|as?\\s+)?(?:gerente |funcion[aá]rio |dono )?([^,]+?)(?: de | do | da | amanh| hoje| toda| todas| nos dias| exig| venc|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BRANCH = Pattern.compile("(?:posto |filial (?:de )?)([^,]+?)(?: amanh| hoje| inform| exig|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECTOR = Pattern.compile("setor (?:da |do )?([^,]+?)(?:$|\\.)", Pattern.CASE_INSENSITIVE);
    private static final Pattern START = Pattern.compile("(?:in[ií]cio|come[cç]a|as|às)\\s+(\\d{1,2}(?::\\d{2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DUE = Pattern.compile("venc(?:endo|e|imento)?(?: às| as)?\\s+(\\d{1,2}(?::\\d{2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FAZER = Pattern.compile("\\bfazer\\s+(?:a |o |as |os |um |uma )?(.+?)(?:\\s+para\\b| amanh| hoje| exig| venc|\\.|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REMINDER = Pattern.compile("lembrete (?:de )?(\\d{1,3})\\s*minut", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUERY_TITLE = Pattern.compile("(?:rotina|tarefa|demanda|ocorrência|serviço) (?:de |da |do )?(.+?)(?: hoje| ontem| foi | esta| está| ja | já | conclu| execut| termin| finaliz|\\?|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUERY_ASSIGNEE = Pattern.compile("\\b(?:o |a )?([A-Za-zÀ-ÿ]+)\\s+(?:executou|concluiu|finalizou|terminou|iniciou|realizou|cumpriu|fez)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_TITLE = Pattern.compile("(?:rotina|tarefa|demanda|ocorrência|serviço) (?:de |da |do )?(.+?)(?: do | da | hoje| ontem| exig|\\?|\\.|$)", Pattern.CASE_INSENSITIVE);

    @Override
    public String name() {
        return "deterministic";
    }

    @Override
    public VoiceIntent interpret(String transcript, VoiceContext context) {
        VoiceIntent intent = new VoiceIntent();
        intent.setSchemaVersion(VoiceIntent.SCHEMA_VERSION);
        intent.setTranscript(transcript);
        intent.setRequiresConfirmation(false);
        intent.setConfidence(0.7);
        String t = transcript == null ? "" : transcript.trim();
        String low = t.toLowerCase(Locale.ROOT);

        if (looksLikeInjection(low)) {
            intent.getWarnings().add("Trechos de instrução foram ignorados.");
        }

        if (readyPhrases != null) {
            var ready = readyPhrases.match(t);
            if (ready.isPresent()) {
                VoiceIntent matched = ready.get();
                intent.setAction(matched.getAction());
                intent.setTitle(matched.getTitle());
                intent.setTaskReference(matched.getTaskReference());
                intent.setComment(matched.getComment());
                intent.setFuel(matched.getFuel());
                intent.setTargetType(matched.getTargetType());
                intent.setRecurrence(matched.getRecurrence());
                intent.setRequestedStatus(matched.getRequestedStatus());
                intent.setOccurrencePriority(matched.getOccurrencePriority());
                intent.setBranchReference(matched.getBranchReference());
                intent.setTargetUserReference(matched.getTargetUserReference());
                intent.setRequiresConfirmation(matched.getRequiresConfirmation());
                intent.setConfidence(matched.getConfidence());
                return intent;
            }
        }

        if (looksLikeAdmin(low)) {
            intent.setAction("ADMIN_DENIED");
            intent.setRequiresConfirmation(false);
            intent.setConfidence(0.98);
        } else if (looksLikeHelp(low)) {
            intent.setAction("HELP");
            intent.setRequiresConfirmation(false);
            intent.setConfidence(0.95);
        } else if (looksLikeNotifications(low)) {
            intent.setAction("OPEN_NOTIFICATIONS");
            intent.setRequiresConfirmation(false);
            intent.setConfidence(0.92);
        } else if (looksLikeSummaryToday(low)) {
            intent.setAction("SUMMARY_TODAY");
            intent.setRequiresConfirmation(false);
            intent.setConfidence(0.92);
        } else if (looksLikeMyTasks(low)) {
            intent.setAction("LIST_MY_TASKS");
            intent.setRequiresConfirmation(false);
            if (low.contains("atrasad")) {
                intent.setRequestedStatus("ATRASADA");
            } else if (low.contains("hoje")) {
                intent.setRequestedStatus("HOJE");
            } else {
                intent.setRequestedStatus("PENDENTE");
            }
            intent.setConfidence(0.9);
        } else if (looksLikeStatusQuery(low)) {
            fillStatusQuery(intent, t, low);
        } else if (looksLikeListQuery(low)) {
            fillListQuery(intent, low);
        } else if (looksLikeDelete(low)) {
            fillDelete(intent, t);
        } else if (looksLikeCloseOccurrence(low)) {
            intent.setAction("CLOSE_OCCURRENCE");
            fillOccurrenceRef(intent, t, context);
        } else if (looksLikeStartOccurrence(low)) {
            intent.setAction("START_OCCURRENCE");
            fillOccurrenceRef(intent, t, context);
        } else if (looksLikeQualityAnalysis(low)) {
            fillQualityAnalysis(intent, low);
        } else if (mentionsOccurrence(low)) {
            fillOccurrence(intent, t, low);
        } else if (wantsCreateTask(low)) {
            fillCreateTask(intent, t, low);
        } else if (containsAny(low, "inicie", "iniciar", "comece", "começar", "inicia ")) {
            if (mentionsOccurrence(low) || low.contains("chamado")) {
                intent.setAction("START_OCCURRENCE");
                fillOccurrenceRef(intent, t, context);
            } else {
                intent.setAction("START_TASK");
                fillTaskRef(intent, t, context);
            }
        } else if (containsAny(low, "conclua", "concluir", "finalize", "finalizar", "finaliza ", "marca como feita", "marca como feito", "dar baixa")) {
            intent.setAction("COMPLETE_TASK");
            fillTaskRef(intent, t, context);
        } else if (containsAny(low, "rejeite", "rejeitar")) {
            intent.setAction("REJECT_TASK");
            intent.setRequiresConfirmation(true);
            fillTaskRef(intent, t, context);
            Matcher why = REJECT_REASON.matcher(t);
            if (why.find()) {
                intent.setComment(why.group(1).trim());
            }
        } else if (containsAny(low, "adicione o coment", "adicionar coment", "comente")) {
            intent.setAction("ADD_COMMENT");
            fillTaskRef(intent, t, context);
            Matcher c = AFTER_COMMENT.matcher(t);
            if (c.find()) {
                intent.setComment(c.group(1).trim());
            } else {
                int idx = low.indexOf(":");
                if (idx > 0) {
                    intent.setComment(t.substring(idx + 1).trim());
                }
            }
        } else if (containsAny(low, "mostre", "liste", "o que esta pendente", "o que está pendente", "o que falta", "resumo", "pendentes", "atrasadas", "situacao", "situação")) {
            intent.setAction("LIST_TASKS");
            if (low.contains("atrasad")) {
                intent.setRequestedStatus("ATRASADA");
            } else if (low.contains("pendent")) {
                intent.setRequestedStatus("PENDENTE");
            } else if (low.contains("hoje")) {
                intent.setRequestedStatus("HOJE");
            }
        } else if (containsAny(low, "abra a tarefa", "abrir tarefa", "abre a tarefa", "abra o chamado", "abrir chamado", "abre o checklist", "abrir checklist", "abrir servico", "abrir serviço")) {
            intent.setAction("OPEN_TASK");
            fillTaskRef(intent, t, context);
        } else if (containsAny(low, "abra uma ocorr", "abrir ocorr", "abrir chamado", "nova ocorr", "novo chamado")) {
            if (looksLikeQualityAnalysis(low)) {
                fillQualityAnalysis(intent, low);
            } else {
                fillOccurrence(intent, t, low);
            }
        } else {
            intent.setAction("HELP");
            intent.getWarnings().add("Não identifiquei o comando; vou explicar o que posso fazer.");
            intent.setConfidence(0.35);
        }
        return intent;
    }

    static boolean looksLikeAdmin(String low) {
        return containsAny(low,
                "criar usuario", "criar usuário", "cadastrar usuario", "cadastrar usuário",
                "novo usuario", "novo usuário", "cadastro de usuario", "cadastro de usuário",
                "criar filial", "cadastrar filial", "nova filial", "criar empresa", "cadastrar empresa",
                "nova empresa", "api key", "chave de api", "webhook", "gestao de", "gestão de",
                "menu gestao", "menu gestão", "administrar sistema", "trocar senha de", "resetar senha",
                "alterar permissao", "alterar permissão", "promover usuario", "promover usuário");
    }

    private static boolean looksLikeNotifications(String low) {
        return containsAny(low,
                "avisos", "notificac", "notificaç", "tem aviso", "tem notific", "alertas novos",
                "mensagens novas", "o que chegou");
    }

    private static boolean looksLikeSummaryToday(String low) {
        return containsAny(low,
                "como esta a operacao", "como está a operação", "resumo do dia", "panorama do dia",
                "situacao de hoje", "situação de hoje", "como estamos hoje", "status da operacao",
                "status da operação", "resumo operacional", "como ta o posto", "como tá o posto",
                "mapa da operacao", "mapa da operação", "situacao geral", "situação geral",
                "panorama da filial", "status geral");
    }

    private static boolean looksLikeMyTasks(String low) {
        return containsAny(low,
                "minhas tarefas", "minhas rotinas", "minhas pendencias", "minhas pendências",
                "o que tenho pra fazer", "o que tenho para fazer", "meu servico", "meu serviço",
                "meus checklists", "minha agenda de hoje", "minha fila", "meu trabalho de hoje",
                "o que ficou pra mim");
    }

    private static boolean looksLikeStartOccurrence(String low) {
        return (mentionsOccurrence(low) || low.contains("chamado"))
                && containsAny(low, "assuma", "assumir", "atender", "pegar", "iniciar atendimento", "comece o atendimento");
    }

    private static boolean looksLikeCloseOccurrence(String low) {
        return (mentionsOccurrence(low) || low.contains("chamado"))
                && containsAny(low, "encerr", "finaliz", "fechar", "concluir o chamado",
                "concluir a ocorrencia", "concluir a ocorrência", "dar baixa no chamado", "dar baixa");
    }

    private static void fillOccurrenceRef(VoiceIntent intent, String t, VoiceContext context) {
        if (context != null && context.getCurrentTaskId() != null
                && "OCCURRENCE".equalsIgnoreCase(context.getCurrentTaskType())) {
            intent.setTaskReference("current");
            if (context.getCurrentTaskTitle() != null) {
                intent.setTitle(context.getCurrentTaskTitle());
            }
            return;
        }
        Matcher title = QUERY_TITLE.matcher(t);
        if (title.find() && !title.group(1).isBlank()) {
            intent.setTaskReference(title.group(1).trim());
            intent.setTitle(capitalize(title.group(1).trim()));
        } else {
            intent.setTaskReference(t);
        }
    }

    private static void fillCreateTask(VoiceIntent intent, String t, String low) {
        intent.setAction("CREATE_TASK");
        intent.setRecurrence("ONCE");
        boolean weeklyByWeekday = VoiceDateTimeNormalizer.weekdayIso(low) != null
                && (low.contains("toda ") || low.contains("todas ") || low.contains("toda semana") || low.contains("semanal"));
        if (weeklyByWeekday || low.contains("toda segunda") || low.contains("todas as segundas") || low.contains("semanal")) {
            intent.setRecurrence("WEEKLY");
        } else if (low.contains("todo dia") || low.contains("diari")) {
            intent.setRecurrence("DAILY");
        } else if (low.contains("todo mes") || low.contains("todo mês") || low.contains("mensal")) {
            intent.setRecurrence("MONTHLY");
        } else if (low.contains("nos dias") || low.contains("dias ")) {
            intent.setRecurrence("CUSTOM");
        }

        Matcher fazer = FAZER.matcher(t);
        Matcher title = TASK_TITLE.matcher(t);
        if (fazer.find() && !fazer.group(1).isBlank()) {
            intent.setTitle(capitalize(fazer.group(1).trim()));
        } else if (title.find()) {
            intent.setTitle(capitalize(title.group(1).trim()));
        } else if (low.contains("extintor")) {
            intent.setTitle("Conferir os extintores");
        } else if (low.contains("estoque")) {
            intent.setTitle("Conferência do estoque");
        } else if (low.contains("aferi")) {
            intent.setTitle("Aferição das bombas");
        } else if (low.contains("caixa")) {
            intent.setTitle("Fechamento de caixa");
        } else if (low.contains("pista")) {
            intent.setTitle("Limpeza da pista");
        } else if (low.contains("bomba")) {
            intent.setTitle("Conferência de bomba");
        } else if (low.contains("tanque")) {
            intent.setTitle("Medição de tanque");
        } else if (low.contains("troco")) {
            intent.setTitle("Conferência de troco");
        } else if (low.contains("lavagem")) {
            intent.setTitle("Lavagem da área de abastecimento");
        }

        if (low.contains("setor")) {
            intent.setTargetType("SECTOR");
            Matcher s = SECTOR.matcher(t);
            if (s.find()) {
                intent.setTargetSectorReference(s.group(1).trim());
            }
        } else if (containsAny(low, "todos os funcionarios", "todos os funcionários", "para todos")) {
            intent.setTargetType("ALL");
        } else if (containsAny(low, "gerentes", "os gerentes")) {
            intent.setTargetType("MANAGERS");
        } else if (containsAny(low, "gerente", "funcionario", "funcionário", "para o ")) {
            intent.setTargetType("USER");
            Matcher u = FOR_USER.matcher(t);
            if (u.find()) {
                intent.setTargetUserReference(u.group(1).trim());
            }
        } else {
            intent.setTargetType("MANAGERS");
        }

        Matcher br = BRANCH.matcher(t);
        if (br.find()) {
            String name = br.group(1).trim();
            intent.setBranchReference(name);
            if (low.contains("sorocaba") || name.toLowerCase(Locale.ROOT).contains("sorocaba")) {
                intent.setCityReference("Sorocaba");
            }
        }

        LocalDate today = LocalDate.now(VoiceDateTimeNormalizer.ZONE);
        if (low.contains("amanh")) {
            intent.setScheduledDate(today.plusDays(1).toString());
        } else if (low.contains("hoje")) {
            intent.setScheduledDate(today.toString());
        }
        // Rotinas recorrentes (WEEKLY/DAILY/MONTHLY/CUSTOM) nao tem data unica: a regra de recorrencia define as datas.

        List<String> times = extractTimes(low);
        java.util.List<Integer> named = VoiceDateTimeNormalizer.namedHoursInOrder(low);
        if (intent.getStartTime() == null && !named.isEmpty()) {
            intent.setStartTime(String.format("%02d:00", named.get(0)));
        }
        if (intent.getStartTime() == null && !times.isEmpty()) {
            intent.setStartTime(toHm(times.get(0)));
        }
        Matcher due = DUE.matcher(t);
        if (due.find()) {
            intent.setDueTime(toHm(due.group(1)));
        } else if (named.size() >= 2) {
            intent.setDueTime(String.format("%02d:00", named.get(1)));
        } else if (times.size() >= 2) {
            intent.setDueTime(toHm(times.get(1)));
        }

        if (containsAny(low, "sem foto", "nao precisa de foto", "não precisa de foto", "sem exigir foto")) {
            intent.setRequiresPhoto(false);
        } else if (containsAny(low, "foto", "fotografia")) {
            intent.setRequiresPhoto(true);
        }
        if (containsAny(low, "sem comentario", "sem comentário", "nao precisa de comentario", "não precisa de comentário")) {
            intent.setRequiresComment(false);
        } else if (low.contains("coment")) {
            intent.setRequiresComment(true);
        }
        Matcher rem = REMINDER.matcher(t);
        if (rem.find()) {
            intent.setReminderBeforeMinutes(Integer.parseInt(rem.group(1)));
        }
        if (intent.getDueTime() == null && intent.getStartTime() != null) {
            intent.setDueTime(intent.getStartTime());
        }
        if ("ONCE".equals(intent.getRecurrence()) && intent.getScheduledDate() == null) {
            intent.setScheduledDate(today.toString());
        }
        if ("CUSTOM".equals(intent.getRecurrence())) {
            List<Integer> days = VoiceDateTimeNormalizer.customDaysFromSpeech(t);
            if (!days.isEmpty()) {
                intent.setDescription("Dias: " + days);
            }
        }
    }

    private static void fillQualityAnalysis(VoiceIntent intent, String low) {
        intent.setAction("OPEN_QUALITY_ANALYSIS");
        intent.setTitle("Análise de qualidade no recebimento de combustível");
        FuelKind fuel = FuelKind.fromSpeech(low);
        if (fuel != null) {
            intent.setFuel(fuel.name());
        }
    }

    private static boolean looksLikeHelp(String low) {
        return containsAny(low,
                "o que voce faz", "o que você faz", "o que posso pedir", "o que posso falar",
                "o que voce sabe", "o que você sabe", "suas capacidades", "me ajuda", "ajuda",
                "como funciona", "o que consigo fazer", "quais comandos", "lista de comandos",
                "me explica", "quem e voce", "quem é você");
    }

    private static boolean looksLikeStatusQuery(String low) {
        return containsAny(low, "executou", "concluiu", "finalizou", "terminou", "realizou", "cumpriu",
                "ja fez", "já fez", "foi feito", "foi feita", "foi concluida", "foi concluída",
                "qual o status", "qual status", "status da", "status do",
                "verificar se", "conferir se", "checar se");
    }

    private static void fillStatusQuery(VoiceIntent intent, String t, String low) {
        intent.setAction("QUERY_TASK");
        Matcher who = QUERY_ASSIGNEE.matcher(t);
        if (who.find()) {
            intent.setTargetUserReference(who.group(1).trim());
        }
        Matcher title = QUERY_TITLE.matcher(t);
        if (title.find() && !title.group(1).isBlank()) {
            intent.setTaskReference(title.group(1).trim());
            intent.setTitle(capitalize(title.group(1).trim()));
        }
        LocalDate today = LocalDate.now(VoiceDateTimeNormalizer.ZONE);
        if (low.contains("ontem")) {
            intent.setScheduledDate(today.minusDays(1).toString());
        } else {
            intent.setScheduledDate(today.toString());
        }
    }

    private static boolean mentionsOccurrence(String low) {
        return low.contains("ocorrencia") || low.contains("ocorrência") || low.contains("chamado");
    }

    // Consulta/checagem ("verifique se tem ocorrencias pendentes", "quais tarefas atrasadas") -> listar, nunca criar.
    private static boolean looksLikeListQuery(String low) {
        boolean cue = containsAny(low,
                "verifique", "verificar", "confira", "conferir", "cheque", "checar",
                "mostre", "mostra", "mostrar", "liste", "listar", "quais", "quantas", "quantos",
                "tem alguma", "tem algum", "há alguma", "ha alguma", "existe", "existem",
                "o que tem", "o que esta", "o que está", "o que falta", "quero ver", "ver as", "veja",
                "tem ocorr", "tem tarefa", "tem rotina");
        boolean noun = containsAny(low,
                "ocorrencia", "ocorrência", "chamado", "pendente", "aberta", "aberto", "atrasada", "atrasado",
                "tarefa", "rotina", "checklist", "servico", "serviço");
        boolean create = containsAny(low,
                "criar", "crie", "cadastr", "registrar", "registre", "abrir uma", "abra uma",
                "abre uma", "nova ", "novo ", "informando que");
        return cue && noun && !create;
    }

    private static void fillListQuery(VoiceIntent intent, String low) {
        if (mentionsOccurrence(low)) {
            intent.setAction("LIST_OCCURRENCES");
            if (containsAny(low, "aberta", "abertas", "aberto", "pendente", "pendentes", "em aberto")) {
                intent.setRequestedStatus("ABERTA");
            }
            return;
        }
        intent.setAction("LIST_TASKS");
        if (low.contains("atrasad")) {
            intent.setRequestedStatus("ATRASADA");
        } else if (low.contains("pendent")) {
            intent.setRequestedStatus("PENDENTE");
        } else if (low.contains("hoje")) {
            intent.setRequestedStatus("HOJE");
        }
    }

    private static boolean looksLikeDelete(String low) {
        return containsAny(low, "exclua", "excluir", "apague", "apagar", "delete", "deletar", "remova", "remover");
    }

    private static void fillDelete(VoiceIntent intent, String t) {
        intent.setAction("DELETE_TASK");
        String low = t.toLowerCase(Locale.ROOT);
        boolean bulk = containsAny(low, "todas", "todos", "tudo");
        intent.setRequiresConfirmation(!bulk);
        if (bulk) {
            intent.getWarnings().add("Exclusão em massa não é permitida por voz.");
            return;
        }
        Matcher title = DELETE_TITLE.matcher(t);
        if (title.find() && !title.group(1).isBlank()) {
            intent.setTaskReference(title.group(1).trim());
            intent.setTitle(capitalize(title.group(1).trim()));
        }
    }

    private static boolean looksLikeQualityAnalysis(String low) {
        if (low.contains("combust")
                && containsAny(low, "testagem", "testando", "testar", "teste de", "teste do")) {
            return true;
        }
        return containsAny(low,
                "ocorrência de registro de análise",
                "ocorrencia de registro de analise",
                "registro de análise",
                "registro de analise",
                "análise de qualidade",
                "analise de qualidade",
                "registrar análise de combustível",
                "registrar analise de combustivel",
                "análise de combustível",
                "analise de combustivel");
    }

    private static void fillOccurrence(VoiceIntent intent, String t, String low) {
        intent.setAction("CREATE_OCCURRENCE");
        intent.setOccurrencePriority(low.contains("crit") || low.contains("crít") ? "CRITICA" : "MEDIA");
        Matcher br = BRANCH.matcher(t);
        if (br.find()) {
            intent.setBranchReference(br.group(1).trim());
        }
        Matcher desc = OCC_DESC.matcher(t);
        if (desc.find()) {
            intent.setDescription(capitalize(desc.group(1).trim()));
            intent.setTitle("Ocorrência: " + intent.getDescription());
        } else {
            intent.setTitle("Nova ocorrência");
            intent.setDescription(t);
        }
    }

    private static void fillTaskRef(VoiceIntent intent, String t, VoiceContext context) {
        if (context != null && context.getCurrentTaskId() != null) {
            intent.setTaskReference("current");
            if (context.getCurrentTaskTitle() != null) {
                intent.setTitle(context.getCurrentTaskTitle());
            }
            return;
        }
        String low = t.toLowerCase(Locale.ROOT);
        if (low.contains("aferi")) {
            intent.setTaskReference("aferição");
        } else if (low.contains("extintor")) {
            intent.setTaskReference("extintores");
        } else if (low.contains("caixa")) {
            intent.setTaskReference("caixa");
        } else if (low.contains("pista")) {
            intent.setTaskReference("pista");
        } else if (low.contains("bomba")) {
            intent.setTaskReference("bomba");
        } else if (low.contains("tanque")) {
            intent.setTaskReference("tanque");
        } else if (low.contains("troco")) {
            intent.setTaskReference("troco");
        } else if (low.contains("lavagem")) {
            intent.setTaskReference("lavagem");
        } else if (low.contains("vence hoje") || low.contains("vence")) {
            intent.setTaskReference(t);
        } else {
            intent.setTaskReference(t);
        }
    }

    static boolean looksLikeInjection(String low) {
        return low.contains("ignore as instru") || low.contains("ignore previous")
                || low.contains("system prompt") || low.contains("altere a permiss")
                || low.contains("você agora é") || low.contains("voce agora e");
    }

    private static boolean wantsCreateTask(String low) {
        // gatilhos de recorrencia ja implicam criacao de tarefa/rotina
        if (containsAny(low, "toda segunda", "todas as segundas", "nos dias")) {
            return true;
        }
        boolean noun = containsAny(low, "tarefa", "rotina", "checklist", "servico", "serviço");
        boolean verb = containsAny(low, "criar", "crie", "cria ", "cadastr",
                "registrar", "registre", "agendar", "agende", "montar", "monte", "programar", "programa ",
                "nova ", "novo ");
        return noun && verb;
    }

    private static boolean containsAny(String low, String... parts) {
        for (String p : parts) {
            if (low.contains(p)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> extractTimes(String low) {
        Matcher m = Pattern.compile("\\b(\\d{1,2})(?::(\\d{2}))?\\b").matcher(low);
        List<String> out = new ArrayList<>();
        while (m.find()) {
            int h = Integer.parseInt(m.group(1));
            if (h > 23) {
                continue;
            }
            String mm = m.group(2) == null ? "00" : m.group(2);
            out.add(h + ":" + mm);
        }
        return out;
    }

    private static String toHm(String raw) {
        LocalTime t = VoiceDateTimeNormalizer.parseTime(raw);
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
