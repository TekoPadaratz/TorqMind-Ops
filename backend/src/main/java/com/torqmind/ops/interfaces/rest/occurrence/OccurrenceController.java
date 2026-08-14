package com.torqmind.ops.interfaces.rest.occurrence;

import com.torqmind.ops.application.occurrence.OccurrenceService;
import com.torqmind.ops.application.task.TaskDetailService;
import com.torqmind.ops.application.tenant.TenantResolver;
import com.torqmind.ops.domain.occurrence.Occurrence;
import com.torqmind.ops.domain.ops.OccurrenceStatus;
import com.torqmind.ops.domain.task.TaskType;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/occurrences")
public class OccurrenceController {

    private final OccurrenceService occurrenceService;
    private final TaskDetailService taskDetailService;
    private final TenantResolver tenantResolver;

    public OccurrenceController(
            OccurrenceService occurrenceService,
            TaskDetailService taskDetailService,
            TenantResolver tenantResolver
    ) {
        this.occurrenceService = occurrenceService;
        this.taskDetailService = taskDetailService;
        this.tenantResolver = tenantResolver;
    }

    @GetMapping
    public List<Occurrence> list(
            @AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) OccurrenceStatus status
    ) {
        Long cid = tenantResolver.resolveListCompanyId(me, companyId);
        Long branchId = tenantResolver.branchFilterOrNull(me);
        return occurrenceService.list(cid, branchId, status);
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
        return occurrenceService.open(occurrence, me.userId());
    }

    @PostMapping("/{id}/transition")
    public Occurrence transition(
            @PathVariable Long id,
            @Valid @RequestBody TransitionRequest request,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        return occurrenceService.transition(id, request.status(), request.reason(), me.userId());
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
        return taskDetailService.addComment(TaskType.OCCURRENCE, id, me.userId(), request.body());
    }

    @PostMapping("/{id}/attachments")
    public TaskDetailService.AttachmentView addAttachment(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        try {
            return taskDetailService.addAttachment(TaskType.OCCURRENCE, id, me.userId(),
                    file.getOriginalFilename(), file.getContentType(), file.getBytes());
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
            @NotBlank String title,
            @NotBlank String description,
            String priority,
            String assigneeUserId
    ) {
    }

    public record TransitionRequest(OccurrenceStatus status, String reason) {
    }

    public record CommentRequest(@NotBlank String body) {
    }
}
