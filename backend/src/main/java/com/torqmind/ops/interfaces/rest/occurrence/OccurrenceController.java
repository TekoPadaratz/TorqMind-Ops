package com.torqmind.ops.interfaces.rest.occurrence;

import com.torqmind.ops.application.occurrence.OccurrenceService;
import com.torqmind.ops.application.occurrence.FuelQualityAnalysisService;
import com.torqmind.ops.application.task.TaskDetailService;
import com.torqmind.ops.application.tenant.TenantResolver;
import com.torqmind.ops.domain.occurrence.Occurrence;
import com.torqmind.ops.domain.ops.OccurrenceStatus;
import com.torqmind.ops.domain.task.TaskType;
import com.torqmind.ops.application.routine.RoutineService;
import com.torqmind.ops.domain.routine.RoutineTemplate;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/occurrences")
public class OccurrenceController {

    private final OccurrenceService occurrenceService;
    private final FuelQualityAnalysisService fuelQualityAnalysisService;
    private final TaskDetailService taskDetailService;
    private final TenantResolver tenantResolver;
    private final RoutineService routineService;

    public OccurrenceController(
            OccurrenceService occurrenceService,
            FuelQualityAnalysisService fuelQualityAnalysisService,
            TaskDetailService taskDetailService,
            TenantResolver tenantResolver,
            RoutineService routineService
    ) {
        this.occurrenceService = occurrenceService;
        this.fuelQualityAnalysisService = fuelQualityAnalysisService;
        this.taskDetailService = taskDetailService;
        this.tenantResolver = tenantResolver;
        this.routineService = routineService;
    }

    @GetMapping
    public List<FuelQualityAnalysisService.OccurrenceListItem> list(
            @AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) OccurrenceStatus status
    ) {
        Long cid = tenantResolver.resolveListCompanyId(me, companyId);
        Long branchId = tenantResolver.branchFilterOrNull(me);
        return fuelQualityAnalysisService.listItems(occurrenceService.list(cid, branchId, status));
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv(
            @AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) OccurrenceStatus status
    ) {
        Long cid = tenantResolver.resolveListCompanyId(me, companyId);
        Long branchId = tenantResolver.branchFilterOrNull(me);
        byte[] csv = occurrenceService.exportCsv(cid, branchId, status);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ocorrencias.csv")
                .body(csv);
    }

    @GetMapping("/quality-receipts/defaults")
    public FuelQualityAnalysisService.QualityReceiptView qualityDefaults(
            @AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long branchId
    ) {
        return fuelQualityAnalysisService.defaults(me, companyId, branchId);
    }

    @PostMapping("/quality-receipts")
    public FuelQualityAnalysisService.QualityReceiptView createQualityReceipt(
            @RequestBody FuelQualityAnalysisService.QualityReceiptRequest request,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        return fuelQualityAnalysisService.save(me, null, request);
    }

    @GetMapping("/{id}/quality-receipt")
    public FuelQualityAnalysisService.QualityReceiptView qualityReceipt(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        return fuelQualityAnalysisService.get(me, id);
    }

    @PutMapping("/{id}/quality-receipt")
    public FuelQualityAnalysisService.QualityReceiptView updateQualityReceipt(
            @PathVariable Long id,
            @RequestBody FuelQualityAnalysisService.QualityReceiptRequest request,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        return fuelQualityAnalysisService.save(me, id, request);
    }

    @PostMapping
    public Occurrence open(
            @Valid @RequestBody CreateOccurrenceRequest request,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        Occurrence occurrence = new Occurrence();
        occurrence.setCompanyId(tenantResolver.resolveCompanyForCreate(me, request.companyId()));
        occurrence.setBranchId(tenantResolver.resolveBranchForCreate(me, request.branchId()));
        occurrence.setTitle(request.title());
        occurrence.setDescription(request.description());
        occurrence.setPriority(request.priority() == null ? "MEDIA" : request.priority());
        occurrence.setAssigneeUserId(parseUuid(request.assigneeUserId()));
        return occurrenceService.open(occurrence, me);
    }

    @PostMapping("/{id}/transition")
    public Occurrence transition(
            @PathVariable Long id,
            @Valid @RequestBody TransitionRequest request,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        return occurrenceService.transition(id, request.status(), request.reason(), me);
    }

    @PostMapping("/{id}/to-routine")
    public RoutineTemplate toRoutine(
            @PathVariable Long id,
            @Valid @RequestBody ToRoutineRequest request,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        Occurrence occ = occurrenceService.get(id, me);
        return routineService.createRecurringTask(
                occ.getCompanyId(), occ.getBranchId(), occ.getTitle(), occ.getDescription(),
                request.recurrence(), request.targetType() == null ? "MANAGERS" : request.targetType(),
                null, request.targetSectorId(), parseUuid(request.targetUserId()),
                request.startTime(), request.dueTime(), request.weekday(), request.dayOfMonth(),
                request.customDays(), Boolean.TRUE.equals(request.businessDaysOnly()), request.startDate(),
                request.reminderBeforeMinutes(), Boolean.TRUE.equals(request.requiresPhoto()),
                Boolean.TRUE.equals(request.requiresComment()), java.util.List.of(), me.userId());
    }

    public record ToRoutineRequest(
            @NotBlank String recurrence,
            String targetType,
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

    @GetMapping("/{id}")
    public TaskDetailService.TaskDetail detail(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        return taskDetailService.getOccurrenceDetail(id, me);
    }

    @PostMapping("/{id}/comments")
    public TaskDetailService.CommentView addComment(
            @PathVariable Long id,
            @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        return taskDetailService.addComment(TaskType.OCCURRENCE, id, me, request.body());
    }

    @PostMapping("/{id}/attachments")
    public TaskDetailService.AttachmentView addAttachment(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        try {
            return taskDetailService.addAttachment(TaskType.OCCURRENCE, id, me,
                    file.getOriginalFilename(), file.getBytes(), lat, lng);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Falha ao ler o arquivo enviado.");
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    public record CreateOccurrenceRequest(
            Long companyId,
            Long branchId,
            @NotBlank @Size(max = 180) String title,
            @NotBlank String description,
            String priority,
            String assigneeUserId
    ) {
    }

    public record TransitionRequest(@NotNull OccurrenceStatus status, String reason) {
    }

    public record CommentRequest(@NotBlank String body) {
    }
}
