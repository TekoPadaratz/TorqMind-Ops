package com.torqmind.ops.application.occurrence;

import com.torqmind.ops.application.notification.NotificationService;
import com.torqmind.ops.application.task.ActivityService;
import com.torqmind.ops.application.tenant.TenantResolver;
import com.torqmind.ops.domain.occurrence.Occurrence;
import com.torqmind.ops.domain.ops.OccurrenceStatus;
import com.torqmind.ops.domain.ops.StatusRules;
import com.torqmind.ops.domain.task.TaskType;
import com.torqmind.ops.domain.user.User;
import com.torqmind.ops.infrastructure.persistence.OccurrenceRepository;
import com.torqmind.ops.infrastructure.persistence.UserRepository;
import com.torqmind.ops.shared.api.ForbiddenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OccurrenceService {

    private final OccurrenceRepository occurrenceRepository;
    private final NotificationService notificationService;
    private final ActivityService activityService;
    private final UserRepository userRepository;
    private final TenantResolver tenantResolver;

    public OccurrenceService(
            OccurrenceRepository occurrenceRepository,
            NotificationService notificationService,
            ActivityService activityService,
            UserRepository userRepository,
            TenantResolver tenantResolver
    ) {
        this.occurrenceRepository = occurrenceRepository;
        this.notificationService = notificationService;
        this.activityService = activityService;
        this.userRepository = userRepository;
        this.tenantResolver = tenantResolver;
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

    @Transactional
    public Occurrence open(Occurrence occurrence, UUID actor) {
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
    public Occurrence transition(Long id, OccurrenceStatus next, String reason, UUID actor) {
        Occurrence occurrence = occurrenceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ocorrência não encontrada."));

        User actorUser = userRepository.findById(actor)
                .orElseThrow(() -> new ForbiddenException("Usuário inválido."));
        tenantResolver.assertCanAccess(
                actorUser.getRole(), actorUser.getCompanyId(), actorUser.getBranchId(),
                occurrence.getCompanyId(), occurrence.getBranchId());

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
