package com.torqmind.ops.application.voice;

import com.torqmind.ops.application.occurrence.OccurrenceService;
import com.torqmind.ops.application.routine.RoutineService;
import com.torqmind.ops.application.task.TaskDetailService;
import com.torqmind.ops.application.tenant.TenantResolver;
import com.torqmind.ops.domain.occurrence.Occurrence;
import com.torqmind.ops.domain.ops.OccurrenceStatus;
import com.torqmind.ops.domain.ops.RoutineStatus;
import com.torqmind.ops.domain.routine.RoutineRun;
import com.torqmind.ops.domain.routine.RoutineTemplate;
import com.torqmind.ops.domain.task.TaskType;
import com.torqmind.ops.infrastructure.persistence.NotificationRepository;
import com.torqmind.ops.infrastructure.persistence.OccurrenceRepository;
import com.torqmind.ops.infrastructure.persistence.RoutineRunRepository;
import com.torqmind.ops.infrastructure.persistence.RoutineTemplateRepository;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import com.torqmind.ops.shared.api.ForbiddenException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class VoiceCommandExecutor {

    private final RoutineService routineService;
    private final OccurrenceService occurrenceService;
    private final TaskDetailService taskDetailService;
    private final TenantResolver tenantResolver;
    private final RoutineRunRepository runRepository;
    private final RoutineTemplateRepository templateRepository;
    private final OccurrenceRepository occurrenceRepository;
    private final NotificationRepository notificationRepository;

    public VoiceCommandExecutor(
            RoutineService routineService,
            OccurrenceService occurrenceService,
            TaskDetailService taskDetailService,
            TenantResolver tenantResolver,
            RoutineRunRepository runRepository,
            RoutineTemplateRepository templateRepository,
            OccurrenceRepository occurrenceRepository,
            NotificationRepository notificationRepository
    ) {
        this.routineService = routineService;
        this.occurrenceService = occurrenceService;
        this.taskDetailService = taskDetailService;
        this.tenantResolver = tenantResolver;
        this.runRepository = runRepository;
        this.templateRepository = templateRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.notificationRepository = notificationRepository;
    }

    public Map<String, Object> execute(AppUserPrincipal me, VoiceIntent intent, VoiceResolved resolved) {
        String action = intent.getAction() == null ? "" : intent.getAction();
        return switch (action) {
            case "CREATE_TASK" -> createTask(me, intent, resolved);
            case "CREATE_OCCURRENCE" -> createOccurrence(me, intent, resolved);
            case "OPEN_QUALITY_ANALYSIS" -> openQualityAnalysis(intent);
            case "START_TASK" -> transitionRun(me, resolved, RoutineStatus.EM_ANDAMENTO, intent.getComment());
            case "COMPLETE_TASK" -> complete(me, intent, resolved);
            case "REJECT_TASK" -> reject(me, intent, resolved);
            case "ADD_COMMENT" -> addComment(me, intent, resolved);
            case "OPEN_TASK" -> open(resolved);
            case "LIST_TASKS" -> list(me, intent);
            case "LIST_OCCURRENCES" -> listOccurrences(me, intent);
            case "QUERY_TASK" -> queryTask(intent, resolved);
            case "DELETE_TASK" -> deleteTask(me, intent, resolved);
            case "HELP" -> help(me);
            case "ADMIN_DENIED" -> adminDenied(me);
            case "START_OCCURRENCE" -> startOccurrence(me, intent, resolved);
            case "CLOSE_OCCURRENCE" -> closeOccurrence(me, intent, resolved);
            case "LIST_MY_TASKS" -> listMyTasks(me, intent);
            case "OPEN_NOTIFICATIONS" -> openNotifications(me);
            case "SUMMARY_TODAY" -> summaryToday(me);
            default -> throw new IllegalArgumentException("Ação de voz não suportada.");
        };
    }

    public String preview(VoiceIntent intent, VoiceResolved resolved) {
        String action = intent.getAction() == null ? "" : intent.getAction();
        return switch (action) {
            case "CREATE_TASK" -> "Criar tarefa \"" + nvl(intent.getTitle(), "(sem título)") + "\""
                    + (resolved.getBranchName() != null ? " na filial " + resolved.getBranchName() : "")
                    + (resolved.getUserName() != null ? " para " + resolved.getUserName() : "")
                    + (intent.getScheduledDate() != null ? " em " + intent.getScheduledDate() : "")
                    + (intent.getStartTime() != null ? " das " + intent.getStartTime() : "")
                    + (intent.getDueTime() != null ? " às " + intent.getDueTime() : "")
                    + (Boolean.TRUE.equals(intent.getRequiresPhoto()) ? ", exigindo foto" : "")
                    + (Boolean.TRUE.equals(intent.getRequiresComment()) ? " e comentário" : "")
                    + ".";
            case "CREATE_OCCURRENCE" -> "Abrir ocorrência \"" + nvl(intent.getTitle(), "") + "\""
                    + (resolved.getBranchName() != null ? " em " + resolved.getBranchName() : "") + ".";
            case "OPEN_QUALITY_ANALYSIS" -> "Abrir a tela de análise de qualidade no recebimento"
                    + (intent.getFuel() != null ? " (" + intent.getFuel() + ")" : "")
                    + ", sem salvar.";
            case "START_TASK" -> "Iniciar \"" + nvl(resolved.getTaskTitle(), "a tarefa") + "\".";
            case "COMPLETE_TASK" -> "Concluir \"" + nvl(resolved.getTaskTitle(), "a tarefa") + "\".";
            case "REJECT_TASK" -> "Rejeitar \"" + nvl(resolved.getTaskTitle(), "a tarefa") + "\".";
            case "ADD_COMMENT" -> "Adicionar comentário em \"" + nvl(resolved.getTaskTitle(), "a tarefa") + "\".";
            case "OPEN_TASK" -> "Abrir \"" + nvl(resolved.getTaskTitle(), "a tarefa") + "\".";
            case "LIST_TASKS" -> "Listar tarefas"
                    + (intent.getRequestedStatus() != null ? " (" + intent.getRequestedStatus() + ")" : "") + ".";
            case "LIST_OCCURRENCES" -> "Listar ocorrências"
                    + ("ABERTA".equals(intent.getRequestedStatus()) ? " abertas" : "") + ".";
            case "QUERY_TASK" -> "Consultar status de \"" + nvl(resolved.getTaskTitle(), nvl(intent.getTitle(), "uma tarefa")) + "\".";
            case "DELETE_TASK" -> "Excluir a rotina \"" + nvl(resolved.getTaskTitle(), nvl(intent.getTitle(), "uma rotina")) + "\".";
            case "HELP" -> "Mostrar o que a assistente pode fazer.";
            case "ADMIN_DENIED" -> "Recusar pedido administrativo.";
            case "START_OCCURRENCE" -> "Assumir ocorrência \"" + nvl(resolved.getTaskTitle(), "selecionada") + "\".";
            case "CLOSE_OCCURRENCE" -> "Encerrar ocorrência \"" + nvl(resolved.getTaskTitle(), "selecionada") + "\".";
            case "LIST_MY_TASKS" -> "Listar minhas tarefas.";
            case "OPEN_NOTIFICATIONS" -> "Consultar avisos.";
            case "SUMMARY_TODAY" -> "Resumo operacional de hoje.";
            default -> "Confirmar comando.";
        };
    }

    public void collectMissing(VoiceIntent intent, VoiceResolved resolved, TaskDetailService details) {
        String action = nvl(intent.getAction(), "");
        List<String> missing = new ArrayList<>(intent.getMissingFields());
        if ("CREATE_TASK".equals(action)) {
            if (blank(intent.getTitle())) missing.add("title");
            if (blank(intent.getStartTime())) missing.add("startTime");
            if ("ONCE".equalsIgnoreCase(nvl(intent.getRecurrence(), "ONCE")) && blank(intent.getScheduledDate())) {
                missing.add("scheduledDate");
            }
            if ("USER".equalsIgnoreCase(intent.getTargetType()) && resolved.getUserId() == null
                    && intent.getAmbiguities().stream().noneMatch(a -> "targetUserReference".equals(a.getField()))) {
                missing.add("targetUserReference");
            }
            if ("SECTOR".equalsIgnoreCase(intent.getTargetType()) && resolved.getSectorId() == null
                    && intent.getAmbiguities().stream().noneMatch(a -> "targetSectorReference".equals(a.getField()))) {
                missing.add("targetSectorReference");
            }
        }
        if ("CREATE_OCCURRENCE".equals(action)) {
            if (blank(intent.getTitle())) missing.add("title");
            if (blank(intent.getDescription())) missing.add("description");
        }
        if ("ADD_COMMENT".equals(action) && blank(intent.getComment())) {
            missing.add("comment");
        }
        if ("REJECT_TASK".equals(action) && blank(intent.getComment())) {
            missing.add("comment");
        }
        if ("HELP".equals(action)) {
            intent.setMissingFields(List.of());
            intent.setAmbiguities(List.of());
            return;
        }
        if ("ADMIN_DENIED".equals(action) || "OPEN_NOTIFICATIONS".equals(action) || "SUMMARY_TODAY".equals(action)) {
            intent.setMissingFields(List.of());
            intent.setAmbiguities(List.of());
            return;
        }
        if (List.of("START_OCCURRENCE", "CLOSE_OCCURRENCE").contains(action) && resolved.getOccurrenceId() == null) {
            missing.add("taskReference");
        }
        if ("COMPLETE_TASK".equals(action) && resolved.getRunId() != null) {
            RoutineRun run = runRepository.findById(resolved.getRunId()).orElse(null);
            RoutineTemplate tpl = run == null ? null : templateRepository.findById(run.getTemplateId()).orElse(null);
            if (tpl != null && tpl.isRequiresPhoto() && !details.hasImageEvidence(TaskType.ROUTINE_RUN, run.getId())) {
                missing.add("photo");
            }
            if (tpl != null && tpl.isRequiresComment()
                    && blank(intent.getComment())
                    && !details.hasCommentEvidence(TaskType.ROUTINE_RUN, run.getId())
                    && (run.getExecutionComment() == null || run.getExecutionComment().isBlank())) {
                missing.add("comment");
            }
        }
        intent.setMissingFields(missing.stream().distinct().toList());
    }

    private Map<String, Object> createTask(AppUserPrincipal me, VoiceIntent intent, VoiceResolved resolved) {
        Long companyId = tenantResolver.resolveCompanyForCreate(me, resolved.getCompanyId());
        Long branchId = tenantResolver.resolveBranchForCreate(me, resolved.getBranchId());
        LocalTime start = VoiceDateTimeNormalizer.parseTime(intent.getStartTime());
        LocalTime due = blank(intent.getDueTime()) ? start : VoiceDateTimeNormalizer.parseTime(intent.getDueTime());
        LocalDate date = blank(intent.getScheduledDate())
                ? null
                : VoiceDateTimeNormalizer.parseDate(intent.getScheduledDate(), LocalDate.now(VoiceDateTimeNormalizer.ZONE));
        Integer weekday = VoiceDateTimeNormalizer.weekdayIso(nvl(intent.getTranscript(), "") + " " + nvl(intent.getRecurrence(), ""));
        if ("WEEKLY".equalsIgnoreCase(intent.getRecurrence()) && weekday == null) {
            weekday = 1;
        }
        List<Integer> customDays = VoiceDateTimeNormalizer.customDaysFromSpeech(nvl(intent.getTranscript(), ""));
        RoutineTemplate saved = routineService.createRecurringTask(
                companyId,
                branchId,
                intent.getTitle(),
                intent.getDescription(),
                nvl(intent.getRecurrence(), "ONCE"),
                nvl(intent.getTargetType(), "MANAGERS"),
                null,
                resolved.getSectorId(),
                resolved.getUserId(),
                start,
                due,
                weekday,
                null,
                customDays.isEmpty() ? null : customDays,
                false,
                date,
                intent.getReminderBeforeMinutes(),
                Boolean.TRUE.equals(intent.getRequiresPhoto()),
                Boolean.TRUE.equals(intent.getRequiresComment()),
                List.of(),
                me.userId()
        );
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entityType", "ROUTINE_TEMPLATE");
        out.put("entityId", saved.getId());
        String spoken = "Pronto. Criei a tarefa " + saved.getTitle() + ".";
        out.put("message", spoken);
        out.put("spoken", spoken);
        out.put("navigateTo", "/routines");
        return out;
    }

    private Map<String, Object> createOccurrence(AppUserPrincipal me, VoiceIntent intent, VoiceResolved resolved) {
        Occurrence occurrence = new Occurrence();
        occurrence.setCompanyId(tenantResolver.resolveCompanyForCreate(me, resolved.getCompanyId()));
        occurrence.setBranchId(tenantResolver.resolveBranchForCreate(me, resolved.getBranchId()));
        occurrence.setTitle(intent.getTitle());
        occurrence.setDescription(intent.getDescription());
        occurrence.setPriority(intent.getOccurrencePriority() == null ? "MEDIA" : intent.getOccurrencePriority());
        Occurrence saved = occurrenceService.open(occurrence, me);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entityType", "OCCURRENCE");
        out.put("entityId", saved.getId());
        String spoken = "Abri a ocorrência " + saved.getTitle() + ".";
        out.put("message", spoken);
        out.put("spoken", spoken);
        out.put("navigateTo", "/occurrences/" + saved.getId());
        return out;
    }

    private Map<String, Object> openQualityAnalysis(VoiceIntent intent) {
        Map<String, Object> out = new LinkedHashMap<>();
        String path = "/occurrences/new/fuel-quality";
        if (!blank(intent.getFuel())) {
            path += "?fuel=" + intent.getFuel();
        }
        out.put("entityType", "OCCURRENCE_FORM");
        out.put("message", "Abrir análise de qualidade no recebimento de combustível.");
        out.put("navigateTo", path);
        if (intent.getFuel() != null) {
            out.put("fuel", intent.getFuel());
        }
        return out;
    }

    private Map<String, Object> transitionRun(AppUserPrincipal me, VoiceResolved resolved, RoutineStatus status, String comment) {
        if (resolved.getRunId() == null) {
            throw new IllegalArgumentException("Tarefa não identificada.");
        }
        RoutineRun run = routineService.transition(resolved.getRunId(), status, comment, me);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entityType", "ROUTINE_RUN");
        out.put("entityId", run.getId());
        out.put("message", "Status atualizado.");
        out.put("navigateTo", "/routines/" + run.getId());
        return out;
    }

    private Map<String, Object> complete(AppUserPrincipal me, VoiceIntent intent, VoiceResolved resolved) {
        return transitionRun(me, resolved, RoutineStatus.CONCLUIDA, intent.getComment());
    }

    private Map<String, Object> reject(AppUserPrincipal me, VoiceIntent intent, VoiceResolved resolved) {
        if (resolved.getOccurrenceId() != null) {
            Occurrence occ = occurrenceService.transition(resolved.getOccurrenceId(), OccurrenceStatus.REJEITADA, intent.getComment(), me);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("entityType", "OCCURRENCE");
            out.put("entityId", occ.getId());
            out.put("message", "Ocorrência rejeitada.");
            out.put("navigateTo", "/occurrences/" + occ.getId());
            return out;
        }
        return transitionRun(me, resolved, RoutineStatus.REJEITADA, intent.getComment());
    }

    private Map<String, Object> addComment(AppUserPrincipal me, VoiceIntent intent, VoiceResolved resolved) {
        TaskType type = resolved.getOccurrenceId() != null ? TaskType.OCCURRENCE : TaskType.ROUTINE_RUN;
        Long id = resolved.getOccurrenceId() != null ? resolved.getOccurrenceId() : resolved.getRunId();
        if (id == null) {
            throw new IllegalArgumentException("Tarefa não identificada.");
        }
        taskDetailService.addComment(type, id, me, intent.getComment());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entityType", type.name());
        out.put("entityId", id);
        out.put("message", "Comentário adicionado.");
        out.put("navigateTo", type == TaskType.OCCURRENCE ? "/occurrences/" + id : "/routines/" + id);
        return out;
    }

    private Map<String, Object> open(VoiceResolved resolved) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (resolved.getOccurrenceId() != null) {
            out.put("entityType", "OCCURRENCE");
            out.put("entityId", resolved.getOccurrenceId());
            out.put("navigateTo", "/occurrences/" + resolved.getOccurrenceId());
        } else if (resolved.getRunId() != null) {
            out.put("entityType", "ROUTINE_RUN");
            out.put("entityId", resolved.getRunId());
            out.put("navigateTo", "/routines/" + resolved.getRunId());
        } else {
            throw new IllegalArgumentException("Tarefa não identificada.");
        }
        out.put("message", "Abrindo tarefa.");
        return out;
    }

    private Map<String, Object> queryTask(VoiceIntent intent, VoiceResolved resolved) {
        Map<String, Object> out = new LinkedHashMap<>();
        String title = nvl(resolved.getTaskTitle(), nvl(intent.getTitle(), "essa rotina"));
        String who = resolved.getUserName();
        if (resolved.getRunId() == null) {
            String alvo = who != null ? " para " + who : "";
            String answer = "Não encontrei a rotina \"" + title + "\"" + alvo + " para a data consultada.";
            out.put("entityType", "QUERY");
            out.put("message", answer);
            out.put("spoken", answer);
            return out;
        }
        RoutineRun run = runRepository.findById(resolved.getRunId()).orElse(null);
        if (run == null) {
            String answer = "Não encontrei a rotina \"" + title + "\".";
            out.put("entityType", "QUERY");
            out.put("message", answer);
            out.put("spoken", answer);
            return out;
        }
        String answer = switch (run.getStatus()) {
            case CONCLUIDA -> "Sim. " + (who != null ? who : "O responsável") + " concluiu a rotina \"" + title + "\"" + concludedWhen(run) + ".";
            case EM_ANDAMENTO -> "A rotina \"" + title + "\" está em andamento" + (who != null ? " com " + who : "") + ".";
            case ATRASADA -> "Ainda não. A rotina \"" + title + "\" está atrasada.";
            case REJEITADA -> "A rotina \"" + title + "\" foi rejeitada.";
            default -> "Ainda não. O status da rotina \"" + title + "\" é pendente.";
        };
        out.put("entityType", "ROUTINE_RUN");
        out.put("entityId", run.getId());
        out.put("message", answer);
        out.put("spoken", answer);
        return out;
    }

    private static String concludedWhen(RoutineRun run) {
        if (run.getCompletedAt() == null) {
            return "";
        }
        var t = run.getCompletedAt().atZone(VoiceDateTimeNormalizer.ZONE).toLocalTime();
        return String.format(" às %02dh%02d", t.getHour(), t.getMinute());
    }

    private Map<String, Object> deleteTask(AppUserPrincipal me, VoiceIntent intent, VoiceResolved resolved) {
        Map<String, Object> out = new LinkedHashMap<>();
        String tr = nvl(intent.getTranscript(), "").toLowerCase(java.util.Locale.ROOT);
        if (tr.contains("todas") || tr.contains("todos") || tr.contains("tudo")) {
            String answer = "Por segurança, não faço exclusão em massa por voz. Peça para excluir uma rotina específica de cada vez.";
            out.put("entityType", "REFUSED");
            out.put("message", answer);
            out.put("spoken", answer);
            return out;
        }
        String title = nvl(resolved.getTaskTitle(), nvl(intent.getTitle(), "essa rotina"));
        if (resolved.getTemplateId() == null) {
            String answer = "Não encontrei uma rotina única chamada \"" + title + "\" para excluir. Pode ser mais específico?";
            out.put("entityType", "QUERY");
            out.put("message", answer);
            out.put("spoken", answer);
            return out;
        }
        try {
            routineService.deleteTemplateAsActor(resolved.getTemplateId(), me);
        } catch (ForbiddenException ex) {
            String answer = "Você não tem permissão para excluir a rotina \"" + title + "\".";
            out.put("entityType", "FORBIDDEN");
            out.put("message", answer);
            out.put("spoken", answer);
            return out;
        }
        String answer = "Pronto. Removi a rotina \"" + title + "\".";
        out.put("entityType", "ROUTINE_TEMPLATE");
        out.put("entityId", resolved.getTemplateId());
        out.put("message", answer);
        out.put("spoken", answer);
        out.put("navigateTo", "/routines");
        return out;
    }

    private Map<String, Object> listOccurrences(AppUserPrincipal me, VoiceIntent intent) {
        Long cid = tenantResolver.resolveListCompanyId(me, null);
        Long bid = tenantResolver.branchFilterOrNull(me);
        OccurrenceStatus status = "ABERTA".equals(intent.getRequestedStatus()) ? OccurrenceStatus.ABERTA : null;
        List<Occurrence> occurrences = occurrenceService.list(cid, bid, status);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Occurrence occ : occurrences.stream().limit(20).toList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", occ.getId());
            row.put("title", occ.getTitle());
            row.put("status", occ.getStatus().name());
            items.add(row);
        }
        String label = status != null ? " aberta(s)" : "";
        String spoken;
        if (items.isEmpty()) {
            spoken = "Nenhuma ocorrência" + label + " no momento.";
        } else {
            StringBuilder sb = new StringBuilder("Você tem " + items.size() + " ocorrência"
                    + (items.size() > 1 ? "s" : "") + label + ".");
            int n = Math.min(3, items.size());
            for (int idx = 0; idx < n; idx++) {
                Object ti = items.get(idx).get("title");
                if (ti != null) {
                    sb.append(idx == 0 ? " " : ", ").append(ti);
                }
            }
            spoken = sb.toString();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entityType", "OCCURRENCE_LIST");
        out.put("items", items);
        out.put("message", spoken);
        out.put("spoken", spoken);
        out.put("navigateTo", "/occurrences");
        return out;
    }

    private Map<String, Object> list(AppUserPrincipal me, VoiceIntent intent) {
        Long cid = tenantResolver.resolveListCompanyId(me, null);
        Long bid = tenantResolver.branchFilterOrNull(me);
        RoutineStatus status = null;
        String requested = intent.getRequestedStatus();
        if (requested != null && !"HOJE".equals(requested)) {
            try {
                status = RoutineStatus.valueOf(requested);
            } catch (Exception ignored) {
                status = null;
            }
        }
        List<RoutineRun> runs = routineService.listRuns(cid, bid, status);
        if ("HOJE".equals(requested)) {
            LocalDate today = LocalDate.now(VoiceDateTimeNormalizer.ZONE);
            runs = runs.stream()
                    .filter(r -> r.getDueAt() != null
                            && r.getDueAt().atZone(VoiceDateTimeNormalizer.ZONE).toLocalDate().equals(today))
                    .toList();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (RoutineRun run : runs.stream().limit(20).toList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", run.getId());
            row.put("status", run.getStatus().name());
            row.put("dueAt", run.getDueAt());
            templateRepository.findById(run.getTemplateId()).ifPresent(t -> row.put("title", t.getTitle()));
            items.add(row);
        }
        String statusLabel = intent.getRequestedStatus() == null ? "" : switch (intent.getRequestedStatus()) {
            case "ATRASADA" -> " atrasada(s)";
            case "PENDENTE" -> " pendente(s)";
            case "HOJE" -> " para hoje";
            default -> "";
        };
        String spoken;
        if (items.isEmpty()) {
            spoken = "Nenhuma rotina" + statusLabel + " encontrada.";
        } else {
            StringBuilder sb = new StringBuilder("Você tem " + items.size() + " rotina" + (items.size() > 1 ? "s" : "") + statusLabel + ".");
            int n = Math.min(3, items.size());
            for (int idx = 0; idx < n; idx++) {
                Object ti = items.get(idx).get("title");
                if (ti != null) {
                    sb.append(idx == 0 ? " " : ", ").append(ti);
                }
            }
            spoken = sb.toString();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entityType", "ROUTINE_RUN_LIST");
        out.put("items", items);
        out.put("message", spoken);
        out.put("spoken", spoken);
        out.put("navigateTo", "/routines");
        return out;
    }

    private Map<String, Object> adminDenied(AppUserPrincipal me) {
        String role = me == null ? "" : nvl(me.role(), "");
        String spoken = switch (role.toUpperCase(java.util.Locale.ROOT)) {
            case "MASTER" -> "Cadastro de usuários, filiais e configurações administrativas é feito no menu Gestão da tela, não por voz. Posso ajudar com tarefas e ocorrências do dia a dia.";
            case "OWNER", "MANAGER" -> "Isso é configuração administrativa e não está disponível por voz. Use a tela do sistema ou fale com o administrador. Posso ajudar com tarefas e ocorrências da operação.";
            default -> "Funcionários não fazem cadastros no sistema. Posso ajudar com suas tarefas, ocorrências e avisos.";
        };
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entityType", "ADMIN_DENIED");
        out.put("message", spoken);
        out.put("spoken", spoken);
        return out;
    }

    private Map<String, Object> startOccurrence(AppUserPrincipal me, VoiceIntent intent, VoiceResolved resolved) {
        if (resolved.getOccurrenceId() == null) {
            throw new IllegalArgumentException("Ocorrência não identificada.");
        }
        Occurrence occ = occurrenceService.transition(
                resolved.getOccurrenceId(), OccurrenceStatus.EM_ATENDIMENTO, intent.getComment(), me);
        String spoken = "Certo. Estou atendendo a ocorrência " + occ.getTitle() + ".";
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entityType", "OCCURRENCE");
        out.put("entityId", occ.getId());
        out.put("message", spoken);
        out.put("spoken", spoken);
        out.put("navigateTo", "/occurrences/" + occ.getId());
        return out;
    }

    private Map<String, Object> closeOccurrence(AppUserPrincipal me, VoiceIntent intent, VoiceResolved resolved) {
        if (resolved.getOccurrenceId() == null) {
            throw new IllegalArgumentException("Ocorrência não identificada.");
        }
        Occurrence occ = occurrenceService.get(resolved.getOccurrenceId(), me);
        OccurrenceStatus status = occ.getStatus();
        if (status == OccurrenceStatus.ABERTA) {
            String spoken = "A ocorrência ainda está aberta. Diga 'assumir ocorrência' antes de encerrar.";
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("entityType", "QUERY");
            out.put("message", spoken);
            out.put("spoken", spoken);
            return out;
        }
        if (status == OccurrenceStatus.EM_ATENDIMENTO) {
            occurrenceService.transition(resolved.getOccurrenceId(), OccurrenceStatus.AGUARDANDO_VALIDACAO, null, me);
            status = OccurrenceStatus.AGUARDANDO_VALIDACAO;
        }
        if (status == OccurrenceStatus.AGUARDANDO_VALIDACAO) {
            occ = occurrenceService.transition(resolved.getOccurrenceId(), OccurrenceStatus.ENCERRADA, intent.getComment(), me);
        } else if (status == OccurrenceStatus.ENCERRADA || status == OccurrenceStatus.REJEITADA) {
            String spoken = "Essa ocorrência já está " + (status == OccurrenceStatus.ENCERRADA ? "encerrada" : "rejeitada") + ".";
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("entityType", "OCCURRENCE");
            out.put("entityId", occ.getId());
            out.put("message", spoken);
            out.put("spoken", spoken);
            return out;
        } else {
            throw new IllegalArgumentException("Não posso encerrar a ocorrência neste status.");
        }
        String spokenDone = "Pronto. Encerrei a ocorrência " + occ.getTitle() + ".";
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entityType", "OCCURRENCE");
        out.put("entityId", occ.getId());
        out.put("message", spokenDone);
        out.put("spoken", spokenDone);
        out.put("navigateTo", "/occurrences/" + occ.getId());
        return out;
    }

    private Map<String, Object> listMyTasks(AppUserPrincipal me, VoiceIntent intent) {
        Long cid = tenantResolver.resolveListCompanyId(me, null);
        Long bid = tenantResolver.branchFilterOrNull(me);
        RoutineStatus status = null;
        String requested = intent.getRequestedStatus();
        if (requested != null && !"HOJE".equals(requested)) {
            try {
                status = RoutineStatus.valueOf(requested);
            } catch (Exception ignored) {
                status = null;
            }
        }
        List<RoutineRun> runs = routineService.listRuns(cid, bid, status);
        runs = runs.stream()
                .filter(r -> me.userId().equals(r.getAssignedUserId()))
                .toList();
        if ("HOJE".equals(requested)) {
            LocalDate today = LocalDate.now(VoiceDateTimeNormalizer.ZONE);
            runs = runs.stream()
                    .filter(r -> r.getDueAt() != null
                            && r.getDueAt().atZone(VoiceDateTimeNormalizer.ZONE).toLocalDate().equals(today))
                    .toList();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (RoutineRun run : runs.stream().limit(20).toList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", run.getId());
            row.put("status", run.getStatus().name());
            templateRepository.findById(run.getTemplateId()).ifPresent(t -> row.put("title", t.getTitle()));
            items.add(row);
        }
        String statusLabel = requested == null ? "" : switch (requested) {
            case "ATRASADA" -> " atrasada(s)";
            case "PENDENTE" -> " pendente(s)";
            case "HOJE" -> " para hoje";
            default -> "";
        };
        String spoken;
        if (items.isEmpty()) {
            spoken = "Você não tem tarefas" + statusLabel + " no momento.";
        } else {
            StringBuilder sb = new StringBuilder("Você tem " + items.size() + " tarefa" + (items.size() > 1 ? "s" : "") + statusLabel + ".");
            int n = Math.min(3, items.size());
            for (int idx = 0; idx < n; idx++) {
                Object ti = items.get(idx).get("title");
                if (ti != null) {
                    sb.append(idx == 0 ? " " : ", ").append(ti);
                }
            }
            spoken = sb.toString();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entityType", "ROUTINE_RUN_LIST");
        out.put("items", items);
        out.put("message", spoken);
        out.put("spoken", spoken);
        out.put("navigateTo", "/routines");
        return out;
    }

    private Map<String, Object> openNotifications(AppUserPrincipal me) {
        long unread = notificationRepository.countByRecipientUserIdAndReadAtIsNull(me.userId());
        var latest = notificationRepository.findTop50ByRecipientUserIdOrderByCreatedAtDesc(me.userId());
        String spoken;
        if (unread == 0) {
            spoken = "Você não tem avisos novos.";
        } else {
            spoken = "Você tem " + unread + " aviso" + (unread > 1 ? "s" : "") + " não lido" + (unread > 1 ? "s" : "") + ".";
            if (!latest.isEmpty() && latest.get(0).getTitle() != null) {
                spoken = spoken + " O mais recente: " + latest.get(0).getTitle() + ".";
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entityType", "NOTIFICATION_LIST");
        out.put("unreadCount", unread);
        out.put("message", spoken);
        out.put("spoken", spoken);
        out.put("navigateTo", "/notifications");
        return out;
    }

    private Map<String, Object> summaryToday(AppUserPrincipal me) {
        Long cid = tenantResolver.resolveListCompanyId(me, null);
        Long bid = tenantResolver.branchFilterOrNull(me);
        long pending = bid != null
                ? runRepository.countByCompanyIdAndBranchIdAndStatus(cid, bid, RoutineStatus.PENDENTE)
                : runRepository.countByCompanyIdAndStatus(cid, RoutineStatus.PENDENTE);
        long overdue = bid != null
                ? runRepository.countByCompanyIdAndBranchIdAndStatus(cid, bid, RoutineStatus.ATRASADA)
                : runRepository.countByCompanyIdAndStatus(cid, RoutineStatus.ATRASADA);
        long openOcc = bid != null
                ? occurrenceRepository.countByCompanyIdAndBranchIdAndStatus(cid, bid, OccurrenceStatus.ABERTA)
                : occurrenceRepository.countByCompanyIdAndStatus(cid, OccurrenceStatus.ABERTA);
        long inProgressOcc = bid != null
                ? occurrenceRepository.countByCompanyIdAndBranchIdAndStatus(cid, bid, OccurrenceStatus.EM_ATENDIMENTO)
                : occurrenceRepository.countByCompanyIdAndStatus(cid, OccurrenceStatus.EM_ATENDIMENTO);
        long unread = notificationRepository.countByRecipientUserIdAndReadAtIsNull(me.userId());
        String scope = bid != null ? "na sua filial" : "na empresa";
        String spoken = "Resumo de hoje " + scope + ": "
                + pending + " tarefa" + (pending == 1 ? "" : "s") + " pendente" + (pending == 1 ? "" : "s") + ", "
                + overdue + " atrasada" + (overdue == 1 ? "" : "s") + ", "
                + openOcc + " ocorrência" + (openOcc == 1 ? "" : "s") + " aberta" + (openOcc == 1 ? "" : "s") + ", "
                + inProgressOcc + " em atendimento"
                + (unread > 0 ? ", e " + unread + " aviso" + (unread > 1 ? "s" : "") + " não lido" + (unread > 1 ? "s" : "") : "")
                + ".";
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entityType", "OPERATION_SUMMARY");
        out.put("pendingTasks", pending);
        out.put("overdueTasks", overdue);
        out.put("openOccurrences", openOcc);
        out.put("occurrencesInProgress", inProgressOcc);
        out.put("unreadNotifications", unread);
        out.put("message", spoken);
        out.put("spoken", spoken);
        return out;
    }

    private Map<String, Object> help(AppUserPrincipal me) {
        String role = me == null ? "" : nvl(me.role(), "");
        String spoken = """
                Sou a assistente do TorqMind Ops. Posso criar tarefas e ocorrências, iniciar, concluir ou rejeitar trabalhos, \
                assumir e encerrar chamados, listar pendências e atrasos, consultar se alguém concluiu uma rotina, \
                ver avisos, resumir o dia e abrir análise de combustível. \
                Para vendas, metas e comissões, use o TorqMind BI. \
                Fale naturalmente em português. Se faltar algum detalhe, eu pergunto. \
                Cadastro de usuários, filiais e configurações administrativas só pela tela de Gestão. \
                Para excluir ou rejeitar, peço confirmação antes.""";
        if ("OPERATOR".equalsIgnoreCase(role)) {
            spoken = spoken + " Como funcionário, você vê e age nas tarefas da sua filial.";
        } else if ("MANAGER".equalsIgnoreCase(role)) {
            spoken = spoken + " Como gerente, você gerencia a operação da sua filial.";
        } else if ("OWNER".equalsIgnoreCase(role)) {
            spoken = spoken + " Como dono da empresa, você enxerga todas as filiais.";
        } else if ("MASTER".equalsIgnoreCase(role)) {
            spoken = spoken + " Como administrador, você tem visão global.";
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entityType", "HELP");
        out.put("message", spoken);
        out.put("spoken", spoken);
        return out;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String nvl(String s, String d) {
        return blank(s) ? d : s;
    }
}
