package com.torqmind.ops.application.occurrence;

import com.torqmind.ops.application.notification.NotificationService;
import com.torqmind.ops.application.task.ActivityService;
import com.torqmind.ops.application.tenant.TenantAccessService;
import com.torqmind.ops.domain.occurrence.Occurrence;
import com.torqmind.ops.domain.ops.OccurrenceStatus;
import com.torqmind.ops.domain.ops.StatusRules;
import com.torqmind.ops.domain.task.TaskType;
import com.torqmind.ops.infrastructure.persistence.OccurrenceRepository;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class OccurrenceService {

    private static final Set<String> PRIORITIES = Set.of("BAIXA", "MEDIA", "ALTA", "CRITICA");

    private final OccurrenceRepository occurrenceRepository;
    private final NotificationService notificationService;
    private final ActivityService activityService;
    private final TenantAccessService tenantAccessService;

    public OccurrenceService(
            OccurrenceRepository occurrenceRepository,
            NotificationService notificationService,
            ActivityService activityService,
            TenantAccessService tenantAccessService
    ) {
        this.occurrenceRepository = occurrenceRepository;
        this.notificationService = notificationService;
        this.activityService = activityService;
        this.tenantAccessService = tenantAccessService;
    }

    public List<Occurrence> list(Long companyId, Long branchId, OccurrenceStatus status) {
        if (branchId != null) {
            if (status == null) {
                return occurrenceRepository.findByCompanyIdAndBranchIdOrderByCreatedAtDesc(companyId, branchId);
            }
            return occurrenceRepository.findByCompanyIdAndBranchIdAndStatusOrderByCreatedAtDesc(companyId, branchId, status);
        }
        if (status == null) {
            return occurrenceRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
        }
        return occurrenceRepository.findByCompanyIdAndStatusOrderByCreatedAtDesc(companyId, status);
    }

    public Occurrence get(Long id, AppUserPrincipal me) {
        return tenantAccessService.requireOccurrenceAccess(me, id);
    }

    @Transactional
    public Occurrence open(Occurrence occurrence, AppUserPrincipal me) {
        tenantAccessService.requireBranchInCompany(occurrence.getCompanyId(), occurrence.getBranchId());
        if (occurrence.getAssigneeUserId() != null) {
            tenantAccessService.requireTargetUser(
                    occurrence.getCompanyId(), occurrence.getBranchId(), occurrence.getAssigneeUserId());
        }
        String priority = occurrence.getPriority() == null
                ? "MEDIA"
                : occurrence.getPriority().trim().toUpperCase();
        if (!PRIORITIES.contains(priority)) {
            throw new IllegalArgumentException("Prioridade inválida.");
        }
        occurrence.setPriority(priority);
        UUID actor = me.userId();
        Instant now = Instant.now();
        occurrence.setStatus(OccurrenceStatus.ABERTA);
        occurrence.setOpenedBy(actor);
        occurrence.setCreatedAt(now);
        occurrence.setUpdatedAt(now);
        Occurrence saved = occurrenceRepository.save(occurrence);

        activityService.record(TaskType.OCCURRENCE, saved.getId(), actor, "CREATED", null,
                OccurrenceStatus.ABERTA.name(), "Ocorrência aberta.");

        if (saved.getAssigneeUserId() != null) {
            notificationService.notifyCounterpart(actor, saved.getAssigneeUserId(), "OCCURRENCE", saved.getId(),
                    "Nova ocorrência", saved.getTitle());
        }
        return saved;
    }

    @Transactional
    public Occurrence transition(Long id, OccurrenceStatus next, String reason, AppUserPrincipal me) {
        Occurrence occurrence = tenantAccessService.requireOccurrenceAccess(me, id);
        UUID actor = me.userId();

        if (!StatusRules.canTransitionOccurrence(occurrence.getStatus(), next)) {
            throw new IllegalArgumentException("Transição de status de ocorrência inválida.");
        }

        Instant now = Instant.now();
        OccurrenceStatus previous = occurrence.getStatus();
        occurrence.setStatus(next);
        occurrence.setUpdatedAt(now);
        Occurrence saved = occurrenceRepository.save(occurrence);

        activityService.record(TaskType.OCCURRENCE, saved.getId(), actor, "STATUS_CHANGED",
                previous.name(), next.name(), reason);

        UUID opener = saved.getOpenedBy();
        UUID assignee = saved.getAssigneeUserId();
        UUID recipient = actor.equals(opener) ? assignee : opener;
        if (recipient != null) {
            String body = reason != null && !reason.isBlank() ? reason : "Status: " + next.name();
            notificationService.notifyCounterpart(actor, recipient, "OCCURRENCE", saved.getId(),
                    "Ocorrência atualizada", body);
        }
        return saved;
    }
}
