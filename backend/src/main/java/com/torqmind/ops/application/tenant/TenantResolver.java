package com.torqmind.ops.application.tenant;

import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import com.torqmind.ops.shared.api.ForbiddenException;
import org.springframework.stereotype.Component;

/**
 * Resolve empresa/filial a partir do usuário autenticado.
 * MASTER: pode operar sem empresa (admin global).
 * OWNER: empresa inteira (todas as filiais).
 * MANAGER/OPERATOR: empresa + filial obrigatória.
 */
@Component
public class TenantResolver {

    /** Empresa para listagens. MASTER pode informar companyId; demais usam a do JWT. */
    public Long resolveListCompanyId(AppUserPrincipal me, Long requestedCompanyId) {
        if (me == null) {
            throw new ForbiddenException("Não autenticado.");
        }
        if ("MASTER".equals(me.role())) {
            if (requestedCompanyId != null) {
                return requestedCompanyId;
            }
            if (me.companyId() != null) {
                return me.companyId();
            }
            return 1L;
        }
        return requireCompanyId(me);
    }

    public Long requireCompanyId(AppUserPrincipal me) {
        if (me == null) {
            throw new ForbiddenException("Não autenticado.");
        }
        if ("MASTER".equals(me.role())) {
            if (me.companyId() != null) {
                return me.companyId();
            }
            throw new ForbiddenException("Administrador sem empresa selecionada. Vincule uma empresa ao usuário ou informe companyId.");
        }
        if (me.companyId() == null) {
            throw new ForbiddenException("Usuário sem empresa vinculada. Peça ao administrador para ajustar o cadastro.");
        }
        return me.companyId();
    }

    /** companyId efetivo para listagens; MASTER sem empresa → null (sem filtro rígido no caller). */
    public Long companyIdOrNull(AppUserPrincipal me) {
        if (me == null) {
            return null;
        }
        if (me.companyId() != null) {
            return me.companyId();
        }
        return null;
    }

    /** Filtra por filial: só Gerente/Funcionário. Dono/Admin veem todas da empresa. */
    public Long branchFilterOrNull(AppUserPrincipal me) {
        if (me == null) {
            return null;
        }
        if ("MANAGER".equals(me.role()) || "OPERATOR".equals(me.role())) {
            return me.branchId();
        }
        return null;
    }

    public Long resolveBranchForCreate(AppUserPrincipal me, Long requestedBranchId) {
        if ("MANAGER".equals(me.role()) || "OPERATOR".equals(me.role())) {
            if (me.branchId() == null) {
                throw new ForbiddenException("Gerente/Funcionário precisa ter filial vinculada.");
            }
            return me.branchId();
        }
        return requestedBranchId;
    }

    public Long resolveCompanyForCreate(AppUserPrincipal me, Long requestedCompanyId) {
        if ("MASTER".equals(me.role())) {
            if (requestedCompanyId != null) {
                return requestedCompanyId;
            }
            if (me.companyId() != null) {
                return me.companyId();
            }
            throw new IllegalArgumentException("Informe a empresa.");
        }
        return requireCompanyId(me);
    }

    public boolean isMaster(AppUserPrincipal me) {
        return me != null && "MASTER".equals(me.role());
    }

    /** MASTER: qualquer uma; OWNER: toda a empresa; demais: só a que criou. Executor não exclui. */
    public static boolean canDeleteTemplate(String role, java.util.UUID actorId, Long actorCompanyId,
                                            Long templateCompanyId, java.util.UUID templateCreatedBy) {
        if (role == null) {
            return false;
        }
        if ("MASTER".equalsIgnoreCase(role)) {
            return true;
        }
        boolean sameCompany = templateCompanyId != null && templateCompanyId.equals(actorCompanyId);
        if (!sameCompany) {
            return false;
        }
        if ("OWNER".equalsIgnoreCase(role)) {
            return true;
        }
        return actorId != null && actorId.equals(templateCreatedBy);
    }

    public void assertCanAccess(AppUserPrincipal me, Long resourceCompanyId, Long resourceBranchId) {
        if (me == null) {
            throw new ForbiddenException("Não autenticado.");
        }
        assertCanAccess(me.role(), me.companyId(), me.branchId(), resourceCompanyId, resourceBranchId);
    }

    public void assertCanAccess(String role, Long userCompanyId, Long userBranchId, Long resourceCompanyId, Long resourceBranchId) {
        if (role == null) {
            throw new ForbiddenException("Não autenticado.");
        }
        if ("MASTER".equalsIgnoreCase(role)) {
            return;
        }
        if (userCompanyId == null || resourceCompanyId == null || !userCompanyId.equals(resourceCompanyId)) {
            throw new ForbiddenException("Recurso fora do escopo da sua empresa.");
        }
        if ("OWNER".equalsIgnoreCase(role)) {
            return;
        }
        if (userBranchId != null && resourceBranchId != null && !userBranchId.equals(resourceBranchId)) {
            throw new ForbiddenException("Recurso fora do escopo da sua filial.");
        }
    }
}
