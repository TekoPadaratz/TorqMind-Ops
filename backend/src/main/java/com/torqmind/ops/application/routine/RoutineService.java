package com.torqmind.ops.application.routine;

import com.torqmind.ops.application.notification.NotificationService;
import com.torqmind.ops.application.task.ActivityService;
import com.torqmind.ops.application.tenant.TenantAccessService;
import com.torqmind.ops.domain.calendar.BrazilianNationalHolidays;
import com.torqmind.ops.domain.ops.RoutineStatus;
import com.torqmind.ops.domain.ops.StatusRules;
import com.torqmind.ops.domain.routine.RoutineRun;
import com.torqmind.ops.domain.routine.RoutineTemplate;
import com.torqmind.ops.domain.task.TaskType;
import com.torqmind.ops.domain.user.User;
import com.torqmind.ops.infrastructure.persistence.RoutineRunRepository;
import com.torqmind.ops.infrastructure.persistence.RoutineTemplateRepository;
import com.torqmind.ops.infrastructure.persistence.TaskAttachmentRepository;
import com.torqmind.ops.infrastructure.persistence.TaskCommentRepository;
import com.torqmind.ops.infrastructure.persistence.UserRepository;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import com.torqmind.ops.shared.media.MediaSignatures;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RoutineService {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final RoutineTemplateRepository templateRepository;
    private final RoutineRunRepository runRepository;
    private final NotificationService notificationService;
    private final ActivityService activityService;
    private final TaskAttachmentRepository attachmentRepository;
    private final TaskCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final TenantAccessService tenantAccessService;

    public RoutineService(
            RoutineTemplateRepository templateRepository,
            RoutineRunRepository runRepository,
            NotificationService notificationService,
            ActivityService activityService,
            TaskAttachmentRepository attachmentRepository,
            TaskCommentRepository commentRepository,
            UserRepository userRepository,
            TenantAccessService tenantAccessService
    ) {
        this.templateRepository = templateRepository;
        this.runRepository = runRepository;
        this.notificationService = notificationService;
        this.activityService = activityService;
        this.attachmentRepository = attachmentRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.tenantAccessService = tenantAccessService;
    }

    public List<RoutineTemplate> listTemplates(Long companyId) {
        return templateRepository.findByCompanyIdAndActiveTrueOrderByCreatedAtDesc(companyId);
    }

    @Transactional
    public void deactivateTemplate(Long templateId, AppUserPrincipal me) {
        RoutineTemplate template = tenantAccessService.requireTemplateAccess(me, templateId);
        template.setActive(false);
        templateRepository.save(template);
    }

    @Transactional
    public RoutineTemplate createTemplate(RoutineTemplate template, UUID actor) {
        tenantAccessService.requireBranchInCompany(template.getCompanyId(), template.getBranchId());
        template.setCreatedBy(actor);
        template.setCreatedAt(Instant.now());
        return templateRepository.save(template);
    }

    public List<RoutineRun> listRuns(Long companyId, Long branchId, RoutineStatus status) {
        if (branchId != null) {
            if (status == null) {
                return runRepository.findByCompanyIdAndBranchIdOrderByDueAtAsc(companyId, branchId);
            }
            return runRepository.findByCompanyIdAndBranchIdAndStatusOrderByDueAtAsc(companyId, branchId, status);
        }
        if (status == null) {
            return runRepository.findByCompanyIdOrderByDueAtAsc(companyId);
        }
        return runRepository.findByCompanyIdAndStatusOrderByDueAtAsc(companyId, status);
    }

    @Transactional
    public RoutineRun generateRun(
            Long templateId,
            Instant scheduledFor,
            Instant dueAt,
            UUID assignedUserId,
            AppUserPrincipal me
    ) {
        RoutineTemplate template = tenantAccessService.requireTemplateAccess(me, templateId);
        if (assignedUserId != null) {
            tenantAccessService.requireTargetUser(template.getCompanyId(), template.getBranchId(), assignedUserId);
        }
        UUID actor = me.userId();

        RoutineRun run = new RoutineRun();
        run.setTemplateId(template.getId());
        run.setCompanyId(template.getCompanyId());
        run.setBranchId(template.getBranchId());
        run.setAssignedUserId(assignedUserId);
        run.setStatus(RoutineStatus.PENDENTE);
        run.setScheduledFor(scheduledFor != null ? scheduledFor : Instant.now());
        run.setDueAt(dueAt);
        run.setCreatedAt(Instant.now());
        run.setUpdatedAt(Instant.now());
        RoutineRun saved = runRepository.save(run);

        activityService.record(TaskType.ROUTINE_RUN, saved.getId(), actor, "CREATED", null,
                RoutineStatus.PENDENTE.name(), "Execução criada a partir de: " + template.getTitle());

        if (assignedUserId != null) {
            notificationService.notifyCounterpart(actor, assignedUserId, "ROUTINE_RUN", saved.getId(),
                    "Nova tarefa", "Você recebeu a rotina: " + template.getTitle());
        }
        return saved;
    }

    @Transactional
    public RoutineRun transition(Long runId, RoutineStatus next, String comment, AppUserPrincipal me) {
        RoutineRun run = tenantAccessService.requireRoutineRunAccess(me, runId);
        UUID actor = me.userId();

        if (!StatusRules.canTransitionRoutine(run.getStatus(), next)) {
            throw new IllegalArgumentException("Transição de status de rotina inválida.");
        }

        if (next == RoutineStatus.EM_ANDAMENTO || next == RoutineStatus.CONCLUIDA) {
            tenantAccessService.requireRoutineExecutor(me, run);
        }

        RoutineTemplate template = templateRepository.findById(run.getTemplateId()).orElse(null);
        if (next == RoutineStatus.CONCLUIDA && template != null && template.isRequiresComment()) {
            boolean hasInline = (comment != null && !comment.isBlank())
                    || (run.getExecutionComment() != null && !run.getExecutionComment().isBlank());
            boolean hasThreadComment = run.getAssignedUserId() == null
                    ? commentRepository.countByTaskTypeAndTaskId(TaskType.ROUTINE_RUN.name(), run.getId()) > 0
                    : commentRepository.countByTaskTypeAndTaskIdAndAuthorUserId(
                            TaskType.ROUTINE_RUN.name(), run.getId(), run.getAssignedUserId()) > 0;
            if (!hasInline && !hasThreadComment) {
                throw new IllegalArgumentException("Esta rotina exige um comentário do responsável para ser concluída.");
            }
        }
        if (next == RoutineStatus.CONCLUIDA && template != null && template.isRequiresPhoto()
                && !hasValidPhoto(TaskType.ROUTINE_RUN, run.getId(), run.getAssignedUserId())) {
            throw new IllegalArgumentException("Esta rotina exige ao menos uma foto do responsável para ser concluída.");
        }

        Instant now = Instant.now();
        RoutineStatus previous = run.getStatus();
        if (next == RoutineStatus.EM_ANDAMENTO && run.getStartedAt() == null) {
            run.setStartedAt(now);
        }
        if (next == RoutineStatus.CONCLUIDA) {
            run.setCompletedAt(now);
        }
        if (comment != null && !comment.isBlank()) {
            run.setExecutionComment(comment);
        }
        run.setStatus(next);
        run.setUpdatedAt(now);
        RoutineRun saved = runRepository.save(run);

        activityService.record(TaskType.ROUTINE_RUN, saved.getId(), actor, "STATUS_CHANGED",
                previous.name(), next.name(), comment);

        UUID owner = template != null ? template.getCreatedBy() : null;
        UUID assignee = run.getAssignedUserId();
        UUID recipient = actor.equals(assignee) ? owner : assignee;
        if (recipient != null) {
            notificationService.notifyCounterpart(actor, recipient, "ROUTINE_RUN", saved.getId(),
                    "Rotina atualizada", "Status alterado para " + next.name());
        }
        return saved;
    }

    public Optional<RoutineTemplate> findTemplate(Long id) {
        return templateRepository.findById(id);
    }

    @Transactional
    public int generateNow(Long templateId, AppUserPrincipal me) {
        RoutineTemplate template = tenantAccessService.requireTemplateAccess(me, templateId);
        return generateRunsForTemplate(template, me.userId());
    }

    // ---- Criacao direta de tarefa (recorrente ou unica), com alvo e janela de horario ----
    @Transactional
    public RoutineTemplate createRecurringTask(
            Long companyId,
            Long branchId,
            String title,
            String description,
            String recurrence,
            String targetType,
            String targetRole,
            Long targetSectorId,
            UUID targetUserId,
            LocalTime startTime,
            LocalTime dueTime,
            Integer weekday,
            Integer dayOfMonth,
            List<Integer> customDays,
            boolean businessDaysOnly,
            LocalDate startDate,
            Integer reminderBeforeMinutes,
            boolean requiresPhoto,
            boolean requiresComment,
            UUID actor
    ) {
        String rec = normalizeRecurrence(recurrence);
        String target = normalizeTarget(targetType);
        if (companyId == null) {
            throw new IllegalArgumentException("Informe a empresa.");
        }
        tenantAccessService.requireBranchInCompany(companyId, branchId);
        if ("USER".equals(target) && targetUserId == null) {
            throw new IllegalArgumentException("Selecione o usuario responsavel.");
        }
        if ("USER".equals(target)) {
            tenantAccessService.requireTargetUser(companyId, branchId, targetUserId);
        }
        if ("SECTOR".equals(target) && targetSectorId == null) {
            throw new IllegalArgumentException("Selecione o setor.");
        }
        if ("SECTOR".equals(target)) {
            tenantAccessService.requireTargetSector(companyId, branchId, targetSectorId);
        }
        if (startTime == null || dueTime == null) {
            throw new IllegalArgumentException("Informe o horario de inicio e de vencimento.");
        }
        if (!dueTime.isAfter(startTime)) {
            throw new IllegalArgumentException("O vencimento deve ser depois do inicio.");
        }
        LocalDate today = LocalDate.now(ZONE);
        if ("ONCE".equals(rec) && startDate == null) {
            throw new IllegalArgumentException("Selecione a data da tarefa.");
        }
        if ("ONCE".equals(rec) && startDate.isBefore(today)) {
            throw new IllegalArgumentException("A data da tarefa nao pode estar no passado.");
        }
        String customDaysCsv = null;
        if ("CUSTOM".equals(rec)) {
            customDaysCsv = normalizeCustomDays(customDays);
            if (customDaysCsv == null) {
                throw new IllegalArgumentException("Selecione ao menos um dia do mês para a recorrência personalizada.");
            }
        }
        if ("MONTHLY".equals(rec) && (dayOfMonth == null || dayOfMonth < 1 || dayOfMonth > 31)) {
            throw new IllegalArgumentException("Informe o dia do mês (1–31).");
        }
        boolean onlyBusiness = businessDaysOnly && ("MONTHLY".equals(rec) || "CUSTOM".equals(rec));

        RoutineTemplate template = new RoutineTemplate();
        template.setCompanyId(companyId);
        template.setBranchId(branchId);
        template.setTitle(title);
        template.setDescription(description);
        template.setRecurrenceRule(rec);
        template.setTargetType(target);
        template.setTargetRole("MANAGERS".equals(target) ? "MANAGER" : targetRole);
        template.setTargetSectorId("SECTOR".equals(target) ? targetSectorId : null);
        template.setTargetUserId("USER".equals(target) ? targetUserId : null);
        template.setStartTime(startTime);
        template.setDueTime(dueTime);
        template.setWeekday("WEEKLY".equals(rec) ? weekday : null);
        template.setDayOfMonth("MONTHLY".equals(rec) ? dayOfMonth : null);
        template.setCustomDays(customDaysCsv);
        template.setBusinessDaysOnly(onlyBusiness);
        template.setStartDate("ONCE".equals(rec) ? startDate : null);
        int remMin = reminderBeforeMinutes == null ? 30 : Math.max(0, Math.min(1440, reminderBeforeMinutes));
        template.setReminderBeforeMinutes(remMin);
        template.setRequiresPhoto(requiresPhoto);
        template.setRequiresComment(requiresComment);
        template.setActive(true);
        template.setCreatedBy(actor);
        template.setCreatedAt(Instant.now());
        RoutineTemplate saved = templateRepository.save(template);

        // Se hoje for dia de execucao e o horario de inicio ja chegou, gera imediatamente.
        if (isRunDay(today, saved) && !LocalTime.now(ZONE).isBefore(startTime)) {
            generateRunsForTemplate(saved, actor);
            saved.setLastGeneratedOn(today);
            if ("ONCE".equals(rec)) {
                saved.setActive(false);
            }
            templateRepository.save(saved);
        }
        return saved;
    }

    // ---- Agendador: gera execucoes cujo inicio chegou hoje ----
    @Transactional
    public int generateDueRuns() {
        LocalDate today = LocalDate.now(ZONE);
        LocalTime now = LocalTime.now(ZONE);
        int created = 0;
        for (RoutineTemplate t : templateRepository.findByActiveTrueAndStartTimeIsNotNull()) {
            if (today.equals(t.getLastGeneratedOn())) {
                continue;
            }
            if (!isRunDay(today, t)) {
                continue;
            }
            if (t.getStartTime() == null || now.isBefore(t.getStartTime())) {
                continue;
            }
            created += generateRunsForTemplate(t, t.getCreatedBy());
            t.setLastGeneratedOn(today);
            if ("ONCE".equals(t.getRecurrenceRule())) {
                t.setActive(false);
            }
            templateRepository.save(t);
        }
        return created;
    }

    // ---- Agendador: lembrete de proximidade de vencimento e marcacao de atraso ----
    @Transactional
    public int processDueReminders() {
        Instant now = Instant.now();
        int actions = 0;
        List<RoutineRun> open = runRepository.findByStatusIn(
                List.of(RoutineStatus.PENDENTE, RoutineStatus.EM_ANDAMENTO));
        for (RoutineRun run : open) {
            if (run.getDueAt() == null) {
                continue;
            }
            RoutineTemplate template = templateRepository.findById(run.getTemplateId()).orElse(null);
            UUID owner = template != null ? template.getCreatedBy() : null;
            UUID assignee = run.getAssignedUserId();
            String title = template != null ? template.getTitle() : "Tarefa";

            if (!now.isBefore(run.getDueAt())) {
                RoutineStatus previous = run.getStatus();
                run.setStatus(RoutineStatus.ATRASADA);
                run.setUpdatedAt(now);
                runRepository.save(run);
                activityService.record(TaskType.ROUTINE_RUN, run.getId(), owner, "STATUS_CHANGED",
                        previous.name(), RoutineStatus.ATRASADA.name(), "Prazo expirado sem conclusao");
                notificationService.notifyCounterpart(owner, assignee, "ROUTINE_RUN", run.getId(),
                        "Tarefa atrasada", title + " passou do prazo.");
                actions++;
            } else if (!run.isExpiryReminded() && run.getScheduledFor() != null) {
                int remMin = run.getReminderBeforeMinutes() != null ? run.getReminderBeforeMinutes() : 30;
                Instant remindFrom = run.getDueAt().minus(remMin, ChronoUnit.MINUTES);
                if (remindFrom.isBefore(run.getScheduledFor())) {
                    remindFrom = run.getScheduledFor();
                }
                if (!now.isBefore(remindFrom)) {
                    notificationService.notifyCounterpart(owner, assignee, "ROUTINE_RUN", run.getId(),
                            "Tarefa perto de vencer", title + " vence em breve. Conclua a tempo.");
                    run.setExpiryReminded(true);
                    run.setUpdatedAt(now);
                    runRepository.save(run);
                    actions++;
                }
            }
        }
        return actions;
    }

    private int generateRunsForTemplate(RoutineTemplate template, UUID actor) {
        List<UUID> recipients = resolveTargetUsers(template);
        LocalDate today = LocalDate.now(ZONE);
        Instant now = Instant.now();
        Instant scheduled = template.getStartTime() != null
                ? today.atTime(template.getStartTime()).atZone(ZONE).toInstant()
                : now;
        Instant due = template.getDueTime() != null
                ? today.atTime(template.getDueTime()).atZone(ZONE).toInstant()
                : null;
        String window = template.getStartTime() != null && template.getDueTime() != null
                ? " (inicio " + template.getStartTime() + ", vence " + template.getDueTime() + ")"
                : "";
        int count = 0;
        for (UUID userId : recipients) {
            RoutineRun run = new RoutineRun();
            run.setTemplateId(template.getId());
            run.setCompanyId(template.getCompanyId());
            run.setBranchId(template.getBranchId());
            run.setAssignedUserId(userId);
            run.setStatus(RoutineStatus.PENDENTE);
            run.setScheduledFor(scheduled);
            run.setDueAt(due);
            run.setExpiryReminded(false);
            run.setReminderBeforeMinutes(template.getReminderBeforeMinutes());
            run.setCreatedAt(now);
            run.setUpdatedAt(now);
            RoutineRun saved = runRepository.save(run);
            activityService.record(TaskType.ROUTINE_RUN, saved.getId(), actor, "CREATED", null,
                    RoutineStatus.PENDENTE.name(), "Tarefa gerada: " + template.getTitle());
            notificationService.notifyCounterpart(actor, userId, "ROUTINE_RUN", saved.getId(),
                    "Nova tarefa", template.getTitle() + window);
            count++;
        }
        return count;
    }

    private List<UUID> resolveTargetUsers(RoutineTemplate template) {
        Set<UUID> ids = new LinkedHashSet<>();
        String target = template.getTargetType() == null ? "USER" : template.getTargetType();
        Long companyId = template.getCompanyId();
        Long branchId = template.getBranchId();
        switch (target) {
            case "USER" -> {
                if (template.getTargetUserId() != null) {
                    ids.add(template.getTargetUserId());
                }
            }
            case "SECTOR" -> {
                if (template.getTargetSectorId() != null) {
                    userRepository.findBySectorIdAndActiveTrue(template.getTargetSectorId())
                            .stream()
                            .filter(u -> companyId == null || companyId.equals(u.getCompanyId()))
                            .filter(u -> branchId == null || branchId.equals(u.getBranchId()))
                            .forEach(u -> ids.add(u.getId()));
                }
            }
            case "MANAGERS" -> {
                List<User> managers = branchId != null
                        ? userRepository.findByCompanyIdAndBranchIdAndRoleIgnoreCaseAndActiveTrue(companyId, branchId, "MANAGER")
                        : userRepository.findByCompanyIdAndRoleIgnoreCaseAndActiveTrue(companyId, "MANAGER");
                managers.forEach(u -> ids.add(u.getId()));
            }
            case "ALL" -> {
                List<User> users = branchId != null
                        ? userRepository.findByCompanyIdAndBranchIdAndActiveTrue(companyId, branchId)
                        : userRepository.findByCompanyIdAndActiveTrue(companyId);
                for (User u : users) {
                    String role = u.getRole() == null ? "" : u.getRole().toUpperCase();
                    if (role.equals("MANAGER") || role.equals("OPERATOR") || role.equals("OWNER")) {
                        ids.add(u.getId());
                    }
                }
            }
            default -> {
                if (template.getTargetUserId() != null) {
                    ids.add(template.getTargetUserId());
                }
            }
        }
        return new ArrayList<>(ids);
    }

    private boolean isRunDay(LocalDate today, RoutineTemplate template) {
        String rec = template.getRecurrenceRule() == null ? "DAILY" : template.getRecurrenceRule();
        LocalDate created = template.getCreatedAt() == null
                ? today
                : template.getCreatedAt().atZone(ZONE).toLocalDate();
        return switch (rec) {
            case "DAILY" -> true;
            case "WEEKLY" -> {
                int wd = template.getWeekday() != null ? template.getWeekday() : created.getDayOfWeek().getValue();
                yield today.getDayOfWeek().getValue() == wd;
            }
            case "MONTHLY" -> {
                int dom = template.getDayOfMonth() != null ? template.getDayOfMonth() : created.getDayOfMonth();
                yield matchesDayOfMonth(today, Set.of(dom), template.isBusinessDaysOnly());
            }
            case "CUSTOM" -> matchesDayOfMonth(today, parseCustomDays(template.getCustomDays()), template.isBusinessDaysOnly());
            case "ONCE" -> template.getStartDate() != null && today.equals(template.getStartDate());
            default -> false;
        };
    }

    /**
     * Verifica se {@code today} é dia de execução para os dias-do-mês selecionados.
     * Com businessDaysOnly: se o dia cair em sáb/dom, a execução ocorre no próximo dia útil
     * (ex.: dia 15 no sábado → segunda). Pode cruzar o mês seguinte.
     */
    private static boolean matchesDayOfMonth(LocalDate today, Set<Integer> daysOfMonth, boolean businessDaysOnly) {
        if (daysOfMonth == null || daysOfMonth.isEmpty()) {
            return false;
        }
        if (!businessDaysOnly) {
            boolean hit = daysOfMonth.contains(today.getDayOfMonth());
            if (!hit && today.getDayOfMonth() == today.lengthOfMonth()) {
                hit = daysOfMonth.stream().anyMatch(d -> d > today.lengthOfMonth());
            }
            return hit;
        }
        LocalDate monthStart = today.withDayOfMonth(1);
        if (isEffectiveRunDate(today, monthStart, daysOfMonth)) {
            return true;
        }
        // Dia adiado do mês anterior (ex.: 31/sáb → 2/seg do mês seguinte)
        return isEffectiveRunDate(today, monthStart.minusMonths(1), daysOfMonth);
    }

    private static boolean isEffectiveRunDate(LocalDate today, LocalDate monthStart, Set<Integer> daysOfMonth) {
        int lastDay = monthStart.lengthOfMonth();
        for (Integer raw : daysOfMonth) {
            if (raw == null || raw < 1) {
                continue;
            }
            int dom = Math.min(raw, lastDay);
            LocalDate scheduled = monthStart.withDayOfMonth(dom);
            LocalDate effective = nextBusinessDay(scheduled);
            if (today.equals(effective)) {
                return true;
            }
        }
        return false;
    }

    /** Se cair em fim de semana ou feriado nacional, avança para o próximo dia útil. */
    static LocalDate nextBusinessDay(LocalDate date) {
        return BrazilianNationalHolidays.nextBusinessDay(date);
    }

    private static Set<Integer> parseCustomDays(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::valueOf)
                .filter(d -> d >= 1 && d <= 31)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Normaliza lista de dias 1–31 para CSV ordenado; null se vazia. */
    static String normalizeCustomDays(List<Integer> days) {
        if (days == null || days.isEmpty()) {
            return null;
        }
        Set<Integer> unique = days.stream()
                .filter(d -> d != null && d >= 1 && d <= 31)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (unique.isEmpty()) {
            return null;
        }
        return unique.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static String normalizeRecurrence(String value) {
        String v = value == null ? "" : value.trim().toUpperCase();
        return switch (v) {
            case "ONCE", "DAILY", "WEEKLY", "MONTHLY", "CUSTOM" -> v;
            default -> throw new IllegalArgumentException("Recorrência inválida.");
        };
    }

    private static String normalizeTarget(String value) {
        String v = value == null ? "" : value.trim().toUpperCase();
        return switch (v) {
            case "USER", "SECTOR", "MANAGERS", "ALL" -> v;
            default -> throw new IllegalArgumentException("Alvo inválido.");
        };
    }

    private boolean hasValidPhoto(TaskType type, Long taskId, UUID assignedUserId) {
        return attachmentRepository.findByTaskTypeAndTaskIdOrderByCreatedAt(type.name(), taskId).stream()
                .anyMatch(a -> MediaSignatures.isImage(a.getMimeType())
                        && (assignedUserId == null || assignedUserId.equals(a.getUploadedBy())));
    }
}
