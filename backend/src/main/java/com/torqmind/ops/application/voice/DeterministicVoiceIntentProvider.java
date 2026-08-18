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

        if (looksLikeStatusQuery(low)) {
            fillStatusQuery(intent, t, low);
        } else if (looksLikeDelete(low)) {
            fillDelete(intent, t);
        } else if (looksLikeQualityAnalysis(low)) {
            fillQualityAnalysis(intent, low);
        } else if (containsAny(low, "ocorrencia", "ocorrência")) {
            fillOccurrence(intent, t, low);
        } else if (wantsCreateTask(low)) {
            fillCreateTask(intent, t, low);
        } else if (containsAny(low, "inicie", "iniciar", "comece")) {
            intent.setAction("START_TASK");
            fillTaskRef(intent, t, context);
        } else if (containsAny(low, "conclua", "concluir", "finalize")) {
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
        } else if (containsAny(low, "mostre", "liste", "minhas tarefas", "o que esta pendente", "o que está pendente", "o que falta", "resumo", "pendentes", "atrasadas", "situacao", "situação")) {
            intent.setAction("LIST_TASKS");
            if (low.contains("atrasad")) {
                intent.setRequestedStatus("ATRASADA");
            } else if (low.contains("pendent")) {
                intent.setRequestedStatus("PENDENTE");
            } else if (low.contains("hoje")) {
                intent.setRequestedStatus("HOJE");
            }
        } else if (containsAny(low, "abra a tarefa", "abrir tarefa", "abre a tarefa")) {
            intent.setAction("OPEN_TASK");
            fillTaskRef(intent, t, context);
        } else if (containsAny(low, "abra uma ocorr", "abrir ocorr")) {
            if (looksLikeQualityAnalysis(low)) {
                fillQualityAnalysis(intent, low);
            } else {
                fillOccurrence(intent, t, low);
            }
        } else {
            intent.setAction("LIST_TASKS");
            intent.getWarnings().add("Não identifiquei a ação; mostrando tarefas para conferência.");
            intent.setConfidence(0.3);
        }
        return intent;
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
        } else if ("WEEKLY".equals(intent.getRecurrence())) {
            Integer wd = VoiceDateTimeNormalizer.weekdayIso(low);
            if (wd != null) {
                intent.setScheduledDate(VoiceDateTimeNormalizer.parseDate("segunda", today).toString());
            }
        }

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
        boolean noun = containsAny(low, "tarefa", "rotina");
        boolean verb = containsAny(low, "criar", "crie", "cria ", "cadastr",
                "registrar", "registre", "agendar", "agende", "montar", "monte", "nova ", "novo ");
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
