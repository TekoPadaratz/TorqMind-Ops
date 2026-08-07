package com.torqmind.ops.application.task;

import com.torqmind.ops.application.notification.NotificationService;
import com.torqmind.ops.application.storage.DriveFolderService;
import com.torqmind.ops.domain.company.Branch;
import com.torqmind.ops.domain.company.Company;
import com.torqmind.ops.domain.occurrence.Occurrence;
import com.torqmind.ops.domain.routine.RoutineRun;
import com.torqmind.ops.domain.routine.RoutineTemplate;
import com.torqmind.ops.domain.task.TaskAttachment;
import com.torqmind.ops.domain.task.TaskComment;
import com.torqmind.ops.domain.task.TaskType;
import com.torqmind.ops.domain.user.User;
import com.torqmind.ops.infrastructure.persistence.BranchRepository;
import com.torqmind.ops.infrastructure.persistence.CompanyRepository;
import com.torqmind.ops.infrastructure.persistence.OccurrenceRepository;
import com.torqmind.ops.infrastructure.persistence.RoutineRunRepository;
import com.torqmind.ops.infrastructure.persistence.RoutineTemplateRepository;
import com.torqmind.ops.infrastructure.persistence.TaskActivityRepository;
import com.torqmind.ops.infrastructure.persistence.TaskAttachmentRepository;
import com.torqmind.ops.infrastructure.persistence.TaskCommentRepository;
import com.torqmind.ops.infrastructure.persistence.UserRepository;
import com.torqmind.ops.infrastructure.storage.StoragePaths;
import com.torqmind.ops.infrastructure.storage.StorageProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TaskDetailService {

    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "application/pdf");
    private static final long MAX_BYTES = 15L * 1024 * 1024;

    private final RoutineRunRepository runRepository;
    private final RoutineTemplateRepository templateRepository;
    private final OccurrenceRepository occurrenceRepository;
    private final TaskCommentRepository commentRepository;
    private final TaskAttachmentRepository attachmentRepository;
    private final TaskActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final BranchRepository branchRepository;
    private final StorageProvider storageProvider;
    private final DriveFolderService driveFolderService;
    private final NotificationService notificationService;
    private final ActivityService activityService;

    public TaskDetailService(
            RoutineRunRepository runRepository,
            RoutineTemplateRepository templateRepository,
            OccurrenceRepository occurrenceRepository,
            TaskCommentRepository commentRepository,
            TaskAttachmentRepository attachmentRepository,
            TaskActivityRepository activityRepository,
            UserRepository userRepository,
            CompanyRepository companyRepository,
            BranchRepository branchRepository,
            StorageProvider storageProvider,
            DriveFolderService driveFolderService,
            NotificationService notificationService,
            ActivityService activityService
    ) {
        this.runRepository = runRepository;
        this.templateRepository = templateRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.commentRepository = commentRepository;
        this.attachmentRepository = attachmentRepository;
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.branchRepository = branchRepository;
        this.storageProvider = storageProvider;
        this.driveFolderService = driveFolderService;
        this.notificationService = notificationService;
        this.activityService = activityService;
    }

    // ---------------- Comentários ----------------
    @Transactional
    public CommentView addComment(TaskType type, Long taskId, UUID actor, String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Comentário não pode ser vazio.");
        }
        ensureTaskExists(type, taskId);

        TaskComment comment = new TaskComment();
        comment.setTaskType(type.name());
        comment.setTaskId(taskId);
        comment.setAuthorUserId(actor);
        comment.setBody(body.trim());
        comment.setCreatedAt(Instant.now());
        TaskComment saved = commentRepository.save(comment);

        activityService.record(type, taskId, actor, "COMMENT", null, null, null);
        notifyParticipants(type, taskId, actor, "Novo comentário", body.trim());

        return toCommentView(saved, nameMap(Set.of(actor)));
    }

    // ---------------- Anexos ----------------
    @Transactional
    public AttachmentView addAttachment(TaskType type, Long taskId, UUID actor, String fileName, String mimeType, byte[] content) {
        ensureTaskExists(type, taskId);
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Arquivo vazio.");
        }
        if (content.length > MAX_BYTES) {
            throw new IllegalArgumentException("Arquivo acima do limite de 15 MB.");
        }
        String mime = mimeType == null ? "" : mimeType.toLowerCase();
        if (!ALLOWED_MIME.contains(mime)) {
            throw new IllegalArgumentException("Tipo de arquivo não suportado. Envie imagem ou PDF.");
        }

        String checksum = sha256(content);
        String ext = extensionFor(mime);
        String displayName = sanitizeName(fileName, ext);
        String folder = resolveStorageFolder(type, taskId);
        String storedName = StoragePaths.taskFileName(taskId, displayName, ext);
        String path = storageProvider.saveBytes(folder, storedName, content);

        TaskAttachment attachment = new TaskAttachment();
        attachment.setTaskType(type.name());
        attachment.setTaskId(taskId);
        attachment.setUploadedBy(actor);
        attachment.setStorageProvider(storageProvider.providerName());
        attachment.setStoragePath(path);
        attachment.setFileName(displayName);
        attachment.setMimeType(mime);
        attachment.setSizeBytes(content.length);
        attachment.setChecksumSha256(checksum);
        attachment.setCreatedAt(Instant.now());
        TaskAttachment saved = attachmentRepository.save(attachment);

        activityService.record(type, taskId, actor, "ATTACHMENT", null, null, saved.getFileName());
        notifyParticipants(type, taskId, actor, "Novo anexo", saved.getFileName());

        return toAttachmentView(saved, nameMap(Set.of(actor)));
    }

    public AttachmentContent readAttachment(Long attachmentId) {
        TaskAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Anexo não encontrado."));
        byte[] bytes = storageProvider.read(attachment.getStoragePath());
        return new AttachmentContent(bytes, attachment.getMimeType(), attachment.getFileName());
    }

    // ---------------- Detalhe ----------------
    public TaskDetail getRoutineRunDetail(Long runId) {
        RoutineRun run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Execução não encontrada."));
        RoutineTemplate template = templateRepository.findById(run.getTemplateId()).orElse(null);

        Set<UUID> userIds = new HashSet<>();
        if (run.getAssignedUserId() != null) userIds.add(run.getAssignedUserId());
        if (template != null && template.getCreatedBy() != null) userIds.add(template.getCreatedBy());

        List<TaskComment> comments = commentRepository.findByTaskTypeAndTaskIdOrderByCreatedAt(TaskType.ROUTINE_RUN.name(), runId);
        List<TaskAttachment> attachments = attachmentRepository.findByTaskTypeAndTaskIdOrderByCreatedAt(TaskType.ROUTINE_RUN.name(), runId);
        List<com.torqmind.ops.domain.task.TaskActivity> activities = activityRepository.findByTaskTypeAndTaskIdOrderByCreatedAt(TaskType.ROUTINE_RUN.name(), runId);
        collectUserIds(userIds, comments, attachments, activities);
        Map<UUID, String> names = nameMap(userIds);

        RoutineSummary summary = new RoutineSummary(
                run.getId(),
                template != null ? template.getTitle() : "Rotina",
                template != null ? template.getDescription() : null,
                run.getStatus().name(),
                run.getScheduledFor(),
                run.getDueAt(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getBranchId(),
                ref(run.getAssignedUserId(), names),
                template != null && template.isRequiresPhoto(),
                template != null && template.isRequiresComment(),
                run.getExecutionComment()
        );

        return new TaskDetail(
                "ROUTINE_RUN",
                summary,
                comments.stream().map(c -> toCommentView(c, names)).toList(),
                attachments.stream().map(a -> toAttachmentView(a, names)).toList(),
                activities.stream().map(a -> toActivityView(a, names)).toList()
        );
    }

    public TaskDetail getOccurrenceDetail(Long occId) {
        Occurrence occ = occurrenceRepository.findById(occId)
                .orElseThrow(() -> new IllegalArgumentException("Ocorrência não encontrada."));

        Set<UUID> userIds = new HashSet<>();
        if (occ.getOpenedBy() != null) userIds.add(occ.getOpenedBy());
        if (occ.getAssigneeUserId() != null) userIds.add(occ.getAssigneeUserId());

        List<TaskComment> comments = commentRepository.findByTaskTypeAndTaskIdOrderByCreatedAt(TaskType.OCCURRENCE.name(), occId);
        List<TaskAttachment> attachments = attachmentRepository.findByTaskTypeAndTaskIdOrderByCreatedAt(TaskType.OCCURRENCE.name(), occId);
        List<com.torqmind.ops.domain.task.TaskActivity> activities = activityRepository.findByTaskTypeAndTaskIdOrderByCreatedAt(TaskType.OCCURRENCE.name(), occId);
        collectUserIds(userIds, comments, attachments, activities);
        Map<UUID, String> names = nameMap(userIds);

        OccurrenceSummary summary = new OccurrenceSummary(
                occ.getId(),
                occ.getTitle(),
                occ.getDescription(),
                occ.getStatus().name(),
                occ.getPriority(),
                occ.getBranchId(),
                ref(occ.getOpenedBy(), names),
                ref(occ.getAssigneeUserId(), names),
                occ.getCreatedAt()
        );

        return new TaskDetail(
                "OCCURRENCE",
                summary,
                comments.stream().map(c -> toCommentView(c, names)).toList(),
                attachments.stream().map(a -> toAttachmentView(a, names)).toList(),
                activities.stream().map(a -> toActivityView(a, names)).toList()
        );
    }

    // ---------------- Helpers ----------------
    private String resolveStorageFolder(TaskType type, Long taskId) {
        Long companyId = null;
        Long branchId = null;
        if (type == TaskType.ROUTINE_RUN) {
            RoutineRun run = runRepository.findById(taskId).orElse(null);
            if (run != null) {
                companyId = run.getCompanyId();
                branchId = run.getBranchId();
            }
        } else {
            Occurrence occ = occurrenceRepository.findById(taskId).orElse(null);
            if (occ != null) {
                companyId = occ.getCompanyId();
                branchId = occ.getBranchId();
            }
        }
        Company company = companyId == null ? null : companyRepository.findById(companyId).orElse(null);
        Branch branch = branchId == null ? null : branchRepository.findById(branchId).orElse(null);
        if (company != null) {
            driveFolderService.ensureCompanyFolder(company);
            if (branch != null) {
                driveFolderService.ensureBranchFolder(company, branch);
            }
            return driveFolderService.logicalFolder(company, branch, type.name());
        }
        return StoragePaths.taskKindFolder(type.name()) + "/" + taskId;
    }

    private void ensureTaskExists(TaskType type, Long taskId) {
        boolean exists = type == TaskType.ROUTINE_RUN
                ? runRepository.existsById(taskId)
                : occurrenceRepository.existsById(taskId);
        if (!exists) {
            throw new IllegalArgumentException("Tarefa não encontrada.");
        }
    }

    private void notifyParticipants(TaskType type, Long taskId, UUID actor, String title, String body) {
        Set<UUID> recipients = new LinkedHashSet<>();
        if (type == TaskType.ROUTINE_RUN) {
            runRepository.findById(taskId).ifPresent(run -> {
                if (run.getAssignedUserId() != null) recipients.add(run.getAssignedUserId());
                templateRepository.findById(run.getTemplateId())
                        .ifPresent(t -> { if (t.getCreatedBy() != null) recipients.add(t.getCreatedBy()); });
            });
        } else {
            occurrenceRepository.findById(taskId).ifPresent(occ -> {
                if (occ.getOpenedBy() != null) recipients.add(occ.getOpenedBy());
                if (occ.getAssigneeUserId() != null) recipients.add(occ.getAssigneeUserId());
            });
        }
        for (UUID recipient : recipients) {
            notificationService.notifyCounterpart(actor, recipient, type.name(), taskId, title, body);
        }
    }

    private void collectUserIds(Set<UUID> target, List<TaskComment> comments, List<TaskAttachment> attachments, List<com.torqmind.ops.domain.task.TaskActivity> activities) {
        comments.forEach(c -> target.add(c.getAuthorUserId()));
        attachments.forEach(a -> target.add(a.getUploadedBy()));
        activities.forEach(a -> { if (a.getActorUserId() != null) target.add(a.getActorUserId()); });
    }

    private Map<UUID, String> nameMap(Set<UUID> ids) {
        List<UUID> clean = ids.stream().filter(java.util.Objects::nonNull).collect(Collectors.toList());
        if (clean.isEmpty()) return Map.of();
        return userRepository.findAllById(clean).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
    }

    private UserRef ref(UUID id, Map<UUID, String> names) {
        if (id == null) return null;
        return new UserRef(id.toString(), names.getOrDefault(id, "Usuário"));
    }

    private CommentView toCommentView(TaskComment c, Map<UUID, String> names) {
        return new CommentView(c.getId(), ref(c.getAuthorUserId(), names), c.getBody(), c.getCreatedAt());
    }

    private AttachmentView toAttachmentView(TaskAttachment a, Map<UUID, String> names) {
        return new AttachmentView(
                a.getId(),
                a.getFileName(),
                a.getMimeType(),
                a.getSizeBytes(),
                "/api/attachments/" + a.getId(),
                ref(a.getUploadedBy(), names),
                a.getCreatedAt());
    }

    private ActivityView toActivityView(com.torqmind.ops.domain.task.TaskActivity a, Map<UUID, String> names) {
        return new ActivityView(
                a.getId(),
                ref(a.getActorUserId(), names),
                a.getActivityType(),
                a.getFromStatus(),
                a.getToStatus(),
                a.getMessage(),
                a.getCreatedAt());
    }

    private static String sanitizeName(String original, String ext) {
        if (original == null || original.isBlank()) {
            return "arquivo" + ext;
        }
        String base = original.replaceAll("[\\\\/\\r\\n]", "_").trim();
        return base.length() > 200 ? base.substring(0, 200) : base;
    }

    private static String extensionFor(String mime) {
        return switch (mime) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "application/pdf" -> ".pdf";
            default -> "";
        };
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao calcular checksum.", ex);
        }
    }

    // ---------------- DTOs ----------------
    public record UserRef(String id, String name) {}

    public record CommentView(Long id, UserRef author, String body, Instant createdAt) {}

    public record AttachmentView(Long id, String fileName, String mimeType, long sizeBytes, String url, UserRef uploadedBy, Instant createdAt) {}

    public record ActivityView(Long id, UserRef actor, String type, String fromStatus, String toStatus, String message, Instant createdAt) {}

    public record RoutineSummary(
            Long id, String title, String description, String status,
            Instant scheduledFor, Instant dueAt, Instant startedAt, Instant completedAt,
            Long branchId, UserRef assignee, boolean requiresPhoto, boolean requiresComment, String executionComment) {}

    public record OccurrenceSummary(
            Long id, String title, String description, String status, String priority,
            Long branchId, UserRef openedBy, UserRef assignee, Instant createdAt) {}

    public record TaskDetail(String taskType, Object summary, List<CommentView> comments, List<AttachmentView> attachments, List<ActivityView> activities) {}

    public record AttachmentContent(byte[] bytes, String mimeType, String fileName) {}

    // Reservado para chamadas futuras que precisem de lista mutável de participantes.
    @SuppressWarnings("unused")
    private List<UUID> participantList(Set<UUID> ids) {
        return new ArrayList<>(ids);
    }
}
