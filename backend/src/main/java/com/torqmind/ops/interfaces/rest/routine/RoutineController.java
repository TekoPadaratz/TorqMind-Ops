package com.torqmind.ops.interfaces.rest.routine;

import com.torqmind.ops.application.routine.RoutineService;
import com.torqmind.ops.application.task.TaskDetailService;
import com.torqmind.ops.application.tenant.TenantResolver;
import com.torqmind.ops.domain.ops.RoutineStatus;
import com.torqmind.ops.domain.routine.RoutineRun;
import com.torqmind.ops.domain.routine.RoutineTemplate;
import com.torqmind.ops.domain.task.TaskType;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/routines")
public class RoutineController {

    private final RoutineService routineService;
    private final TaskDetailService taskDetailService;
    private final TenantResolver tenantResolver;

    public RoutineController(
            RoutineService routineService,
            TaskDetailService taskDetailService,
            TenantResolver tenantResolver
    ) {
        this.routineService = routineService;
        this.taskDetailService = taskDetailService;
        this.tenantResolver = tenantResolver;
    }

    @GetMapping("/templates")
    public List<RoutineTemplate> templates(
            @AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(required = false) Long companyId
    ) {
        Long cid = tenantResolver.resolveListCompanyId(me, companyId);
        List<RoutineTemplate> all = routineService.listTemplates(cid);
        Long branchFilter = tenantResolver.branchFilterOrNull(me);
        if (branchFilter == null) {
            return all;
        }
        return all.stream()
                .filter(t -> branchFilter.equals(t.getBranchId()))
                .toList();
    }

    @PostMapping("/templates")
    public RoutineTemplate createTemplate(
            @Valid @RequestBody CreateTemplateRequest request,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        RoutineTemplate template = new RoutineTemplate();
        template.setCompanyId(tenantResolver.resolveCompanyForCreate(me, request.companyId()));
        template.setBranchId(tenantResolver.resolveBranchForCreate(me, request.branchId()));
        template.setTitle(request.title());
        template.setDescription(request.description());
        template.setRecurrenceRule(request.recurrenceRule());
        template.setRequiresPhoto(Boolean.TRUE.equals(request.requiresPhoto()));
        template.setRequiresComment(Boolean.TRUE.equals(request.requiresComment()));
        template.setActive(true);
        return routineService.createTemplate(template, me.userId());
    }

    @DeleteMapping("/templates/{id}")
    public Map<String, Object> deactivateTemplate(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        routineService.deleteTemplateAsActor(id, me);
        return Map.of("id", id, "active", false);
    }

    @PostMapping("/tasks")
    public RoutineTemplate createTask(
            @Valid @RequestBody CreateTaskRequest request,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        Long companyId = tenantResolver.resolveCompanyForCreate(me, request.companyId());
        Long branchId = tenantResolver.resolveBranchForCreate(me, request.branchId());
        return routineService.createRecurringTask(
                companyId,
                branchId,
                request.title(),
                request.description(),
                request.recurrence(),
                request.targetType(),
                request.targetRole(),
                request.targetSectorId(),
                parseUuid(request.targetUserId()),
                request.startTime(),
                request.dueTime(),
                request.weekday(),
                request.dayOfMonth(),
                request.customDays(),
                Boolean.TRUE.equals(request.businessDaysOnly()),
                request.startDate(),
                request.reminderBeforeMinutes(),
                Boolean.TRUE.equals(request.requiresPhoto()),
                Boolean.TRUE.equals(request.requiresComment()),
                me.userId()
        );
    }

    @PostMapping("/templates/{id}/generate")
    public Map<String, Object> generateNow(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal me) {
        return Map.of("generated", routineService.generateNow(id, me));
    }

    @GetMapping("/runs/{id}")
    public TaskDetailService.TaskDetail runDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        return taskDetailService.getRoutineRunDetail(id, me);
    }

    @GetMapping("/runs/{id}/report")
    public ResponseEntity<byte[]> runReport(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        byte[] pdf = taskDetailService.renderRoutineRunReport(id, me);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=comprovante-rotina-" + id + ".pdf")
                .body(pdf);
    }

    @GetMapping("/runs/export.csv")
    public ResponseEntity<byte[]> exportRuns(
            @AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) RoutineStatus status
    ) {
        Long cid = tenantResolver.resolveListCompanyId(me, companyId);
        Long branchId = tenantResolver.branchFilterOrNull(me);
        byte[] csv = routineService.exportRunsCsv(cid, branchId, status);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=rotinas.csv")
                .body(csv);
    }

    @PostMapping("/runs/{id}/comments")
    public TaskDetailService.CommentView addComment(
            @PathVariable Long id,
            @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        return taskDetailService.addComment(TaskType.ROUTINE_RUN, id, me, request.body());
    }

    @PostMapping("/runs/{id}/attachments")
    public TaskDetailService.AttachmentView addAttachment(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        try {
            return taskDetailService.addAttachment(TaskType.ROUTINE_RUN, id, me,
                    file.getOriginalFilename(), file.getBytes(), lat, lng);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Falha ao ler o arquivo enviado.");
        }
    }

    @GetMapping("/runs")
    public List<RoutineRun> runs(
            @AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) RoutineStatus status
    ) {
        Long cid = tenantResolver.resolveListCompanyId(me, companyId);
        Long branchId = tenantResolver.branchFilterOrNull(me);
        return routineService.listRuns(cid, branchId, status);
    }

    @PostMapping("/runs")
    public RoutineRun generateRun(
            @Valid @RequestBody GenerateRunRequest request,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        UUID assigned = parseUuid(request.assignedUserId());
        return routineService.generateRun(request.templateId(), request.scheduledFor(), request.dueAt(), assigned, me);
    }

    @PostMapping("/runs/{id}/transition")
    public RoutineRun transition(
            @PathVariable Long id,
            @Valid @RequestBody TransitionRequest request,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        return routineService.transition(id, request.status(), request.comment(), me);
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    public record CreateTemplateRequest(
            Long companyId,
            Long branchId,
            @NotBlank @Size(max = 180) String title,
            String description,
            @NotBlank String recurrenceRule,
            Boolean requiresPhoto,
            Boolean requiresComment
    ) {
    }

    public record CreateTaskRequest(
            Long companyId,
            Long branchId,
            @NotBlank @Size(max = 180) String title,
            String description,
            @NotBlank String recurrence,
            @NotBlank String targetType,
            String targetRole,
            Long targetSectorId,
            String targetUserId,
            LocalTime startTime,
            LocalTime dueTime,
            Integer weekday,
            Integer dayOfMonth,
            List<Integer> customDays,
            Boolean businessDaysOnly,
            LocalDate startDate,
            Integer reminderBeforeMinutes,
            Boolean requiresPhoto,
            Boolean requiresComment
    ) {
    }

    public record GenerateRunRequest(
            Long templateId,
            Instant scheduledFor,
            Instant dueAt,
            String assignedUserId
    ) {
    }

    public record TransitionRequest(@NotNull RoutineStatus status, String comment) {
    }

    public record CommentRequest(@NotBlank String body) {
    }
}
