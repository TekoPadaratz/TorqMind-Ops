package com.torqmind.ops.application.tenant;

import com.torqmind.ops.domain.occurrence.Occurrence;
import com.torqmind.ops.domain.routine.RoutineRun;
import com.torqmind.ops.domain.routine.RoutineTemplate;
import com.torqmind.ops.domain.sector.Sector;
import com.torqmind.ops.domain.task.TaskAttachment;
import com.torqmind.ops.domain.task.TaskType;
import com.torqmind.ops.domain.user.User;
import com.torqmind.ops.infrastructure.persistence.BranchRepository;
import com.torqmind.ops.infrastructure.persistence.OccurrenceRepository;
import com.torqmind.ops.infrastructure.persistence.RoutineRunRepository;
import com.torqmind.ops.infrastructure.persistence.RoutineTemplateRepository;
import com.torqmind.ops.infrastructure.persistence.SectorRepository;
import com.torqmind.ops.infrastructure.persistence.TaskAttachmentRepository;
import com.torqmind.ops.infrastructure.persistence.UserRepository;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import com.torqmind.ops.shared.api.ForbiddenException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/** Centraliza a autorização multi-tenant para recursos acessados por ID. */
@Service
public class TenantAccessService {

    private final RoutineTemplateRepository templateRepository;
    private final RoutineRunRepository runRepository;
    private final OccurrenceRepository occurrenceRepository;
    private final TaskAttachmentRepository attachmentRepository;
    private final BranchRepository branchRepository;
    private final SectorRepository sectorRepository;
    private final UserRepository userRepository;

    public TenantAccessService(
            RoutineTemplateRepository templateRepository,
            RoutineRunRepository runRepository,
            OccurrenceRepository occurrenceRepository,
            TaskAttachmentRepository attachmentRepository,
            BranchRepository branchRepository,
            SectorRepository sectorRepository,
            UserRepository userRepository
    ) {
        this.templateRepository = templateRepository;
        this.runRepository = runRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.attachmentRepository = attachmentRepository;
        this.branchRepository = branchRepository;
        this.sectorRepository = sectorRepository;
        this.userRepository = userRepository;
    }

    public RoutineTemplate requireTemplateAccess(AppUserPrincipal me, Long templateId) {
        RoutineTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Rotina não encontrada."));
        requireScope(me, template.getCompanyId(), template.getBranchId());
        return template;
    }

    public RoutineRun requireRoutineRunAccess(AppUserPrincipal me, Long runId) {
        RoutineRun run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Execução não encontrada."));
        requireScope(me, run.getCompanyId(), run.getBranchId());
        return run;
    }

    /**
     * Quando a tarefa tem responsável nominal, somente ele pode assumir ou
     * concluir a execução. O escopo da empresa/filial continua sendo validado
     * separadamente por {@link #requireRoutineRunAccess(AppUserPrincipal, Long)}.
     */
    public void requireRoutineExecutor(AppUserPrincipal me, RoutineRun run) {
        if (run.getAssignedUserId() != null
                && (me == null || !run.getAssignedUserId().equals(me.userId()))) {
            throw new ForbiddenException("Somente o responsável pode iniciar ou concluir esta tarefa.");
        }
    }

    public Occurrence requireOccurrenceAccess(AppUserPrincipal me, Long occurrenceId) {
        Occurrence occurrence = occurrenceRepository.findById(occurrenceId)
                .orElseThrow(() -> new IllegalArgumentException("Ocorrência não encontrada."));
        requireScope(me, occurrence.getCompanyId(), occurrence.getBranchId());
        return occurrence;
    }

    public TaskAttachment requireAttachmentAccess(AppUserPrincipal me, Long attachmentId) {
        TaskAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Anexo não encontrado."));
        TaskType type;
        try {
            type = TaskType.valueOf(attachment.getTaskType());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Anexo inválido.");
        }
        requireTaskAccess(me, type, attachment.getTaskId());
        return attachment;
    }

    public void requireTaskAccess(AppUserPrincipal me, TaskType type, Long taskId) {
        if (type == TaskType.ROUTINE_RUN) {
            requireRoutineRunAccess(me, taskId);
        } else {
            requireOccurrenceAccess(me, taskId);
        }
    }

    public void requireBranchInCompany(Long companyId, Long branchId) {
        if (branchId == null) {
            return;
        }
        boolean valid = branchRepository.findById(branchId)
                .map(branch -> Objects.equals(companyId, branch.getCompanyId()))
                .orElse(false);
        if (!valid) {
            throw new IllegalArgumentException("Filial inválida para a empresa selecionada.");
        }
    }

    public User requireTargetUser(Long companyId, Long branchId, UUID userId) {
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Usuário responsável inválido."));
        if (!Objects.equals(companyId, user.getCompanyId())
                || (branchId != null && !Objects.equals(branchId, user.getBranchId()))) {
            throw new IllegalArgumentException("O usuário responsável não pertence à empresa/filial da tarefa.");
        }
        return user;
    }

    public Sector requireTargetSector(Long companyId, Long branchId, Long sectorId) {
        Sector sector = sectorRepository.findById(sectorId)
                .orElseThrow(() -> new IllegalArgumentException("Setor inválido."));
        boolean branchMatches = branchId == null
                || sector.getBranchId() == null
                || Objects.equals(branchId, sector.getBranchId());
        if (!Objects.equals(companyId, sector.getCompanyId()) || !branchMatches) {
            throw new IllegalArgumentException("O setor não pertence à empresa/filial da tarefa.");
        }
        return sector;
    }

    void requireScope(AppUserPrincipal me, Long resourceCompanyId, Long resourceBranchId) {
        if (me == null) {
            throw new ForbiddenException("Não autenticado.");
        }
        if ("MASTER".equals(me.role())) {
            return;
        }
        if (me.companyId() == null || !Objects.equals(me.companyId(), resourceCompanyId)) {
            throw new ForbiddenException("Este recurso pertence a outra empresa.");
        }
        if (("MANAGER".equals(me.role()) || "OPERATOR".equals(me.role()))
                && (me.branchId() == null || !Objects.equals(me.branchId(), resourceBranchId))) {
            throw new ForbiddenException("Este recurso pertence a outra filial.");
        }
    }
}
